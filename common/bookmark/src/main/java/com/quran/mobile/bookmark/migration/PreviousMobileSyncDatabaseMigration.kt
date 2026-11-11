package com.quran.mobile.bookmark.migration

import android.content.Context
import com.quran.data.dao.HighlightsDao
import com.quran.data.di.AppScope
import com.quran.mobile.bookmark.importdata.MobileSyncImporter
import com.quran.mobile.di.qualifier.ApplicationContext
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * mobile-sync 0.1.36 moved its data from `quran.db` to a fresh `user_data.db` without a migration.
 * This copies ayah bookmarks, collections, highlights, reading bookmarks and recent pages from the
 * old file once, then renames it (keeping it on disk as a backup) so it is not imported again.
 */
interface PreviousMobileSyncDatabaseMigration {
  /**
   * Returns why the old database could not be migrated, or null when it was (or there is none).
   * A failed migration is retried on the next call.
   */
  suspend fun migrateIfNeeded(): Throwable?
}

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class PreviousMobileSyncDatabaseMigrationImpl @Inject constructor(
  @param:ApplicationContext private val appContext: Context,
  private val mobileSyncImporter: MobileSyncImporter,
  private val highlightsDao: HighlightsDao
) : PreviousMobileSyncDatabaseMigration {

  private val mutex = Mutex()
  private val preferences by lazy {
    appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
  }

  override suspend fun migrateIfNeeded(): Throwable? = mutex.withLock {
    withContext(Dispatchers.IO) {
      val database = appContext.getDatabasePath(PREVIOUS_DATABASE_NAME)
      if (!database.isFile) return@withContext null

      try {
        // if only the rename failed last time, importing again could undo the user's newer changes
        val identity = "${database.length()}:${database.lastModified()}"
        if (preferences.getString(KEY_IMPORTED_FILE, null) != identity) {
          val data = PreviousMobileSyncDatabaseReader.read(database)
          if (!data.importData.isEmpty()) {
            mobileSyncImporter.importData(data.importData, deleteExisting = false)
          }
          data.highlights.forEach { (ayah, color) -> highlightsDao.setHighlight(ayah, color) }
          preferences.edit().putString(KEY_IMPORTED_FILE, identity).commit()
        }
        retire(database)
        null
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: Exception) {
        exception
      }
    }
  }

  /** Renames the database, then its side files; the copy stays as a backup of the old data. */
  private fun retire(database: File) {
    val target = File(database.path + MIGRATED_SUFFIX)
    if (!database.renameTo(target)) throw IOException("Unable to rename $database to $target")
    listOf("-journal", "-wal", "-shm").forEach { suffix ->
      val side = File(database.path + suffix)
      if (side.exists()) side.renameTo(File(target.path + suffix))
    }
  }

  companion object {
    const val PREVIOUS_DATABASE_NAME = "quran.db"
    const val MIGRATED_SUFFIX = ".migrated"
    private const val PREFERENCES_NAME = "previous_mobile_sync_migration"
    private const val KEY_IMPORTED_FILE = "imported_file"
  }
}
