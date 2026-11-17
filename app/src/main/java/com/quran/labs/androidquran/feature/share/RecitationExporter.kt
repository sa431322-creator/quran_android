package com.quran.labs.androidquran.feature.share

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.Clock
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSourceBitmapLoader
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.mp3.Mp3Extractor
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultAssetLoaderFactory
import androidx.media3.transformer.DefaultDecoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Joins recitation segments into one file: an MP4 video where each ayah's card stays on screen
 * while that ayah is recited, or an M4A with the recitation only.
 */
@OptIn(UnstableApi::class)
class RecitationExporter(private val context: Context) {

  /** Empties the folder that holds a video's frames, so earlier exports leave nothing behind. */
  fun clearFrames(directory: File) {
    directory.deleteRecursively()
    directory.mkdirs()
  }

  /** Saves one frame as PNG, since Transformer reads images from files. */
  suspend fun writeFrame(frame: Bitmap, directory: File, index: Int): File =
    withContext(Dispatchers.IO) {
      val file = File(directory, "frame_$index.png")
      file.outputStream().use { frame.compress(Bitmap.CompressFormat.PNG, 100, it) }
      file
    }

  /**
   * Exports [segments]; with [frames] (one per segment, same order) the output is a video,
   * otherwise audio only. [onProgress] gets 0–100. Must be called from the main thread.
   */
  suspend fun export(
    segments: List<RecitationSegment>,
    frames: List<File>?,
    output: File,
    onProgress: (Int) -> Unit
  ): File {
    output.parentFile?.mkdirs()
    output.delete()

    val audio = EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_AUDIO))
      .addItems(segments.map { segment ->
        val clipping = MediaItem.ClippingConfiguration.Builder()
          .setStartPositionMs(segment.startMs)
          .apply { segment.endMs?.let { setEndPositionMs(it) } }
          .build()
        val item = MediaItem.Builder()
          .setUri(Uri.fromFile(segment.file))
          .setClippingConfiguration(clipping)
          .build()
        EditedMediaItem.Builder(item).setRemoveVideo(true).build()
      })
      .build()

    val sequences = mutableListOf<EditedMediaItemSequence>()
    if (frames != null) {
      require(frames.size == segments.size) { "one frame per segment" }
      val video = EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_VIDEO))
        .addItems(segments.mapIndexed { index, segment ->
          val item = MediaItem.Builder()
            .setUri(Uri.fromFile(frames[index]))
            .setImageDurationMs(segment.durationMs.coerceAtLeast(1))
            .build()
          EditedMediaItem.Builder(item).setFrameRate(FRAME_RATE).build()
        })
        .build()
      sequences += video
    }
    sequences += audio
    val composition = Composition.Builder(sequences).build()

    // index seeking makes the cut points in variable bit rate MP3 files exact
    val extractors = DefaultExtractorsFactory()
      .setMp3ExtractorFlags(Mp3Extractor.FLAG_ENABLE_INDEX_SEEKING)
    val assetLoaderFactory = DefaultAssetLoaderFactory(
      context,
      DefaultDecoderFactory(context),
      Clock.DEFAULT,
      DefaultMediaSourceFactory(context, extractors),
      DataSourceBitmapLoader(context)
    )

    return suspendCancellableCoroutine { continuation ->
      val handler = Handler(Looper.getMainLooper())
      val progressHolder = ProgressHolder()
      lateinit var transformer: Transformer
      val poll = object : Runnable {
        override fun run() {
          if (transformer.getProgress(progressHolder) == Transformer.PROGRESS_STATE_AVAILABLE) {
            onProgress(progressHolder.progress)
          }
          handler.postDelayed(this, 250)
        }
      }
      transformer = Transformer.Builder(context)
        .setAudioMimeType(MimeTypes.AUDIO_AAC)
        .apply { if (frames != null) setVideoMimeType(MimeTypes.VIDEO_H264) }
        .setAssetLoaderFactory(assetLoaderFactory)
        .addListener(object : Transformer.Listener {
          override fun onCompleted(composition: Composition, exportResult: ExportResult) {
            handler.removeCallbacks(poll)
            onProgress(100)
            if (continuation.isActive) continuation.resume(output)
          }

          override fun onError(
            composition: Composition,
            exportResult: ExportResult,
            exportException: ExportException
          ) {
            handler.removeCallbacks(poll)
            output.delete()
            if (continuation.isActive) continuation.resumeWithException(exportException)
          }
        })
        .build()
      continuation.invokeOnCancellation {
        handler.post {
          handler.removeCallbacks(poll)
          transformer.cancel()
          output.delete()
        }
      }
      transformer.start(composition, output.path)
      handler.post(poll)
    }
  }

  companion object {
    private const val FRAME_RATE = 30
  }
}
