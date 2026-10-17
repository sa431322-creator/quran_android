package com.quran.mobile.bookmark.migration

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.google.common.truth.Truth.assertThat
import com.quran.data.core.QuranInfo
import com.quran.data.di.AppCoroutineScope
import com.quran.data.model.highlight.HighlightColor
import com.quran.labs.androidquran.pages.data.madani.MadaniDataSource
import com.quran.mobile.bookmark.di.MobileSyncDatabase
import com.quran.mobile.bookmark.importdata.MobileSyncImporterImpl
import com.quran.mobile.bookmark.model.BookmarksDaoImpl
import com.quran.mobile.bookmark.model.HighlightsDaoImpl
import com.quran.mobile.bookmark.model.RepositoryBackedTestBookmarkCollectionsState
import com.quran.mobile.bookmark.time.FakeMobileSyncTimestampProvider
import com.quran.shared.persistence.model.AyahReadingBookmark
import com.quran.shared.persistence.model.PageReadingBookmark
import com.quran.shared.persistence.model.ReadingBookmarkSlot
import com.quran.shared.persistence.repository.bookmark.repository.BookmarksRepositoryImpl
import com.quran.shared.persistence.repository.collection.repository.CollectionsRepositoryImpl
import com.quran.shared.persistence.repository.collectionbookmark.repository.CollectionBookmarksRepositoryImpl
import com.quran.shared.persistence.repository.readingbookmark.repository.ReadingBookmarksRepositoryImpl
import com.quran.shared.persistence.repository.readingsession.repository.ReadingSessionsRepositoryImpl
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

@RunWith(RobolectricTestRunner::class)
class PreviousMobileSyncDatabaseMigrationTest {

  private lateinit var context: Context
  private lateinit var mobileSyncDatabase: MobileSyncDatabase
  private lateinit var appCoroutineScope: AppCoroutineScope
  private lateinit var bookmarksDao: BookmarksDaoImpl
  private lateinit var collectionBookmarksRepository: CollectionBookmarksRepositoryImpl
  private lateinit var migration: PreviousMobileSyncDatabaseMigrationImpl
  private lateinit var previousDatabase: File

  @Before
  fun setUp() {
    context = RuntimeEnvironment.getApplication().applicationContext as Context
    context.deleteDatabase("user_data.db")
    context.deleteDatabase("quran.db")
    context.getDatabasePath("quran.db.migrated").delete()
    previousDatabase = context.getDatabasePath("quran.db")
    mobileSyncDatabase = MobileSyncDatabase(context)
    appCoroutineScope = AppCoroutineScope()
    val collectionsRepository = CollectionsRepositoryImpl(mobileSyncDatabase.database.synced)
    collectionBookmarksRepository = CollectionBookmarksRepositoryImpl(mobileSyncDatabase.database.synced)
    val timestampProvider = FakeMobileSyncTimestampProvider()
    bookmarksDao = BookmarksDaoImpl(
      quranInfoProvider = { QuranInfo(MadaniDataSource()) },
      bookmarksRepository = BookmarksRepositoryImpl(mobileSyncDatabase.database.synced),
      collectionsRepository = collectionsRepository,
      collectionBookmarksRepository = collectionBookmarksRepository,
      bookmarkCollectionsState = RepositoryBackedTestBookmarkCollectionsState(
        collectionsRepository,
        collectionBookmarksRepository,
        appCoroutineScope
      ),
      timestampProvider = timestampProvider,
      appCoroutineScope = appCoroutineScope
    )
    migration = PreviousMobileSyncDatabaseMigrationImpl(
      appContext = context,
      mobileSyncImporter = MobileSyncImporterImpl(mobileSyncDatabase, context),
      highlightsDao = HighlightsDaoImpl(timestampProvider, collectionBookmarksRepository, appCoroutineScope)
    )
  }

  @After
  fun tearDown() {
    if (::appCoroutineScope.isInitialized) appCoroutineScope.cancel()
  }

