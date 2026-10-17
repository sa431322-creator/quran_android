package com.quran.mobile.bookmark.migration

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.quran.data.model.SuraAyah
import com.quran.data.model.highlight.HighlightColor
import com.quran.mobile.bookmark.importdata.MobileSyncImportBookmark
import com.quran.mobile.bookmark.importdata.MobileSyncImportCollection
import com.quran.mobile.bookmark.importdata.MobileSyncImportCollectionBookmark
import com.quran.mobile.bookmark.importdata.MobileSyncImportData
import com.quran.mobile.bookmark.importdata.MobileSyncImportReadingBookmark
import com.quran.mobile.bookmark.importdata.MobileSyncImportReadingSession
import com.quran.shared.persistence.model.ReadingBookmarkSlot
import java.io.File

/** What a mobile-sync database from before 0.1.36 (`quran.db`) holds. */
data class PreviousMobileSyncData(
  val importData: MobileSyncImportData,
  val highlights: Map<SuraAyah, HighlightColor>
) {
  fun isEmpty(): Boolean = importData.isEmpty() && highlights.isEmpty()
}

/**
 * Reads `quran.db` from mobile-sync 0.1.14–0.1.35 with plain SQL, since 0.1.36 can no longer open it.
 * Those versions share the tables read here (0.1.30 only added collection.name_key, unused here);
 * a table or column that is missing is skipped instead of failing the whole read.
 */
internal object PreviousMobileSyncDatabaseReader {

  fun read(file: File): PreviousMobileSyncData {
    return SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
      val bookmarks = mutableListOf<MobileSyncImportBookmark>()
      val collectionBookmarks = mutableListOf<MobileSyncImportCollectionBookmark>()
      val highlights = mutableMapOf<SuraAyah, HighlightColor>()

      if (db.hasColumns("bookmark", "local_id", "sura", "ayah", "deleted") &&
        db.hasColumns("bookmark_collection", "bookmark_local_id", "collection_local_id", "is_active", "modified_at") &&
        db.hasColumns("collection", "local_id", "name", "deleted", "is_default", "is_system")
      ) {
        db.rawQuery(
          """
          SELECT c.name, c.is_default, c.is_system, b.sura, b.ayah, bc.modified_at
          FROM bookmark_collection bc
          JOIN bookmark b ON b.local_id = bc.bookmark_local_id
          JOIN collection c ON c.local_id = bc.collection_local_id
          WHERE bc.is_active = 1 AND b.deleted = 0 AND c.deleted = 0
          ORDER BY bc.modified_at
          """.trimIndent(),
          null
        ).use { cursor ->
          while (cursor.moveToNext()) {
            val name = cursor.getString(0)
            val sura = cursor.getInt(3)
            val ayah = cursor.getInt(4)
            val timestamp = cursor.getLong(5)
            when {
              cursor.getInt(1) == 1 -> bookmarks += MobileSyncImportBookmark(sura, ayah, timestamp)
              cursor.getInt(2) == 1 ->
                highlightColor(name)?.let { highlights[SuraAyah(sura, ayah)] = it }
              else -> collectionBookmarks += MobileSyncImportCollectionBookmark(name, sura, ayah, timestamp)
            }
          }
        }
      }

      val collections = if (db.hasColumns("collection", "name", "modified_at", "deleted", "is_default", "is_system")) {
        db.rawQuery(
          "SELECT name, modified_at FROM collection WHERE deleted = 0 AND is_default = 0 AND is_system = 0",
          null
        ).use { cursor ->
          cursor.map { MobileSyncImportCollection(it.getString(0), it.getLong(1)) }
        }
      } else {
        emptyList()
      }

      val readingSessions =
        if (db.hasColumns("reading_session", "chapter_number", "verse_number", "modified_at", "deleted")) {
          db.rawQuery(
            "SELECT chapter_number, verse_number, modified_at FROM reading_session WHERE deleted = 0",
            null
          ).use { cursor ->
            cursor.map { MobileSyncImportReadingSession(it.getInt(0), it.getInt(1), it.getLong(2)) }
          }
        } else {
          emptyList()
        }

      val readingBookmarks =
        if (db.hasColumns("reading_bookmark", "slot", "name", "bookmark_type", "sura", "ayah", "page", "modified_at")) {
          db.rawQuery(
            """
            SELECT slot, name, bookmark_type, sura, ayah, page, modified_at
            FROM reading_bookmark WHERE bookmark_type IS NOT NULL
            """.trimIndent(),
            null
          ).use { cursor -> cursor.map { it.toReadingBookmark() }.filterNotNull() }
        } else {
          emptyList()
        }

      PreviousMobileSyncData(
        MobileSyncImportData(
          bookmarks = bookmarks,
          collections = collections,
          collectionBookmarks = collectionBookmarks,
          readingSessions = readingSessions,
          readingBookmarks = readingBookmarks
        ),
        highlights
      )
    }
  }

  private fun Cursor.toReadingBookmark(): MobileSyncImportReadingBookmark? {
    val slot = when (getInt(0)) {
      1 -> ReadingBookmarkSlot.TEAL
      2 -> ReadingBookmarkSlot.ORANGE
      3 -> ReadingBookmarkSlot.RED
      else -> return null
    }
    val name = if (isNull(1)) null else getString(1)
    val timestamp = getLong(6)
    return when (getString(2)) {
      "AYAH" -> if (isNull(3) || isNull(4)) null else {
        MobileSyncImportReadingBookmark.Ayah(slot, getInt(3), getInt(4), timestamp, name)
      }
      "PAGE" -> if (isNull(5)) null else {
        MobileSyncImportReadingBookmark.Page(slot, getInt(5), timestamp, name)
      }
      else -> null
    }
  }

  /** Highlights were system collections named `system:highlights:<color>`; pink was once red. */
  private fun highlightColor(collectionName: String): HighlightColor? {
    return when (collectionName.removePrefix(HIGHLIGHT_PREFIX).takeIf { collectionName.startsWith(HIGHLIGHT_PREFIX) }) {
      "blue" -> HighlightColor.BLUE
      "pink", "red" -> HighlightColor.PINK
      "green" -> HighlightColor.GREEN
      "yellow" -> HighlightColor.YELLOW
      "purple" -> HighlightColor.PURPLE
      else -> null
    }
  }

  private fun SQLiteDatabase.hasColumns(table: String, vararg columns: String): Boolean {
    val present = rawQuery("PRAGMA table_info($table)", null).use { cursor ->
      cursor.map { it.getString(it.getColumnIndexOrThrow("name")) }.toSet()
    }
    return present.containsAll(columns.toList())
  }

  private inline fun <T> Cursor.map(transform: (Cursor) -> T): List<T> {
    val result = ArrayList<T>(count)
    while (moveToNext()) result += transform(this)
    return result
  }

  private const val HIGHLIGHT_PREFIX = "system:highlights:"
}
