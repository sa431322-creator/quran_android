package com.quran.mobile.feature.library.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import kotlin.math.roundToInt

sealed interface DownloadState {
  data object Idle : DownloadState
  data object Failed : DownloadState
  /** [fraction] is 0 to 1, or null while the size is unknown. */
  data class Running(val fraction: Float?) : DownloadState
}

/**
 * Book downloads, kept for the life of the process so leaving or rotating the details page
 * doesn't abort one. [states] has an entry only for books downloading or whose download failed.
 */
object LibraryDownloads {

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
  private val _states = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
  val states: StateFlow<Map<String, DownloadState>> = _states.asStateFlow()

  /** Starts downloading [book] unless it is already downloading. */
  fun start(context: Context, book: LibraryBook) {
    if (_states.value[book.id] is DownloadState.Running) return
    val appContext = context.applicationContext
    set(book.id, DownloadState.Running(null))
    scope.launch {
      val result = try {
        LibraryFiles.download(appContext, book) { fraction ->
          // whole percents only, so screens aren't redrawn for every few kilobytes
          val rounded = fraction?.let { (it * 100).roundToInt() / 100f }
          if ((_states.value[book.id] as? DownloadState.Running)?.fraction != rounded) {
            set(book.id, DownloadState.Running(rounded))
          }
        }
        DownloadState.Idle
      } catch (_: IOException) {
        DownloadState.Failed
      }
      set(book.id, result)
    }
  }

  private fun set(bookId: String, state: DownloadState) {
    _states.update { if (state == DownloadState.Idle) it - bookId else it + (bookId to state) }
  }
}
