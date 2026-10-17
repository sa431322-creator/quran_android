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
 * 0.1.20–0.1.35 share the tables read here (0.1.30 only added collection.name_key, unused here).
 * Older versions are read with whatever of those columns they have: a missing filter or timestamp
 * column is left out, and only a table without its core columns is skipped.
 */
internal object PreviousMobileSyncDatabaseReader {

  fun read(file: File): PreviousMobileSyncData {
    return SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
      val bookmarks = mutableListOf<MobileSyncImportBookmark>()
      val collectionBookmarks = mutableListOf<MobileSyncImportCollectionBookmark>()
      val highlights = mutableMapOf<SuraAyah, HighlightColor>()

      val bookmarkColumns = db.columns("bookmark")
      val membershipColumns = db.columns("bookmark_collection")
      val collectionColumns = db.columns("collection")
      if (bookmarkColumns.containsAll(listOf("local_id", "sura", "ayah")) &&
        membershipColumns.containsAll(listOf("bookmark_local_id", "collection_local_id")) &&
        collectionColumns.containsAll(listOf("local_id", "name", "is_default", "is_system"))
      ) {
        val timestamp = membershipColumns.timestamp("bc")
        val conditions = listOfNotNull(
          "bc.is_active = 1".takeIf { "is_active" in membershipColumns },
          "b.deleted = 0".takeIf { "deleted" in bookmarkColumns },
          "c.deleted = 0".takeIf { "deleted" in collectionColumns }
        )
        db.rawQuery(
          """
          SELECT c.name, c.is_default, c.is_system, b.sura, b.ayah, $timestamp
          FROM bookmark_collection bc
          JOIN bookmark b ON b.local_id = bc.bookmark_local_id
          JOIN collection c ON c.local_id = bc.collection_local_id
          ${conditions.where()}
          ORDER BY 6
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

      val savedCollections =
        if (collectionColumns.containsAll(listOf("name", "is_default", "is_system"))) {
          val conditions = listOfNotNull(
            "deleted = 0".takeIf { "deleted" in collectionColumns },
            "is_default = 0",
            "is_system = 0"
          )
          db.rawQuery(
            "SELECT name, ${collectionColumns.timestamp()} FROM collection ${conditions.where()}",
            null
          ).use { cursor ->
            cursor.map { MobileSyncImportCollection(it.getString(0), it.getLong(1)) }
          }
        } else {
          emptyList()
        }
      // the importer only creates listed collections, so every collection with an ayah is listed
      val collections = savedCollections + collectionBookmarks
        .filter { membership -> savedCollections.none { it.name == membership.collectionName } }
        .groupBy { it.collectionName }
        .map { (name, memberships) -> MobileSyncImportCollection(name, memberships.minOf { it.timestampMillis }) }

      val sessionColumns = db.columns("reading_session")
      val readingSessions =
        if (sessionColumns.containsAll(listOf("chapter_number", "verse_number"))) {
          val conditions = listOfNotNull("deleted = 0".takeIf { "deleted" in sessionColumns })
          db.rawQuery(
            "SELECT chapter_number, verse_number, ${sessionColumns.timestamp()} FROM reading_session ${conditions.where()}",
            null
          ).use { cursor ->
            cursor.map { MobileSyncImportReadingSession(it.getInt(0), it.getInt(1), it.getLong(2)) }
          }
        } else {
          emptyList()
        }

      val readingBookmarkColumns = db.columns("reading_bookmark")
      val readingBookmarks =
        if (readingBookmarkColumns.containsAll(listOf("slot", "name", "bookmark_type", "sura", "ayah", "page"))) {
          db.rawQuery(
            """
            SELECT slot, name, bookmark_type, sura, ayah, page, ${readingBookmarkColumns.timestamp()}
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

  private fun SQLiteDatabase.columns(table: String): Set<String> =
    rawQuery("PRAGMA table_info($table)", null).use { cursor ->
      cursor.map { it.getString(it.getColumnIndexOrThrow("name")) }.toSet()
    }

  /** The newest timestamp column a table has; mobile-sync's oldest tables may have neither. */
  private fun Set<String>.timestamp(alias: String? = null): String {
    val prefix = alias?.let { "$it." }.orEmpty()
    return when {
      "modified_at" in this -> prefix + "modified_at"
      "created_at" in this -> prefix + "created_at"
      else -> "0"
    }
  }

  private fun List<String>.where(): String =
    if (isEmpty()) "" else joinToString(" AND ", prefix = "WHERE ")

  private inline fun <T> Cursor.map(transform: (Cursor) -> T): List<T> {
    val result = ArrayList<T>(count)
    while (moveToNext()) result += transform(this)
    return result
  }

  private const val HIGHLIGHT_PREFIX = "system:highlights:"
}
