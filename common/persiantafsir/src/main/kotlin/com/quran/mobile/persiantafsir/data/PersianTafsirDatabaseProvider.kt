package com.quran.mobile.persiantafsir.data

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.quran.data.core.QuranFileManager
import com.quran.data.di.AppScope
import com.quran.mobile.di.qualifier.ApplicationContext
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

/**
 * Provides the bundled Persian tafsir database. The database ships in the apk's assets and is
 * copied once into the Quran database directory, so it is always available offline.
 */
@SingleIn(AppScope::class)
class PersianTafsirDatabaseProvider @Inject constructor(
  @param:ApplicationContext private val appContext: Context,
  private val quranFileManager: QuranFileManager
) {
  private val mutex = Mutex()
  private var cachedDatabase: PersianTafsirDatabase? = null

  private suspend fun ensureDatabase(): File? {
    val directory = quranFileManager.databaseDirectory()
    val databaseFile = File(directory, DATABASE_FILE_NAME)
    if (databaseFile.exists()) {
      return databaseFile
    }

    return withContext(Dispatchers.IO) {
      runCatching {
        // copy to a temporary name first so an interrupted copy never looks complete
        val temporaryName = "$DATABASE_FILE_NAME.tmp"
        quranFileManager.copyFromAssetsRelative(ASSET_NAME, temporaryName, directory)
        val temporaryFile = File(directory, temporaryName)
        check(temporaryFile.renameTo(databaseFile)) { "unable to rename $temporaryFile" }
        removeOldVersions(directory)
        databaseFile
      }.onFailure { Timber.e(it, "unable to copy the Persian tafsir database") }
        .getOrNull()
    }
  }

  private fun removeOldVersions(directory: File) {
    directory.listFiles { _, name ->
      name.startsWith(DATABASE_PREFIX) && !name.startsWith(DATABASE_FILE_NAME)
    }?.forEach { it.delete() }
  }

  suspend fun provideDatabase(): PersianTafsirDatabase? {
    return mutex.withLock {
      cachedDatabase ?: ensureDatabase()?.let { file ->
        val driver = AndroidSqliteDriver(
          PersianTafsirDatabase.Schema, appContext, name = file.absolutePath
        )
        PersianTafsirDatabase(driver).also { cachedDatabase = it }
      }
    }
  }

  companion object {
    private const val ASSET_NAME = "persian_tafsir.db"
    private const val DATABASE_PREFIX = "persian_tafsir_v"

    // bump this whenever the bundled asset changes so existing installs pick up the new copy
    private const val DATABASE_VERSION = 1
    private const val DATABASE_FILE_NAME = "$DATABASE_PREFIX$DATABASE_VERSION.db"
  }
}
