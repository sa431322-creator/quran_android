package com.quran.labs.androidquran.tasnim

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.quran.data.constant.DependencyInjectionConstants
import com.quran.data.di.AppScope
import com.quran.mobile.di.qualifier.ApplicationContext
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

// The Tasnim server's app settings and announcements (tasneem-backend ADR 0005).
// Field names match the server JSON; every field has the same default the server uses.

@JsonClass(generateAdapter = true)
data class AppLinks(
  val website: String? = null,
  val email: String? = null,
  val videoChannel: String? = null,
  val messenger: String? = null
)

/** Stream links for the live and radio screens; our own channel first, then the Quran channel. */
@JsonClass(generateAdapter = true)
data class AppStreams(
  val ownLive: String? = null,
  val quranLive: String? = null,
  val ownRadio: String? = null,
  val quranRadio: String? = null
)

@JsonClass(generateAdapter = true)
data class AppFeatures(val library: Boolean = true, val live: Boolean = false, val radio: Boolean = false)

@JsonClass(generateAdapter = true)
data class AppMaintenance(val enabled: Boolean = false, val message: String? = null)

@JsonClass(generateAdapter = true)
data class AppUpdate(
  val minVersionCode: Int? = null,
  val latestVersionCode: Int? = null,
  val url: String? = null,
  val notes: String? = null
)

@JsonClass(generateAdapter = true)
data class AppConfig(
  val links: AppLinks = AppLinks(),
  val streams: AppStreams = AppStreams(),
  val features: AppFeatures = AppFeatures(),
  val maintenance: AppMaintenance = AppMaintenance(),
  val update: AppUpdate = AppUpdate()
)

@JsonClass(generateAdapter = true)
data class AnnouncementItem(val icon: String = "info", val title: String = "", val body: String = "")

@JsonClass(generateAdapter = true)
data class Announcement(
  val id: Int,
  val title: String,
  val body: String = "",
  val items: List<AnnouncementItem> = emptyList(),
  val imageUrl: String? = null,
  val actionText: String? = null,
  val actionUrl: String? = null
)

@JsonClass(generateAdapter = true)
data class AnnouncementList(val items: List<Announcement> = emptyList())

internal interface TasnimService {
  @GET("api/v1/app/config")
  suspend fun config(): AppConfig

  @GET("api/v1/announcements")
  suspend fun announcements(
    @Query("versionCode") versionCode: Int,
    @Query("platform") platform: String = "android"
  ): AnnouncementList
}

/** What the app must do about its version. */
enum class UpdateNeed { NONE, SUGGESTED, REQUIRED }

/**
 * Below `minVersionCode` the app must be updated; below `latestVersionCode` an update is
 * suggested, unless the person already dismissed the suggestion for that same latest version.
 */
fun updateNeed(update: AppUpdate, versionCode: Int, dismissedLatest: Int?): UpdateNeed {
  update.minVersionCode?.let { if (versionCode < it) return UpdateNeed.REQUIRED }
  val latest = update.latestVersionCode ?: return UpdateNeed.NONE
  return if (versionCode < latest && dismissedLatest != latest) UpdateNeed.SUGGESTED else UpdateNeed.NONE
}

/** The first announcement this person has not seen yet (the server sends only active ones). */
fun firstUnseen(items: List<Announcement>, seen: Set<Int>): Announcement? =
  items.firstOrNull { it.id !in seen }

/**
 * Reads settings and announcements from the Tasnim server and keeps the last good settings, so
 * the launch screen shows the right links even offline. Without a server (blank base URL, e.g.
 * release builds before a domain exists) everything stays at the defaults.
 */
@SingleIn(AppScope::class)
class TasnimConfigRepository internal constructor(
  private val service: TasnimService?,
  private val prefs: SharedPreferences
) {

  @Inject
  constructor(
    @ApplicationContext context: Context,
    okHttpClient: OkHttpClient,
    @Named(DependencyInjectionConstants.TASNIM_API_BASE_URL) baseUrl: String
  ) : this(
    if (baseUrl.isBlank()) null else Retrofit.Builder()
      .baseUrl(baseUrl)
      .client(okHttpClient)
      .addConverterFactory(MoshiConverterFactory.create())
      .build()
      .create(TasnimService::class.java),
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
  )

  private val configAdapter = Moshi.Builder().build().adapter(AppConfig::class.java)

  /** Whether this build talks to a Tasnim server at all (false when the base URL is blank). */
  val hasServer: Boolean get() = service != null

  /** The last settings received, or the defaults. Instant; safe on the main thread. */
  fun cached(): AppConfig =
    prefs.getString(KEY_CONFIG, null)
      ?.let { runCatching { configAdapter.fromJson(it) }.getOrNull() }
      ?: AppConfig()

  /** Fresh settings from the server (saved for next time), or the cached ones on any failure. */
  suspend fun refreshConfig(): AppConfig {
    val api = service ?: return cached()
    return try {
      val fresh = api.config()
      withContext(Dispatchers.IO) {
        prefs.edit { putString(KEY_CONFIG, configAdapter.toJson(fresh)) }
      }
      fresh
    } catch (e: CancellationException) {
      throw e
    } catch (_: Exception) {
      cached()
    }
  }

  /** Active announcements for this app version; empty when offline or without a server. */
  suspend fun announcements(versionCode: Int): List<Announcement> {
    val api = service ?: return emptyList()
    return try {
      api.announcements(versionCode).items
    } catch (e: CancellationException) {
      throw e
    } catch (_: Exception) {
      emptyList()
    }
  }

  fun seenAnnouncements(): Set<Int> =
    prefs.getStringSet(KEY_SEEN, emptySet()).orEmpty().mapNotNull { it.toIntOrNull() }.toSet()

  fun markSeen(id: Int) {
    prefs.edit { putStringSet(KEY_SEEN, seenAnnouncements().map { it.toString() }.toSet() + id.toString()) }
  }

  /** The latest version whose update suggestion was dismissed, if any. */
  fun dismissedUpdate(): Int? = prefs.getInt(KEY_DISMISSED_UPDATE, -1).takeIf { it >= 0 }

  fun dismissUpdate(latestVersionCode: Int) {
    prefs.edit { putInt(KEY_DISMISSED_UPDATE, latestVersionCode) }
  }

  internal companion object {
    const val PREFS = "tasnim_server"
    const val KEY_CONFIG = "app_config_json"
    const val KEY_SEEN = "announcements_seen"
    const val KEY_DISMISSED_UPDATE = "update_dismissed_latest"
  }
}
