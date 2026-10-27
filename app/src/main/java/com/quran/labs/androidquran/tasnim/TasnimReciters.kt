package com.quran.labs.androidquran.tasnim

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import com.quran.data.constant.DependencyInjectionConstants
import com.quran.data.di.AppScope
import com.quran.data.model.audio.Qari
import com.quran.labs.androidquran.common.audio.util.ServerQariSource
import com.quran.mobile.di.qualifier.ApplicationContext
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import dev.zacsweers.metro.ContributesBinding
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
import java.util.Locale

// The reciter list from the Tasnim server (tasneem-backend ADR 0008, GET /api/v1/reciters).
// Field names match the server JSON.

@JsonClass(generateAdapter = true)
data class ReciterNames(val fa: String? = null, val ar: String? = null, val en: String = "")

@JsonClass(generateAdapter = true)
data class ServerReciter(
  val id: Int,
  val names: ReciterNames = ReciterNames(),
  val url: String = "",
  val path: String = "",
  val db: String? = null,
  val hasGaplessAlternative: Boolean = false
)

@JsonClass(generateAdapter = true)
data class ServerReciterList(val version: Int = 0, val items: List<ServerReciter> = emptyList())

internal interface TasnimReciterService {
  @GET("api/v1/reciters")
  suspend fun reciters(): ServerReciterList
}

/** The name for the device language: Persian, then Arabic, then English. */
internal fun ReciterNames.pick(language: String): String {
  val fa = fa?.takeIf { it.isNotBlank() }
  val ar = ar?.takeIf { it.isNotBlank() }
  return when (language) {
    "fa" -> fa ?: ar
    "ar" -> ar ?: fa
    else -> null
  } ?: en.takeIf { it.isNotBlank() } ?: fa ?: ar ?: ""
}

/**
 * Turns the server list into qaris, dropping entries the audio code could not use (no link or
 * folder, or an id / folder used twice). Empty = fall back to the built-in list.
 */
internal fun ServerReciterList.toQaris(language: String): List<Qari> {
  val ids = HashSet<Int>()
  val paths = HashSet<String>()
  return items
    .filter { it.url.startsWith("http") && it.url.endsWith("/") && it.path.isNotBlank() }
    .filter { ids.add(it.id) && paths.add(it.path) }
    .map {
      Qari(
        id = it.id,
        nameResource = 0,
        url = it.url,
        path = it.path,
        hasGaplessAlternative = it.hasGaplessAlternative,
        db = it.db?.takeIf { db -> db.isNotBlank() },
        name = it.names.pick(language)
      )
    }
}

/**
 * Keeps the last reciter list received from the Tasnim server. The list is read once per app
 * process: the audio code caches the qaris at start-up, so a fresh list from [refresh] is used
 * from the next start. Without a server (blank base URL) or before the first answer, the app
 * uses its built-in reciters.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class TasnimReciterRepository internal constructor(
  private val service: TasnimReciterService?,
  private val prefs: SharedPreferences,
  private val language: () -> String
) : ServerQariSource {

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
      .create(TasnimReciterService::class.java),
    context.getSharedPreferences(TasnimConfigRepository.PREFS, Context.MODE_PRIVATE),
    // The app's own language (set in its settings, Persian by default), else the device's.
    { AppCompatDelegate.getApplicationLocales()[0]?.language ?: Locale.getDefault().language }
  )

  private val adapter = Moshi.Builder().build().adapter(ServerReciterList::class.java)

  private val snapshot: List<Qari>? by lazy {
    if (service == null) return@lazy null
    prefs.getString(KEY_RECITERS, null)
      ?.let { runCatching { adapter.fromJson(it) }.getOrNull() }
      ?.toQaris(language())
      ?.takeIf { it.isNotEmpty() }
  }

  /** The list saved at the last successful refresh, for this whole process. Instant. */
  override fun cached(): List<Qari>? = snapshot

  /** Fetches and saves the list for the next start; false when offline or without a server. */
  suspend fun refresh(): Boolean {
    val api = service ?: return false
    return try {
      val fresh = api.reciters()
      withContext(Dispatchers.IO) {
        prefs.edit { putString(KEY_RECITERS, adapter.toJson(fresh)) }
      }
      true
    } catch (e: CancellationException) {
      throw e
    } catch (_: Exception) {
      false
    }
  }

  internal companion object {
    const val KEY_RECITERS = "reciters_json"
  }
}
