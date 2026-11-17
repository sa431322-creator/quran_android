package com.quran.labs.androidquran.presenter.translation

import android.content.Context
import android.util.Pair
import com.quran.common.networking.ReachableUrl
import com.quran.data.constant.DependencyInjectionConstants
import com.quran.data.di.AppScope
import com.quran.labs.androidquran.dao.translation.Translation
import com.quran.labs.androidquran.dao.translation.TranslationItem
import com.quran.labs.androidquran.dao.translation.TranslationList
import com.quran.labs.androidquran.data.Constants
import com.quran.labs.androidquran.database.DatabaseHandler.Companion.getDatabaseHandler
import com.quran.labs.androidquran.database.TranslationsDBAdapter
import com.quran.labs.androidquran.tasnim.TasnimDataHost
import com.quran.labs.androidquran.util.QuranFileUtils
import com.quran.labs.androidquran.util.QuranSettings
import com.quran.mobile.di.qualifier.ApplicationContext
import com.squareup.moshi.Moshi
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.buffer
import okio.sink
import okio.source
import timber.log.Timber
import java.io.File
import java.io.IOException

@SingleIn(AppScope::class)
open class TranslationManagerPresenter @Inject internal constructor(
  @ApplicationContext private val appContext: Context,
  private val okHttpClient: OkHttpClient,
  private val quranSettings: QuranSettings,
  private val translationsDBAdapter: TranslationsDBAdapter,
  private val quranFileUtils: QuranFileUtils,
  @Named(DependencyInjectionConstants.TASNIM_API_BASE_URL) tasnimBaseUrl: String
) {
  internal var host: String = TasnimDataHost.of(tasnimBaseUrl)

  private val scope = MainScope()

  private val moshiTranslationListAdapter by lazy {
    val moshi = Moshi.Builder().build()
    moshi.adapter(TranslationList::class.java)
  }

  fun checkForUpdates() {
    Timber.d("checking whether we should update translations..")
    val isCacheStale = System.currentTimeMillis() -
        quranSettings.lastUpdatedTranslationDate > Constants.MIN_TRANSLATION_REFRESH_TIME
    if (isCacheStale) {
      Timber.d("updating translations list...")
      getTranslations(true)
        .catch { Timber.e(it) }
        .launchIn(scope)
    }
  }

  fun getTranslations(forceDownload: Boolean): Flow<List<TranslationItem>> {
    val isCacheStale = System.currentTimeMillis() -
        quranSettings.lastUpdatedTranslationDate > Constants.MIN_TRANSLATION_REFRESH_TIME
    val flow = if (forceDownload) {
      remoteTranslationList()
    } else {
      val flow = merge(cachedTranslationList(), remoteTranslationList())
      if (isCacheStale) {
        flow
      } else {
        flow.take(1)
      }
    }

    return flow
      .map { list -> mergeWithServerTranslations(list.translations.map(::reachable)) }
      .onEach { translations ->
        val updatedTranslations = translations.any { it.needsUpgrade() }
        quranSettings.setHaveUpdatedTranslations(updatedTranslations)
      }
  }

  /**
   * A development server lists its own files as `http://localhost:3000/...`; the download must
   * go to the address the app reaches that server at. quran.app links are left as they are.
   */
  internal fun reachable(translation: Translation): Translation {
    val url = ReachableUrl.of(translation.fileUrl, host.toHttpUrlOrNull())
    return if (url == translation.fileUrl) translation else translation.copy(fileUrl = url)
  }

  suspend fun updateItem(item: TranslationItem) {
    withContext(Dispatchers.IO) {
      // for upgrades, remove the old file to stop the tafseer from showing up
      // twice. this happens because old and new tafaseer (ex ibn kathir) have
      // different ids when they target different schema versions, and so the
      // old file needs to be removed from the database explicitly
      val (_, minimumVersion, _, _, _, fileName) = item.translation
      if (minimumVersion >= 5) {
        translationsDBAdapter.deleteTranslationByFileName(fileName)
      }
      translationsDBAdapter.writeTranslationUpdates(listOf(item))
    }
  }

  suspend fun updateItemOrdering(items: List<TranslationItem>) {
    withContext(Dispatchers.IO) {
      translationsDBAdapter.writeTranslationUpdates(items)
    }
  }

  internal fun cachedTranslationList(): Flow<TranslationList> {
    return flow {
      try {
        val cachedFile = cachedFile
        if (cachedFile.exists()) {
          val list = moshiTranslationListAdapter.fromJson(cachedFile.source().buffer())
          if (list != null && list.translations.isNotEmpty()) {
            emit(list)
          }
        }
      } catch (e: Exception) {
        Timber.e(e)
      }
    }
    .flowOn(Dispatchers.IO)
  }

  /**
   * Where the list is asked for when the Tasnim server fails: quran.app, or null when the
   * list already comes from quran.app.
   */
  internal var fallbackHost: String? =
    TasnimDataHost.of(Constants.HOST).takeIf { it != host }

  /**
   * The list from the Tasnim server; when that fails, from quran.app. A quran.app list is
   * shown but not cached, so the next refresh asks the Tasnim server again and its own
   * choices (hidden and added translations) are not overwritten.
   */
  internal fun remoteTranslationList(): Flow<TranslationList> {
    return flow {
      val fromServer = try {
        fetchTranslationList(host)
      } catch (e: Exception) {
        if (e is CancellationException) throw e
        val fallback = fallbackHost ?: throw e
        Timber.w(e, "Tasnim translation list failed, asking %s", fallback)
        val list = try {
          fetchTranslationList(fallback)
        } catch (fallbackError: Exception) {
          e.addSuppressed(fallbackError)
          throw e
        }
        if (list != null) emit(list)
        return@flow
      }
      if (fromServer != null) {
        writeTranslationList(fromServer)
        emit(fromServer)
      }
    }
      .flowOn(Dispatchers.IO)
  }

  /** The list at [base], or null when it is empty. */
  private fun fetchTranslationList(base: String): TranslationList? {
    val request: Request = Request.Builder()
      .url(base + WEB_SERVICE_ENDPOINT)
      .build()
    okHttpClient.newCall(request).execute().use { response ->
      // e.g. 503 from the Tasnim server before its first sync: treat it like a network
      // failure, instead of parsing the error body.
      if (!response.isSuccessful) {
        throw IOException("Translation list answered ${response.code}")
      }
      val result = moshiTranslationListAdapter.fromJson(response.body.source())
      return result?.takeIf { it.translations.isNotEmpty() }
    }
  }

  open fun writeTranslationList(list: TranslationList) {
    val cacheFile = cachedFile
    try {
      val directory = cacheFile.getParentFile()
      val directoryExists = directory != null && (directory.mkdirs() || directory.isDirectory())
      if (directoryExists) {
        if (cacheFile.exists()) {
          cacheFile.delete()
        }
        val sink = cacheFile.sink().buffer()
        moshiTranslationListAdapter.toJson(sink, list)
        sink.close()
        quranSettings.lastUpdatedTranslationDate = System.currentTimeMillis()
      }
    } catch (e: Exception) {
      cacheFile.delete()
      Timber.e(e)
    }
  }

  internal open val cachedFile: File
    get() {
      val dir = quranFileUtils.getQuranDatabaseDirectory()
      return File(dir, CACHED_RESPONSE_FILE_NAME)
    }

  internal open suspend fun mergeWithServerTranslations(serverTranslations: List<Translation>): List<TranslationItem> {
    val localTranslations = translationsDBAdapter.translationsHash()
    val databaseDir = quranFileUtils.getQuranDatabaseDirectory()
    val updates: MutableList<TranslationItem> = ArrayList()

    val results = serverTranslations.mapIndexed { _, translation ->
      val local = localTranslations[translation.id]
      val dbFile = File(databaseDir, translation.fileName)
      val translationExists = dbFile.exists()
      var override: TranslationItem? = null

      val item: TranslationItem
      if (translationExists) {
        if (local == null) {
          // text version, schema version
          val versions = getVersionFromDatabase(translation.fileName)
          item = TranslationItem(translation, versions.first)
          if (versions.second != translation.minimumVersion) {
            // schema change, write downloaded schema version to the db and return server item
            override = TranslationItem(translation.withSchema(versions.second), versions.first)
          }
        } else {
          item = TranslationItem(translation, local.version, local.displayOrder)
        }
      } else {
        item = TranslationItem(translation)
      }

      val exists = if (translationExists && !item.exists()) {
        // delete the file, it has been corrupted
        dbFile.delete()
        false
      } else {
        translationExists
      }

      if (local == null && exists || local != null && !exists) {
        if (override != null && item.translation.minimumVersion >= 5) {
          // certain schema changes, especially those going to v5, keep the same filename while
          // changing the database entry id. this could cause duplicate entries in the database.
          // work around it by removing the existing entries before doing the updates.
          translationsDBAdapter.deleteTranslationByFileName(override.translation.fileName)
        }
        updates.add(override ?: item)
      } else if (local != null && local.languageCode == null) {
        // older items don't have a language code
        updates.add(item)
      }
      item
    }

    if (updates.isNotEmpty()) {
      translationsDBAdapter.writeTranslationUpdates(updates)
    }
    return results
  }

  private fun getVersionFromDatabase(filename: String): Pair<Int, Int> {
    try {
      val handler = getDatabaseHandler(appContext, filename, quranFileUtils)
      if (handler.validDatabase()) {
        return Pair(handler.getTextVersion(), handler.getSchemaVersion())
      }
    } catch (e: Exception) {
      Timber.d(e, "exception opening database: %s", filename)
    }
    return Pair(0, 0)
  }

  companion object {
    private const val WEB_SERVICE_ENDPOINT = "data/translations.php?v=5"
    private const val CACHED_RESPONSE_FILE_NAME = "translations.v5.cache"
  }
}
