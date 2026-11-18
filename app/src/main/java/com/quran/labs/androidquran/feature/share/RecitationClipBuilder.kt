package com.quran.labs.androidquran.feature.share

import android.content.Context
import android.media.MediaMetadataRetriever
import com.quran.data.core.QuranInfo
import com.quran.data.model.SuraAyah
import com.quran.labs.androidquran.common.audio.model.QariItem
import com.quran.labs.androidquran.data.TimingRepository
import com.quran.labs.androidquran.util.AudioUtils
import com.quran.labs.androidquran.util.QuranFileUtils
import com.quran.labs.androidquran.util.ZipUtils
import com.quran.mobile.di.qualifier.ApplicationContext
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.Locale
import kotlin.coroutines.coroutineContext

/** One piece of recitation: a part of an audio file that belongs to one ayah. */
data class RecitationSegment(
  val ayah: SuraAyah,
  val file: File,
  val startMs: Long,
  /** null: to the end of the file */
  val endMs: Long?,
  val durationMs: Long
)

/** Why a recitation could not be prepared. */
class RecitationException(val reason: Reason, cause: Throwable? = null) : Exception(reason.name, cause) {
  enum class Reason { NO_TIMINGS, DOWNLOAD_FAILED }
}

/**
 * Finds the recitation of an ayah range for a qari: the reader's downloaded files when they are
 * there, else downloads the needed files into the share cache. Gapless qaris (one file per sura)
 * are cut with the qari's ayah timings; gapped qaris have one file per ayah.
 */