  @Test
  fun `without an old database there is nothing to do`() = runTest {
    assertThat(migration.migrateIfNeeded()).isTrue()
    assertThat(bookmarksDao.bookmarks()).isEmpty()
  }

  @Test
  fun `copies bookmarks collections highlights and reading positions then retires the file`() = runTest {
    createPreviousDatabase {
      execSQL("INSERT INTO collection (local_id, name, is_default, is_system, modified_at) VALUES (1, 'Favorites', 1, 1, 1000)")
      execSQL("INSERT INTO collection (local_id, name, is_default, is_system, modified_at) VALUES (2, 'system:highlights:red', 0, 1, 1000)")
      execSQL("INSERT INTO collection (local_id, name, modified_at) VALUES (3, 'Hifz', 2000)")
      execSQL("INSERT INTO collection (local_id, name, modified_at) VALUES (4, 'Empty one', 3000)")
      execSQL("INSERT INTO collection (local_id, name, deleted, modified_at) VALUES (5, 'Removed', 1, 3000)")
      execSQL("INSERT INTO bookmark (local_id, sura, ayah) VALUES (10, 2, 255)")
      execSQL("INSERT INTO bookmark (local_id, sura, ayah) VALUES (11, 18, 10)")
      execSQL("INSERT INTO bookmark (local_id, sura, ayah, deleted) VALUES (12, 36, 1, 1)")
      execSQL("INSERT INTO bookmark_collection (bookmark_local_id, collection_local_id, modified_at) VALUES (10, 1, 5000)")
      execSQL("INSERT INTO bookmark_collection (bookmark_local_id, collection_local_id, modified_at) VALUES (11, 3, 6000)")
      execSQL("INSERT INTO bookmark_collection (bookmark_local_id, collection_local_id, modified_at) VALUES (11, 2, 6000)")
      execSQL("INSERT INTO bookmark_collection (bookmark_local_id, collection_local_id, is_active, modified_at) VALUES (10, 3, 0, 6000)")
      execSQL("INSERT INTO bookmark_collection (bookmark_local_id, collection_local_id, modified_at) VALUES (12, 1, 7000)")
      execSQL("INSERT INTO reading_bookmark (slot, bookmark_type, page, mushaf_id, modified_at) VALUES (1, 'PAGE', 42, 1, 8000)")
      execSQL("INSERT INTO reading_bookmark (slot, name, bookmark_type, sura, ayah, mushaf_id, modified_at) VALUES (2, 'Night', 'AYAH', 67, 1, 1, 9000)")
      execSQL("INSERT INTO reading_bookmark (slot) VALUES (3)")
      execSQL("INSERT INTO reading_session (chapter_number, verse_number, modified_at) VALUES (19, 5, 10000)")
    }

    assertThat(migration.migrateIfNeeded()).isTrue()

    val bookmarks = bookmarksDao.bookmarks()
    assertThat(bookmarks.map { it.sura to it.ayah }).containsExactly(2 to 255, 18 to 10)
    val tags = bookmarksDao.tags()
    val hifz = tags.single { it.name == "Hifz" }
    assertThat(tags.map { it.name }).containsAtLeast("Hifz", "Empty one")
    assertThat(tags.map { it.name }).doesNotContain("Removed")
    assertThat(bookmarks.single { it.sura == 18 }.tags).contains(hifz.id)
    assertThat(bookmarks.single { it.sura == 2 }.tags).doesNotContain(hifz.id)

    val highlight = collectionBookmarksRepository.getHighlightsFlow().first().single()
    assertThat(highlight.sura to highlight.ayah).isEqualTo(18 to 10)
    assertThat(highlight.color.name).isEqualTo(HighlightColor.PINK.name)

    val readingBookmarks = ReadingBookmarksRepositoryImpl(mobileSyncDatabase.database.synced).getReadingBookmarks()
    assertThat((readingBookmarks.single { it.slot == ReadingBookmarkSlot.TEAL } as PageReadingBookmark).page).isEqualTo(42)
    val ayah = readingBookmarks.single { it.slot == ReadingBookmarkSlot.ORANGE } as AyahReadingBookmark
    assertThat(ayah.sura to ayah.ayah).isEqualTo(67 to 1)
    assertThat(ayah.name).isEqualTo("Night")

    val sessions = ReadingSessionsRepositoryImpl(mobileSyncDatabase.database.synced).getReadingSessions()
    assertThat(sessions.map { it.sura to it.ayah }).containsExactly(19 to 5)

    assertThat(previousDatabase.exists()).isFalse()
    assertThat(context.getDatabasePath("quran.db.migrated").isFile).isTrue()
    assertThat(migration.migrateIfNeeded()).isTrue()
    assertThat(bookmarksDao.bookmarks()).hasSize(2)
  }

