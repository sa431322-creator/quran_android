package com.quran.mobile.feature.ads.house

import android.util.AtomicFile
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.time.LocalDate
import java.util.UUID

/** Counts of one ad in one slot on one Tehran day. */
data class CountRow(
  val adId: Int,
  val slot: String,
  val day: String,
  val impressions: Int,
  val clicks: Int,
)

/** A batch waiting to be sent; its id is saved before the first try, so a retry is deduped. */
data class PendingBatch(val batchId: String, val rows: List<CountRow>)

/** Server limits per batch (tasneem-backend AD_EVENT_LIMITS). */
internal object EventLimits {
  const val ROWS = 50
  const val ROW_IMPRESSIONS = 200
  const val ROW_CLICKS = 20
  const val BATCH_IMPRESSIONS = 500
  const val BATCH_CLICKS = 50
  const val KEEP_DAYS = 7L
}

/**
 * Pure part of counting: adding to rows, and cutting the next batch within the server limits
 * (what does not fit stays for the next batch). Clicks never exceed impressions in a row.
 */
object CountMath {
  fun add(rows: List<CountRow>, adId: Int, slot: String, day: String, impressions: Int, clicks: Int): List<CountRow> {
    val i = rows.indexOfFirst { it.adId == adId && it.slot == slot && it.day == day }
    if (i < 0) return rows + CountRow(adId, slot, day, impressions, clicks)
    val r = rows[i]
    return rows.toMutableList().also {
      it[i] = r.copy(impressions = r.impressions + impressions, clicks = r.clicks + clicks)
    }
  }

  /** Drops rows older than [EventLimits.KEEP_DAYS] (the server refuses them anyway). */
  fun dropOld(rows: List<CountRow>, today: String): List<CountRow> {
    val oldest = LocalDate.parse(today).minusDays(EventLimits.KEEP_DAYS).toString()
    return rows.filter { it.day >= oldest }
  }

  /** Splits [rows] into (what goes into the next batch, what stays). */
  fun cut(rows: List<CountRow>): Pair<List<CountRow>, List<CountRow>> {
    val batch = mutableListOf<CountRow>()
    val rest = mutableListOf<CountRow>()
    var imp = 0
    var clk = 0
    for (r in rows) {
      if (batch.size >= EventLimits.ROWS) {
        rest += r
        continue
      }
      val i = minOf(r.impressions, EventLimits.ROW_IMPRESSIONS, EventLimits.BATCH_IMPRESSIONS - imp)
      val c = minOf(r.clicks, i, EventLimits.ROW_CLICKS, EventLimits.BATCH_CLICKS - clk)
      if (i <= 0 && c <= 0) {
        rest += r
        continue
      }
      batch += r.copy(impressions = i, clicks = c)
      imp += i
      clk += c
      // Clicks above the impressions sent now are dropped (a click always counted its view).
      val leftImp = r.impressions - i
      val leftClk = (r.clicks - c).coerceAtMost(leftImp)
      if (leftImp > 0 || leftClk > 0) rest += r.copy(impressions = leftImp, clicks = leftClk)
    }
    return batch to rest
  }
}

/**
 * Counts on disk (filesDir/ads/counts.json and pending.json), guarded by a mutex: the screen adds,
 * the worker sends. Only numbers per ad, slot and day — nothing about the person or device.
 */
class AdCountStore(dir: File) {
  private val mutex = Mutex()
  private val countsFile = AtomicFile(File(dir, "counts.json"))
  private val pendingFile = AtomicFile(File(dir, "pending.json"))
  private val moshi = Moshi.Builder().build()
  private val rowsAdapter = moshi.adapter<List<Map<String, Any>>>(
    Types.newParameterizedType(List::class.java, Map::class.java)
  )
  private val mapAdapter = moshi.adapter(Map::class.java)

  init {
    dir.mkdirs()
  }

  suspend fun add(adId: Int, slot: String, day: String, impressions: Int, clicks: Int) =
    mutex.withLock {
      writeRows(countsFile, CountMath.add(readRows(countsFile), adId, slot, day, impressions, clicks))
    }

  /** The batch to send now: the saved pending one, or a new one cut from the counts. */
  suspend fun nextBatch(today: String): PendingBatch? = mutex.withLock {
    readPending()?.let { return@withLock it }
    val rows = CountMath.dropOld(readRows(countsFile), today)
    if (rows.isEmpty()) {
      writeRows(countsFile, rows)
      return@withLock null
    }
    val (batch, rest) = CountMath.cut(rows)
    if (batch.isEmpty()) return@withLock null
    val pending = PendingBatch(UUID.randomUUID().toString(), batch)
    writePending(pending) // saved before the first send: a retry keeps the same id
    writeRows(countsFile, rest)
    pending
  }

  /** After 2xx/400/413/422: the batch is done (stored, a duplicate, or refused for good). */
  suspend fun finish(batchId: String) = mutex.withLock {
    if (readPending()?.batchId == batchId) pendingFile.delete()
  }

  /** Pending batches older than the keep window are dropped by the caller's day check. */
  suspend fun dropPendingIfOld(today: String) = mutex.withLock {
    val p = readPending() ?: return@withLock
    val kept = CountMath.dropOld(p.rows, today)
    if (kept.isEmpty()) pendingFile.delete() else if (kept.size != p.rows.size) writePending(p.copy(rows = kept))
  }

  suspend fun hasData(): Boolean = mutex.withLock {
    readPending() != null || readRows(countsFile).isNotEmpty()
  }

  suspend fun clear() = mutex.withLock {
    countsFile.delete()
    pendingFile.delete()
  }

  private fun readRows(file: AtomicFile): List<CountRow> = runCatching {
    rowsAdapter.fromJson(String(file.readFully()))!!.mapNotNull { it.toRow() }
  }.getOrDefault(emptyList())

  private fun writeRows(file: AtomicFile, rows: List<CountRow>) =
    write(file, rowsAdapter.toJson(rows.map { it.toMap() }))

  private fun readPending(): PendingBatch? = runCatching {
    val m = mapAdapter.fromJson(String(pendingFile.readFully()))!!
    PendingBatch(
      m["batchId"] as String,
      (m["rows"] as List<*>).mapNotNull { (it as? Map<*, *>)?.toRow() },
    )
  }.getOrNull()

  private fun writePending(p: PendingBatch) =
    write(pendingFile, mapAdapter.toJson(mapOf("batchId" to p.batchId, "rows" to p.rows.map { it.toMap() })))

  private fun write(file: AtomicFile, text: String) {
    val out = file.startWrite()
    try {
      out.write(text.toByteArray())
      file.finishWrite(out)
    } catch (e: Exception) {
      file.failWrite(out)
      throw e
    }
  }

  private fun Map<*, *>.toRow(): CountRow? = runCatching {
    CountRow(
      (this["adId"] as Number).toInt(),
      this["slot"] as String,
      this["day"] as String,
      (this["impressions"] as Number).toInt(),
      (this["clicks"] as Number).toInt(),
    )
  }.getOrNull()

  private fun CountRow.toMap(): Map<String, Any> =
    mapOf("adId" to adId, "slot" to slot, "day" to day, "impressions" to impressions, "clicks" to clicks)
}