class RecitationClipBuilder @Inject constructor(
  @param:ApplicationContext private val appContext: Context,
  private val quranInfo: QuranInfo,
  private val audioUtils: AudioUtils,
  private val timingRepository: TimingRepository,
  private val quranFileUtils: QuranFileUtils,
  private val okHttpClient: OkHttpClient
) {

  /**
   * Deletes recitation files downloaded for earlier shares once they are older than a day, or
   * the oldest ones when they take more than [MAX_CACHE_BYTES].
   */
  fun trimCache() {
    val directory = File(appContext.cacheDir, "share/audio")
    val files = directory.walkTopDown().filter { it.isFile }.sortedBy { it.lastModified() }.toMutableList()
    val dayAgo = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
    var total = files.sumOf { it.length() }
    val iterator = files.iterator()
    while (iterator.hasNext()) {
      val file = iterator.next()
      if (file.lastModified() < dayAgo || total > MAX_CACHE_BYTES) {
        total -= file.length()
        file.delete()
      }
    }
  }

  suspend fun build(
    qari: QariItem,
    start: SuraAyah,
    end: SuraAyah,
    onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }
  ): List<RecitationSegment> = withContext(Dispatchers.IO) {
    val ayahs = ayahsBetween(start, end)
    if (qari.isGapless) gapless(qari, ayahs, onProgress) else gapped(qari, ayahs, onProgress)
  }

  private suspend fun gapless(
    qari: QariItem,
    ayahs: List<SuraAyah>,
    onProgress: (Int, Int) -> Unit
  ): List<RecitationSegment> {
    val databasePath = audioUtils.getQariDatabasePathIfGapless(qari)
      ?: throw RecitationException(RecitationException.Reason.NO_TIMINGS)
    if (!File(databasePath).exists()) downloadTimings(qari, File(databasePath))
    val suras = ayahs.map { it.sura }.distinct()
    val segments = mutableListOf<RecitationSegment>()
    suras.forEachIndexed { index, sura ->
      coroutineContext.ensureActive()
      val file = audioFile(qari, String.format(Locale.US, "%03d", sura), String.format(Locale.US, "%03d.mp3", sura))
      onProgress(index + 1, suras.size)
      val timings = timingRepository.timings(databasePath, sura).ayahTimings
      if (timings.size() == 0) throw RecitationException(RecitationException.Reason.NO_TIMINGS)
      val fileDuration by lazy { duration(file) }
      ayahs.filter { it.sura == sura }.forEach { ayah ->
        // the first ayah keeps the bismillah that comes before it in the sura's file
        val startMs = if (ayah.ayah == 1) 0L else timings.get(ayah.ayah, -1L)
        if (startMs < 0) throw RecitationException(RecitationException.Reason.NO_TIMINGS)
        val next = timings.get(ayah.ayah + 1, -1L)
        val endMs = when {
          next > startMs -> next
          timings.get(999, -1L) > startMs -> timings.get(999, -1L)
          else -> null
        }
        segments += (if (endMs != null) {
          RecitationSegment(ayah, file, startMs, endMs, endMs - startMs)
        } else {
          toEndOfFile(ayah, file, startMs, fileDuration)
        } ?: return@forEach)
      }
    }
    return segments
  }

  private suspend fun gapped(
    qari: QariItem,
    ayahs: List<SuraAyah>,
    onProgress: (Int, Int) -> Unit
  ): List<RecitationSegment> {
    val segments = mutableListOf<RecitationSegment>()
    ayahs.forEachIndexed { index, ayah ->
      coroutineContext.ensureActive()
      // gapped qaris record the bismillah once (1:1); it opens every sura but 1 and 9
      if (ayah.ayah == 1 && ayah.sura != 1 && ayah.sura != 9) {
        val bismillah = ayahFile(qari, SuraAyah(1, 1))
        toEndOfFile(ayah, bismillah, 0, duration(bismillah))?.let { segments += it }
      }
      val file = ayahFile(qari, ayah)
      segments += toEndOfFile(ayah, file, 0, duration(file))
        ?: throw RecitationException(RecitationException.Reason.DOWNLOAD_FAILED) // unreadable file
      onProgress(index + 1, ayahs.size)
    }
    return segments
  }

  /**
   * A clip from [startMs] to the end of [file]. An MP3's length is first estimated and corrected by
   * a few ms once it has been read, and Transformer stops with an error when a clip's length
   * changes, so the clip ends a little before the end (the silence after the recitation). The card
   * of a video is shown for exactly the same time.
   */
  private fun toEndOfFile(ayah: SuraAyah, file: File, startMs: Long, fileDurationMs: Long): RecitationSegment? {
    val endMs = fileDurationMs - END_MARGIN_MS
    return if (endMs > startMs) RecitationSegment(ayah, file, startMs, endMs, endMs - startMs) else null
  }

  private fun ayahFile(qari: QariItem, ayah: SuraAyah): File =
    audioFile(
      qari,
      ayah.sura.toString() + File.separator + ayah.ayah,
      String.format(Locale.US, "%03d%03d.mp3", ayah.sura, ayah.ayah)
    )

  /**
   * Fetches the qari's ayah timings into the qari's audio folder, where the player keeps them too,
   * the same way the player does before gapless playback (a zip next to the audio, unpacked).
   */
  private fun downloadTimings(qari: QariItem, database: File) {
    val name = qari.databaseName ?: throw RecitationException(RecitationException.Reason.NO_TIMINGS)
    val directory = database.parentFile ?: throw RecitationException(RecitationException.Reason.NO_TIMINGS)
    directory.mkdirs()
    val zip = File(directory, name + AudioUtils.ZIP_EXTENSION)
    download(quranFileUtils.gaplessDatabaseRootUrl.trimEnd('/') + "/" + zip.name, zip)
    val unzipped = ZipUtils.unzipFile(zip, directory, zip.name, null)
    zip.delete()
    if (!unzipped || !database.exists()) throw RecitationException(RecitationException.Reason.NO_TIMINGS)
  }

  /**
   * The reader's own copy at `<qari path>/<localName>.mp3` if there is one, else a copy in the
   * share cache, downloaded from the qari's server when needed.
   */
  private fun audioFile(qari: QariItem, localName: String, remoteName: String): File {
    val local = audioUtils.getLocalQariUrl(qari)?.let { File(it, "$localName.mp3") }
    if (local != null && local.length() > 0) return local

    val cached = File(appContext.cacheDir, "share/audio/${qari.id}/$remoteName")
    if (cached.length() > 0) return cached
    cached.parentFile?.mkdirs()
    download(qari.url("mp3") + remoteName, cached)
    return cached
  }

  /** Saves [url] to [destination], through a ".part" file so a broken download never looks complete. */
  private fun download(url: String, destination: File) {
    try {
      okHttpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
        if (!response.isSuccessful) throw IOException("HTTP ${response.code} for $url")
        val partial = File(destination.path + ".part")
        response.body.byteStream().use { input ->
          partial.outputStream().use { output -> input.copyTo(output) }
        }
        if (!partial.renameTo(destination)) throw IOException("could not save $destination")
      }
    } catch (e: IOException) {
      throw RecitationException(RecitationException.Reason.DOWNLOAD_FAILED, e)
    }
  }

  private fun duration(file: File): Long {
    val retriever = MediaMetadataRetriever()
    return try {
      retriever.setDataSource(file.path)
      retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
    } catch (e: RuntimeException) {
      0L
    } finally {
      retriever.release()
    }
  }

  private companion object {
    const val MAX_CACHE_BYTES = 200L * 1024 * 1024
    const val END_MARGIN_MS = 150L
  }

  private fun ayahsBetween(start: SuraAyah, end: SuraAyah): List<SuraAyah> {
    val result = mutableListOf<SuraAyah>()
    for (sura in start.sura..end.sura) {
      val first = if (sura == start.sura) start.ayah else 1
      val last = if (sura == end.sura) end.ayah else quranInfo.getNumberOfAyahs(sura)
      for (ayah in first..last) result += SuraAyah(sura, ayah)
    }
    return result
  }
}