  @Test
  fun `an unreadable old database is kept for the next try`() = runTest {
    previousDatabase.parentFile?.mkdirs()
    previousDatabase.writeText("not a database")

    assertThat(migration.migrateIfNeeded()).isFalse()
    assertThat(previousDatabase.isFile).isTrue()
  }

  /** The tables and columns mobile-sync 0.1.20–0.1.35 created, as far as the migration reads them. */
  private fun createPreviousDatabase(fill: SQLiteDatabase.() -> Unit) {
    previousDatabase.parentFile?.mkdirs()
    SQLiteDatabase.openOrCreateDatabase(previousDatabase, null).use { db ->
      db.execSQL(
        """
        CREATE TABLE collection(local_id INTEGER PRIMARY KEY AUTOINCREMENT, remote_id TEXT, name TEXT NOT NULL,
          name_key TEXT, created_at INTEGER NOT NULL DEFAULT 0, modified_at INTEGER NOT NULL DEFAULT 0,
          deleted INTEGER NOT NULL DEFAULT 0, is_edited INTEGER NOT NULL DEFAULT 0,
          is_default INTEGER NOT NULL DEFAULT 0, is_system INTEGER NOT NULL DEFAULT 0,
          pending_version INTEGER NOT NULL DEFAULT 0)
        """.trimIndent()
      )
      db.execSQL(
        """
        CREATE TABLE bookmark(local_id INTEGER PRIMARY KEY AUTOINCREMENT, remote_id TEXT, sura INTEGER NOT NULL,
          ayah INTEGER NOT NULL, mushaf_id INTEGER NOT NULL DEFAULT 1, deleted INTEGER NOT NULL DEFAULT 0,
          created_at INTEGER NOT NULL DEFAULT 0, modified_at INTEGER NOT NULL DEFAULT 0)
        """.trimIndent()
      )
      db.execSQL(
        """
        CREATE TABLE bookmark_collection(local_id INTEGER PRIMARY KEY AUTOINCREMENT,
          bookmark_local_id INTEGER NOT NULL, collection_local_id INTEGER NOT NULL,
          is_active INTEGER NOT NULL DEFAULT 1, created_at INTEGER NOT NULL DEFAULT 0,
          modified_at INTEGER NOT NULL DEFAULT 0)
        """.trimIndent()
      )
      db.execSQL(
        """
        CREATE TABLE reading_bookmark(local_id INTEGER PRIMARY KEY AUTOINCREMENT, remote_id TEXT,
          slot INTEGER NOT NULL, name TEXT, bookmark_type TEXT, sura INTEGER, ayah INTEGER, page INTEGER,
          mushaf_id INTEGER, created_at INTEGER NOT NULL DEFAULT 0, modified_at INTEGER NOT NULL DEFAULT 0)
        """.trimIndent()
      )
      db.execSQL(
        """
        CREATE TABLE reading_session(local_id INTEGER PRIMARY KEY AUTOINCREMENT, remote_id TEXT,
          chapter_number INTEGER NOT NULL, verse_number INTEGER NOT NULL, deleted INTEGER NOT NULL DEFAULT 0,
          created_at INTEGER NOT NULL DEFAULT 0, modified_at INTEGER NOT NULL DEFAULT 0)
        """.trimIndent()
      )
      db.fill()
    }
  }
}
