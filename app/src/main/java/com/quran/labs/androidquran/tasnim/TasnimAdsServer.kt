package com.quran.labs.androidquran.tasnim

import android.content.Context
import android.content.Intent
import com.quran.data.constant.DependencyInjectionConstants
import com.quran.data.di.AppScope
import com.quran.mobile.feature.ads.AdsConfig
import com.quran.mobile.feature.ads.AdsServer
import com.quran.mobile.feature.library.details.LibraryBookActivity
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.SingleIn

/**
 * The server side of house ads (ADR 0010): the `ads` section of the app settings the launch screen
 * already downloads, read apart from [AppConfig] so a bad section can never break the rest.
 * Builds without ads (ads-noop) never call it.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class TasnimAdsServer internal constructor(
  private val repository: TasnimConfigRepository,
  override val baseUrl: String,
  private val now: () -> Long,
) : AdsServer {

  @Inject
  constructor(
    repository: TasnimConfigRepository,
    @Named(DependencyInjectionConstants.TASNIM_API_BASE_URL) baseUrl: String,
  ) : this(repository, baseUrl, System::currentTimeMillis)

  private var lastRefreshMs = Long.MIN_VALUE / 2

  override fun cachedConfig(): AdsConfig = AdsConfig.fromAppConfigJson(repository.cachedJson())

  /** Asks again at most every 15 minutes; the launch screen also refreshes it at start. */
  override suspend fun refreshConfig(): AdsConfig {
    if (now() - lastRefreshMs >= REFRESH_MS) {
      lastRefreshMs = now()
      repository.refreshConfig()
    }
    return cachedConfig()
  }

  override fun reportLinks(): List<String> {
    val links = repository.cached().links
    return listOfNotNull(
      links.email?.let { if (it.startsWith("mailto:", ignoreCase = true)) it else "mailto:$it" },
      links.messenger,
    ).filter { it.isNotBlank() }
  }

  override fun bookIntent(context: Context, bookId: String): Intent =
    LibraryBookActivity.intent(context, bookId)

  private companion object {
    const val REFRESH_MS = 15 * 60_000L
  }
}
