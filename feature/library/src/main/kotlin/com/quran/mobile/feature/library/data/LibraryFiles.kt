package com.quran.mobile.feature.library.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * A book's PDF on this device: bundled books are always here, and a book with a web link is
 * here once [download] has stored it.
 */
object LibraryFiles {

  /** The book's http(s) link, or null for a bundled book or one without a file. */
  val LibraryBook.remoteUrl: String?
    get() = fileUrl?.takeIf { it.startsWith("https://") || it.startsWith("http://") }

  fun downloadedFile(context: Context, bookId: String): File =
    File(context.noBackupFilesDir, "library/downloads/$bookId.pdf")

  /** Whether [book] can be read without a connection. */
  fun isOffline(context: Context, book: LibraryBook): Boolean =
    book.bundledAssetPath != null || downloadedFile(context, book.id).exists()

  /**
   * Downloads [book]'s PDF and returns the stored file. [onProgress] gets 0 to 1, or null
   * while the size is unknown. Throws IOException when the download fails.
   */
  suspend fun download(
    context: Context,
    book: LibraryBook,
    onProgress: (Float?) -> Unit
  ): File = withContext(Dispatchers.IO) {
    val url = book.remoteUrl ?: throw IOException("book ${book.id} has no web link")
    val file = downloadedFile(context, book.id)
    file.parentFile?.mkdirs()
    // written aside and renamed, so an interrupted download is never taken for a whole book
    val partial = File(file.path + ".part")
    val connection = URL(url).openConnection() as HttpURLConnection
    try {
      connection.connectTimeout = TIMEOUT_MILLIS
      connection.readTimeout = TIMEOUT_MILLIS
      if (connection.responseCode !in 200..299) {
        throw IOException("download failed with HTTP ${connection.responseCode}")
      }
      val total = connection.contentLengthLong
      connection.inputStream.use { input ->
        partial.outputStream().use { output ->
          val buffer = ByteArray(64 * 1024)
          var copied = 0L
          while (true) {
            ensureActive()
            val read = input.read(buffer)
            if (read < 0) break
            output.write(buffer, 0, read)
            copied += read
            onProgress(if (total > 0) copied.toFloat() / total else null)
          }
        }
      }
      if (!partial.renameTo(file)) throw IOException("could not store book ${book.id}")
      file
    } finally {
      connection.disconnect()
      partial.delete()
    }
  }

  private const val TIMEOUT_MILLIS = 30_000
}
