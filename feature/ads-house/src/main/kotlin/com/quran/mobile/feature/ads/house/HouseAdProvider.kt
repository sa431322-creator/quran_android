package com.quran.mobile.feature.ads.house

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.ConnectivityManager
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.quran.data.di.AppScope
import com.quran.mobile.di.qualifier.ApplicationContext
import com.quran.mobile.feature.ads.AdContent
import com.quran.mobile.feature.ads.AdProvider
import com.quran.mobile.feature.ads.AdSlot
import com.quran.mobile.feature.ads.AdState
import com.quran.mobile.feature.ads.AdsServer
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.io.File
import kotlin.random.Random

/**
 * House ads from the Tasnim server. Nothing runs at app start: the first screen with a slot
 * starts it. With ads off on the server (or all slots off, or a blocked version, or no server)
 * it makes no `/ads` call, downloads nothing and schedules no work.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class HouseAdProvider internal constructor(
  private val context: Context,
  internal val server: AdsServer,
  private val repo: AdsRepository?,
  private val counts: AdCountStore,
  private val prefs: AdsPrefs,
  private val versionCode: Int,
  private val debuggable: Boolean,
  private val random: Random = Random.Default,
  private val now: () -> Long = System::currentTimeMillis,
  private val dataSaver: () -> Boolean = { false },
  private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) : AdProvider {

  @Inject
  constructor(
    @ApplicationContext context: Context,
    server: AdsServer,
    okHttpClient: OkHttpClient,
  ) : this(
    context = context,
    server = server,
    repo = server.baseUrl.takeIf { it.isNotBlank() }?.let {
      AdsRepository(
        okHttpClient,
        it,
        File(context.filesDir, "ads/answer"),
        File(context.cacheDir, "ads"),
      )
    },
    counts = AdCountStore(AdCountsWorker.countsDir(context)),
    prefs = AdsPrefs(context),
    versionCode = versionCodeOf(context),
    debuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0,
    dataSaver = { backgroundDataRestricted(context) },
  )

  private val states = AdSlot.entries.associateWith { MutableStateFlow<AdState>(AdState.Hidden) }
  private var job: Job? = null
  private var lastRefreshMs = Long.MIN_VALUE / 2
  private var refreshEveryMs = MIN_REFRESH_MS
  /** Ads shown from a preview token: never counted. */
  private var previewIds = emptySet<Int>()

  override fun state(slot: AdSlot): StateFlow<AdState> {
    maybeRefresh()
    return states.getValue(slot)
  }

  @Composable
  override fun Render(content: AdContent, modifier: Modifier) {
    HouseAdView(content, this, modifier)
  }

  override fun libraryListEvery(): Int = server.cachedConfig().libraryListEvery

  /** After a new preview token: ask again on the next screen visit. */
  internal fun forceRefresh() {
    lastRefreshMs = Long.MIN_VALUE / 2
  }

  /** Every screen visit asks for its slot; a new pick happens at most every refresh period. */
  private fun maybeRefresh() {
    if (job?.isActive == true || now() - lastRefreshMs < refreshEveryMs) return
    lastRefreshMs = now()
    job = scope.launch { refresh() }
  }

  internal suspend fun refresh() {
    val repo = repo
    val config = server.refreshConfig()
    val preview = prefs.previewToken?.takeIf { debuggable && it.isNotBlank() }
    val houseSlots = AdSlot.entries.filter { config.showsHouseAds(it, versionCode) }.toSet()
    if (repo == null || (houseSlots.isEmpty() && preview == null)) {
      states.values.forEach { it.value = AdState.Hidden }
      if (!counts.hasData()) AdCountsWorker.cancel(context)
      return
    }
    prefs.baseUrl = server.baseUrl
    val saver = dataSaver()
    val served = when {
      preview != null -> repo.preview(preview)
      saver -> repo.cached()
      else -> repo.fetch(versionCode)
    }
    refreshEveryMs = (served.refreshAfterSeconds * 1000L).coerceAtLeast(MIN_REFRESH_MS)
    previewIds = if (preview != null) served.ads.map { it.id }.toSet() else emptySet()
    val t = now()
    for (slot in AdSlot.entries) {
      val candidates = when {
        preview != null -> served.ads.filter { it.slot == slot }
        slot in houseSlots -> served.forSlot(slot, t)
        else -> emptyList()
      }
      val ad = pickWeighted(candidates, random) { it.weight }
      val file = ad?.let { repo.image(it, mayDownload = !saver) }
      states.getValue(slot).value = if (ad != null && file != null) {
        AdState.Ready(
          AdContent(
            id = ad.id,
            slot = slot,
            image = file,
            width = ad.width,
            height = ad.height,
            altText = ad.altText,
            actionUrl = ad.actionUrl,
            actionText = ad.actionText,
          )
        )
      } else {
        AdState.Hidden
      }
    }
    if (preview == null) {
      repo.keepOnly(served.ads)
      if (prefs.sendCounts) AdCountsWorker.schedulePeriodic(context)
    }
  }

  /** One impression (≥ 50 % visible for 1 s) and/or one tap, filed under today in Tehran. */
  internal fun record(content: AdContent, impressions: Int, clicks: Int) {
    if (content.id in previewIds || !prefs.sendCounts) return
    scope.launch(Dispatchers.IO) {
      counts.add(content.id, content.slot.id, tehranDay(now()), impressions, clicks)
      AdCountsWorker.scheduleSoon(context)
    }
  }

  internal fun reportLinks() = server.reportLinks()

  internal fun bookIntent(bookId: String): Intent? = server.bookIntent(context, bookId)

  companion object {
    private const val MIN_REFRESH_MS = 60_000L

    private fun versionCodeOf(context: Context): Int = runCatching {
      val info = context.packageManager.getPackageInfo(context.packageName, 0)
      if (Build.VERSION.SDK_INT >= 28) info.longVersionCode.toInt()
      else @Suppress("DEPRECATION") info.versionCode
    }.getOrDefault(0)

    /** Data Saver on and a metered network: no prefetch (only what is already on disk). */
    private fun backgroundDataRestricted(context: Context): Boolean {
      val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
      return cm.isActiveNetworkMetered &&
        cm.restrictBackgroundStatus == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED
    }
  }
}
