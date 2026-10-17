package com.quran.mobile.feature.tajweed.data

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
 * Page layout from assets, and the per-page QPC V4 tajweed fonts (from QUL, Tarteel's CDN),
 * downloaded the first time a page is opened and kept in app storage.
 */
class TajweedRepository(context: Context) {
  private val appContext = context.applicationContext
  private val fontDirectory = File(appContext.filesDir, "tajweed/v4")
  // one lock per page: a slow download never holds up another page's font
  private val pageLocks = ConcurrentHashMap<Int, Mutex>()

  /** Parsed once per process, so rotating the screen does not parse the asset again. */
  suspend fun data(): TajweedData = loadMutex.withLock {
    data ?: withContext(Dispatchers.IO) { readData() }.also { data = it }
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

  private fun download(page: Int): File {
    fontDirectory.mkdirs()
    val target = fontFile(page)
    val temp = File(fontDirectory, "p$page.ttf.part")
    val connection = URL("$FONT_BASE_URL/p$page.ttf").openConnection() as HttpURLConnection
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

  private fun readData(): TajweedData {
    val text = appContext.assets.open(PAGES_ASSET).bufferedReader().use { it.readText() }
    val pageArray = JSONObject(text).getJSONArray("pages")
    val raw = List(pageArray.length()) { index ->
      val page = pageArray.getJSONObject(index)
      val lines = page.getJSONArray("l")
      val starts = page.getJSONArray("h")
      val first = page.getJSONArray("f")
      RawTajweedPage(
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
    val pages = buildTajweedPages(raw)
    return TajweedData(pages, suraHeaderPages(pages))
  }

  companion object {
    private val loadMutex = Mutex()
    private var data: TajweedData? = null

    private const val PAGES_ASSET = "tajweed/pages.json"
    private const val FONT_BASE_URL = "https://static-cdn.tarteel.ai/qul/fonts/quran_fonts/v4-tajweed/ttf"
    private const val TIMEOUT_MS = 20_000
    private const val MIN_FONT_BYTES = 1_000L
  }
}

class TajweedData(val pages: List<TajweedPage>, val suraHeaderPages: Map<Int, Int>)
