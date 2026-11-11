package com.quran.mobile.feature.library.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import java.io.IOException

/**
 * Renders the pages of a PDF with the platform's [PdfRenderer]. The renderer opens one page
 * at a time, so every call is serialized on [lock].
 */
class PdfPages private constructor(
  private val descriptor: ParcelFileDescriptor,
  private val renderer: PdfRenderer
) : Closeable {

  private val lock = Any()
  private var closed = false

  val pageCount: Int = renderer.pageCount

  /** Page [index] drawn on white, [width] pixels wide. */
  suspend fun render(index: Int, width: Int): Bitmap = withContext(Dispatchers.IO) {
    synchronized(lock) {
      if (closed) throw IOException("document is closed")
      renderer.openPage(index).use { page ->
        val height = (width.toLong() * page.height / page.width).toInt()
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
          eraseColor(Color.WHITE)
          page.render(this, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        }
      }
    }
  }

  override fun close() {
    synchronized(lock) {
      if (closed) return
      closed = true
      renderer.close()
      descriptor.close()
    }
  }

  companion object {
    /**
     * The bundled PDF at [assetPath] as a file. [PdfRenderer] needs a seekable file, so the
     * asset is copied to app storage the first time, and again after an app update in case it
     * changed.
     */
    suspend fun bundledFile(context: Context, assetPath: String): File =
      withContext(Dispatchers.IO) {
        val file = File(context.noBackupFilesDir, assetPath)
        val installedAt = context.packageManager
          .getPackageInfo(context.packageName, 0)
          .lastUpdateTime
        if (!file.exists() || file.lastModified() < installedAt) {
          file.parentFile?.mkdirs()
          // copy then rename, so an interrupted copy is never taken for a whole book
          val partial = File(file.path + ".part")
          context.assets.open(assetPath).use { input ->
            partial.outputStream().use { output -> input.copyTo(output) }
          }
          if (!partial.renameTo(file)) throw IOException("could not store $assetPath")
        }
        file
      }

    /**
     * Opens [file]. This is quick and not a suspend function, so the caller always gets the
     * open document and can close it.
     */
    fun open(file: File): PdfPages {
      val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
      return try {
        PdfPages(descriptor, PdfRenderer(descriptor))
      } catch (e: Exception) {
        // IOException for a broken file, SecurityException for a password-protected one
        descriptor.close()
        throw e
      }
    }
  }
}
