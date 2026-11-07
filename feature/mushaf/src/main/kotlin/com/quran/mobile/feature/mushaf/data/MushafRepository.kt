package com.quran.mobile.feature.mushaf.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * Which King Fahd (QPC) page fonts and page layout a text mushaf uses. Both are 604 pages of
 * 15 lines; every word is one glyph of its page's font.
 */
enum class MushafEdition(
  internal val pagesAsset: String,
  internal val fontBaseUrl: String,
  internal val fontDirectory: String,
  /** whether the Tasnim server keeps a copy of these fonts */
  internal val hasTasnimCopy: Boolean
) {
  /** QPC V4 (1441H) with tajweed colors */
  TAJWEED_V4(
    "mushaf/v4/pages.json",
    "https://static-cdn.tarteel.ai/qul/fonts/quran_fonts/v4-tajweed/ttf",
    "tajweed/v4",
    hasTasnimCopy = true
  ),

  /** QPC V2 (1421H), the Madinah mushaf: the reader's text pages, all 604. */
  PLAIN_V2(
    "mushaf/v2/pages.json",
    "https://static-cdn.tarteel.ai/qul/fonts/quran_fonts/v2/ttf",
    "mushaf/v2",
    hasTasnimCopy = false
  )
}

/**
 * Page layout from assets, and the per-page QPC fonts (from QUL, Tarteel's CDN),
 * downloaded the first time a page is opened and kept in app storage.
 */
class MushafRepository(
  context: Context,
  fallbackBaseUrl: String,
  private val edition: MushafEdition = MushafEdition.TAJWEED_V4
) {
  private val appContext = context.applicationContext
  private val fallbackBaseUrl = if (edition.hasTasnimCopy) fallbackBaseUrl else ""
  private val fontDirectory = File(appContext.filesDir, edition.fontDirectory)
  // one lock per page: a slow download never holds up another page's font
  private val pageLocks = ConcurrentHashMap<Int, Mutex>()

  /** Parsed once per process, so rotating the screen does not parse the asset again. */
  suspend fun data(): MushafData = loadMutex.withLock {
    loaded[edition] ?: withContext(Dispatchers.IO) { readData() }.also { loaded[edition] = it }
  }

  fun cachedFont(page: Int): File? = fontFile(page).takeIf { it.isFile }

  /** Returns the page's font, downloading it when it is not stored yet. */
  suspend fun font(page: Int): File {
    cachedFont(page)?.let { return it }
    return pageLocks.getOrPut(page) { Mutex() }.withLock {
      cachedFont(page) ?: withContext(Dispatchers.IO) { download(page) }
    }
  }

  private fun fontFile(page: Int) = File(fontDirectory, "p$page.ttf")

  /**
   * Tarteel first; when that fails, the copy on the Tasnim server (when the build names one).
   * After Tarteel failed, the copy is asked first for a while instead of waiting for its timeout
   * again on every page.
   */
  private fun download(page: Int): File {
    val primary = "${edition.fontBaseUrl}/p$page.ttf"
    val fallbackUrl = tajweedFontFallbackUrl(fallbackBaseUrl, page) ?: return download(page, primary)
    val tarteelDown = System.currentTimeMillis() - tarteelFailedAt < TARTEEL_DOWN_MS
    val (first, second) = if (tarteelDown) fallbackUrl to primary else primary to fallbackUrl
    return try {
      download(page, first).also { if (first == primary) tarteelFailedAt = 0L }
    } catch (e: IOException) {
      if (first == primary) tarteelFailedAt = System.currentTimeMillis()
      try {
        download(page, second)
      } catch (secondError: IOException) {
        e.addSuppressed(secondError)
        throw e
      }
    }
  }

  private fun download(page: Int, url: String): File {
    fontDirectory.mkdirs()
    val target = fontFile(page)
    val temp = File(fontDirectory, "p$page.ttf.part")
    val connection = URL(url).openConnection() as HttpURLConnection
    try {
      connection.connectTimeout = TIMEOUT_MS
      connection.readTimeout = TIMEOUT_MS
      if (connection.responseCode != HttpURLConnection.HTTP_OK) {
        throw IOException("font $page: HTTP ${connection.responseCode}")
      }
      connection.inputStream.use { input -> temp.outputStream().use { input.copyTo(it) } }
    } finally {
      connection.disconnect()
    }
    if (!isTrueType(temp) || !temp.renameTo(target)) {
      temp.delete()
      throw IOException("font $page: not a valid font")
    }
    return target
  }

  private fun isTrueType(file: File): Boolean {
    if (file.length() < MIN_FONT_BYTES) return false
    val header = ByteArray(4)
    file.inputStream().use { if (it.read(header) != 4) return false }
    return header.contentEquals(byteArrayOf(0, 1, 0, 0))
  }

  private fun readData(): MushafData {
    val text = appContext.assets.open(edition.pagesAsset).bufferedReader().use { it.readText() }
    val pageArray = JSONObject(text).getJSONArray("pages")
    val raw = List(pageArray.length()) { index ->
      val page = pageArray.getJSONObject(index)
      val lines = page.getJSONArray("l")
      val starts = page.getJSONArray("h")
      val first = page.getJSONArray("f")
      RawMushafPage(
        page = page.getInt("p"),
        sura = page.getInt("s"),
        juz = page.getInt("j"),
        firstAyah = first.getInt(0) to first.getInt(1),
        lines = (0 until lines.length()).associate { i ->
          val line = lines.getJSONArray(i)
          line.getInt(0) to line.getString(1).split('|')
        },
        suraStarts = List(starts.length()) { i ->
          val start = starts.getJSONArray(i)
          start.getInt(0) to start.getInt(1)
        },
        wordAyahs = buildList {
          val runs = page.getJSONArray("w")
          for (i in 0 until runs.length()) {
            val run = runs.getJSONArray(i)
            repeat(run.getInt(2)) { add(ayahKey(run.getInt(0), run.getInt(1))) }
          }
        }
      )
    }
    val pages = buildMushafPages(raw)
    return MushafData(pages, suraHeaderPages(pages))
  }

  companion object {
    private val loadMutex = Mutex()
    private val loaded = HashMap<MushafEdition, MushafData>()

    private const val TARTEEL_DOWN_MS = 5 * 60 * 1000L

    /** When a Tarteel download last failed (shared by every page and screen); 0 = working. */
    @Volatile private var tarteelFailedAt = 0L

    private const val TIMEOUT_MS = 20_000
    private const val MIN_FONT_BYTES = 1_000L
  }
}

class MushafData(val pages: List<MushafPage>, val suraHeaderPages: Map<Int, Int>)

/** The Tasnim server's copy of a page's font, or null without a Tasnim server. */
internal fun tajweedFontFallbackUrl(baseUrl: String, page: Int): String? {
  val base = baseUrl.trim().ifBlank { return null }
  return "${base.removeSuffix("/")}/data/qul/v4-tajweed/ttf/p$page.ttf"
}
