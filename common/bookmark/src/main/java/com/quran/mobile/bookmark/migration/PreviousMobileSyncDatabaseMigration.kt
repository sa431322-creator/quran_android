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

/**
 * mobile-sync 0.1.36 moved its data from `quran.db` to a fresh `user_data.db` without a migration.
 * This copies ayah bookmarks, collections, highlights, reading bookmarks and recent pages from the
 * old file once, then renames it (keeping it on disk as a backup) so it is not imported again.
 */
interface PreviousMobileSyncDatabaseMigration {
  /** False when the old database exists but could not be migrated; it is retried next time. */
  suspend fun migrateIfNeeded(): Boolean
}

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class PreviousMobileSyncDatabaseMigrationImpl @Inject constructor(
  @param:ApplicationContext private val appContext: Context,
  private val mobileSyncImporter: MobileSyncImporter,
  private val highlightsDao: HighlightsDao
) : PreviousMobileSyncDatabaseMigration {

  private val mutex = Mutex()

  override suspend fun migrateIfNeeded(): Boolean = mutex.withLock {
    withContext(Dispatchers.IO) {
      val database = appContext.getDatabasePath(PREVIOUS_DATABASE_NAME)
      if (!database.isFile) return@withContext true

      try {
        val data = PreviousMobileSyncDatabaseReader.read(database)
        if (!data.importData.isEmpty()) {
          mobileSyncImporter.importData(data.importData, deleteExisting = false)
        }
        data.highlights.forEach { (ayah, color) -> highlightsDao.setHighlight(ayah, color) }
        retire(database)
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: Exception) {
        false
      }
    }
  }

  /** Renames the database and its side files; the copy stays as a backup of the old data. */
  private fun retire(database: File): Boolean {
    val suffixes = listOf("", "-journal", "-wal", "-shm")
    suffixes.drop(1).forEach { suffix ->
      val side = File(database.path + suffix)
      if (side.exists()) side.renameTo(File(database.path + MIGRATED_SUFFIX + suffix))
    }
    return database.renameTo(File(database.path + MIGRATED_SUFFIX))
  }

  companion object {
    const val PREVIOUS_DATABASE_NAME = "quran.db"
    const val MIGRATED_SUFFIX = ".migrated"
  }
}
