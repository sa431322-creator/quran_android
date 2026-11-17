package com.quran.labs.androidquran.feature.share

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import com.quran.data.model.SuraAyah
import com.quran.labs.androidquran.QuranApplication
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.common.audio.model.QariItem
import com.quran.labs.androidquran.common.audio.repository.CurrentQariManager
import com.quran.labs.androidquran.common.ui.core.QuranIcons
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import com.quran.labs.androidquran.util.AudioUtils
import com.quran.labs.androidquran.util.QuranUtils
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

/**
 * Tasnim: makes a shareable card of the selected ayahs, with a Quranic frame, one of five
 * backgrounds, the sura / ayah / page / juz, and optionally the Persian tafsir or a translation.
 * The card is shared as an image, or as a video (or audio) with the chosen qari's recitation.
 * See `docs/share-feature-plan.md`.
 */
class ShareComposerActivity : ComponentActivity() {

  @Inject lateinit var loader: ShareContentLoader
  @Inject lateinit var clipBuilder: RecitationClipBuilder
  @Inject lateinit var audioUtils: AudioUtils
  @Inject lateinit var currentQariManager: CurrentQariManager
  @Inject lateinit var shareAnalytics: ShareAnalytics

  private lateinit var renderer: ShareCardRenderer
  private val prefs by lazy { getSharedPreferences(PREFS, Context.MODE_PRIVATE) }

  // screen state
  private var options by mutableStateOf(ShareCardOptions())
  private var sources by mutableStateOf<List<TafsirSource>>(listOf(TafsirSource.Persian))
  private var wholePage by mutableStateOf(false)
  private var content by mutableStateOf<ShareCardContent?>(null)
  private var missingText by mutableStateOf(false)
  private var preview by mutableStateOf<Bitmap?>(null)
  private var qaris by mutableStateOf<List<QariItem>>(emptyList())
  private var qari by mutableStateOf<QariItem?>(null)
  private var progress by mutableStateOf<ExportProgress?>(null)
  private var errorMessage by mutableStateOf<String?>(null)

  private var exportJob: Job? = null

  private lateinit var start: SuraAyah
  private lateinit var end: SuraAyah

  private sealed interface ExportProgress {
    data class Preparing(val done: Int, val total: Int) : ExportProgress
    data class Rendering(val percent: Int) : ExportProgress
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    (application as QuranApplication).applicationComponent.inject(this)
    renderer = ShareCardRenderer(this)

    start = SuraAyah(intent.getIntExtra(EXTRA_START_SURA, 1), intent.getIntExtra(EXTRA_START_AYAH, 1))
    end = SuraAyah(intent.getIntExtra(EXTRA_END_SURA, start.sura), intent.getIntExtra(EXTRA_END_AYAH, start.ayah))
    val audioFirst = intent.getBooleanExtra(EXTRA_AUDIO, false)
    options = loadOptions()
    wholePage = savedInstanceState?.getBoolean(STATE_WHOLE_PAGE) ?: false

    lifecycleScope.launch {
      sources = loader.tafsirSources()
      if (sources.none { it.key == options.tafsirKey }) {
        options = options.copy(tafsirKey = TafsirSource.Persian.key)
      }
      reloadContent()
    }
    lifecycleScope.launch(Dispatchers.IO) {
      clipBuilder.trimCache()
      val list = audioUtils.getQariList(this@ShareComposerActivity)
      val currentId = currentQariManager.currentQariId()
      withContext(Dispatchers.Main) {
        qaris = list
        qari = list.firstOrNull { it.id == currentId } ?: list.firstOrNull()
      }
    }

    enableEdgeToEdge()
    setContent {
      QuranTheme {
        ComposerScreen(audioFirst)
      }
    }
  }

  override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    outState.putBoolean(STATE_WHOLE_PAGE, wholePage)
  }

  private fun range(): Pair<SuraAyah, SuraAyah> =
    if (wholePage) loader.pageRange(start) else start to end

  private fun reloadContent() {
    lifecycleScope.launch {
      val (from, to) = range()
      val loaded = loader.load(from, to, sources, options.tafsirKey.takeIf { options.showTafsir })
      missingText = loaded == null
      content = loaded
    }
  }

  private fun updateOptions(updated: ShareCardOptions) {
    val reload = updated.showTafsir != options.showTafsir || updated.tafsirKey != options.tafsirKey
    options = updated
    saveOptions(updated)
    if (reload) reloadContent()
  }

  @Composable
  private fun ComposerScreen(audioFirst: Boolean) {
    val currentContent = content
    LaunchedEffect(currentContent, options) {
      if (currentContent != null) {
        preview = withContext(Dispatchers.Default) {
          renderer.render(currentContent, options, scale = PREVIEW_SCALE)
        }
      }
    }

    Column(
      Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.surface)
        .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
      Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
        IconButton(onClick = { finish() }) {
          Icon(QuranIcons.ArrowBack, contentDescription = null)
        }
        Text(
          stringResource(R.string.tasnim_share_composer_title),
          style = MaterialTheme.typography.titleMedium
        )
      }

      Column(
        Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 16.dp)
      ) {
        PreviewBox()
        Spacer(Modifier.height(16.dp))

        if (audioFirst) {
          RecitationSection()
          ImageActions()
        } else {
          ImageActions()
        }
        SectionTitle(R.string.tasnim_share_size)
        ChipRow(
          listOf(
            ShareAspect.SQUARE to R.string.tasnim_share_size_square,
            ShareAspect.PORTRAIT to R.string.tasnim_share_size_portrait,
            ShareAspect.STORY to R.string.tasnim_share_size_story
          ),
          selected = options.aspect
        ) { updateOptions(options.copy(aspect = it)) }

        SectionTitle(R.string.tasnim_share_background)
        BackgroundPicker()

        SectionTitle(R.string.tasnim_share_show)
        ToggleRow(R.string.tasnim_share_whole_page, wholePage) {
          wholePage = it
          reloadContent()
        }
        ToggleRow(R.string.tasnim_share_show_sura, options.showSuraName) {
          updateOptions(options.copy(showSuraName = it))
        }
        ToggleRow(R.string.tasnim_share_show_ayah_numbers, options.showAyahNumbers) {
          updateOptions(options.copy(showAyahNumbers = it))
        }
        ToggleRow(R.string.tasnim_share_show_page, options.showPage) {
          updateOptions(options.copy(showPage = it))
        }
        ToggleRow(R.string.tasnim_share_show_juz, options.showJuz) {
          updateOptions(options.copy(showJuz = it))
        }
        ToggleRow(R.string.tasnim_share_show_tafsir, options.showTafsir) {
          updateOptions(options.copy(showTafsir = it))
        }
        if (options.showTafsir) {
          SectionTitle(R.string.tasnim_share_tafsir_source)
          Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            sources.forEach { source ->
              FilterChip(
                selected = source.key == options.tafsirKey,
                onClick = { updateOptions(options.copy(tafsirKey = source.key)) },
                label = {
                  Text(
                    when (source) {
                      TafsirSource.Persian -> stringResource(R.string.tasnim_share_persian_tafsir)
                      is TafsirSource.Translation -> source.name
                    },
                    maxLines = 1
                  )
                }
              )
            }
          }
        }

        if (!audioFirst) RecitationSection()
        Spacer(Modifier.height(24.dp))
      }
    }

    progress?.let { ExportDialog(it) }
    errorMessage?.let { message ->
      AlertDialog(
        onDismissRequest = { errorMessage = null },
        confirmButton = { TextButton(onClick = { errorMessage = null }) { Text(stringResource(android.R.string.ok)) } },
        text = { Text(message) }
      )
    }
  }

  @Composable
  private fun PreviewBox() {
    val bitmap = preview
    Box(
      Modifier
        .fillMaxWidth()
        .heightIn(max = 460.dp),
      contentAlignment = Alignment.Center
    ) {
      when {
        missingText -> Text(
          stringResource(R.string.tasnim_share_no_text),
          modifier = Modifier.padding(32.dp),
          style = MaterialTheme.typography.bodyMedium
        )
        bitmap == null -> CircularProgressIndicator(Modifier.padding(64.dp))
        else -> Image(
          bitmap = bitmap.asImageBitmap(),
          contentDescription = content?.rangeLabel,
          modifier = Modifier
            .aspectRatio(bitmap.width.toFloat() / bitmap.height)
            .clip(RoundedCornerShape(12.dp))
        )
      }
    }
  }

  @Composable
  private fun ImageActions() {
    val ready = content != null
    Button(onClick = { shareImage() }, enabled = ready, modifier = Modifier.fillMaxWidth()) {
      Text(stringResource(R.string.tasnim_share_image))
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      OutlinedButton(onClick = { saveImage() }, enabled = ready, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.tasnim_share_save))
      }
    }
  }

  @Composable
  private fun RecitationSection() {
    SectionTitle(R.string.tasnim_share_recitation)
    Text(
      stringResource(R.string.tasnim_share_recitation_hint),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(8.dp))
    ToggleRow(R.string.tasnim_share_video_motion, options.videoMotion) {
      updateOptions(options.copy(videoMotion = it))
    }
    ToggleRow(R.string.tasnim_share_video_all_ayahs, options.videoAllAyahs) {
      updateOptions(options.copy(videoAllAyahs = it))
    }
    Spacer(Modifier.height(8.dp))
    QariPicker()
    Spacer(Modifier.height(8.dp))
    val ready = content != null && qari != null
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Button(onClick = { exportRecitation(video = true) }, enabled = ready, modifier = Modifier.weight(1f)) {
        Text(stringResource(R.string.tasnim_share_video))
      }
      OutlinedButton(onClick = { exportRecitation(video = false) }, enabled = ready, modifier = Modifier.weight(1f)) {
        Text(stringResource(R.string.tasnim_share_audio))
      }
    }
    Spacer(Modifier.height(8.dp))
  }

  @Composable
  private fun QariPicker() {
    var expanded by remember { mutableStateOf(false) }
    Box {
      OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
        Text(
          stringResource(R.string.tasnim_share_qari) + ": " + (qari?.name ?: "…"),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
      DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        qaris.forEach { item ->
          DropdownMenuItem(
            text = { Text(item.name) },
            onClick = {
              qari = item
              expanded = false
            }
          )
        }
      }
    }
  }

  @Composable
  private fun BackgroundPicker() {
    val thumbnails = remember { (0 until ShareBackgrounds.COUNT).map { renderer.thumbnail(it, 160) } }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      thumbnails.forEachIndexed { index, thumbnail ->
        val selected = index == options.background
        Image(
          bitmap = thumbnail.asImageBitmap(),
          contentDescription = null,
          modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(10.dp))
            .border(
              BorderStroke(if (selected) 3.dp else 1.dp, if (selected) GOLD else Color.Gray.copy(alpha = 0.4f)),
              RoundedCornerShape(10.dp)
            )
            .clickable { updateOptions(options.copy(background = index)) }
        )
      }
    }
  }

  @Composable
  private fun <T> ChipRow(items: List<Pair<T, Int>>, selected: T, onSelect: (T) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      items.forEach { (value, label) ->
        FilterChip(
          selected = value == selected,
          onClick = { onSelect(value) },
          label = { Text(stringResource(label)) }
        )
      }
    }
  }

  @Composable
  private fun ToggleRow(label: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
      Modifier
        .fillMaxWidth()
        .clickable { onChange(!checked) }
        .padding(vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(stringResource(label), modifier = Modifier.weight(1f))
      Switch(checked = checked, onCheckedChange = onChange)
    }
  }

  @Composable
  private fun SectionTitle(label: Int) {
    Text(
      stringResource(label),
      style = MaterialTheme.typography.titleSmall,
      fontWeight = FontWeight.Medium,
      color = MaterialTheme.colorScheme.primary,
      modifier = Modifier.padding(top = 18.dp, bottom = 8.dp)
    )
  }

  @Composable
  private fun ExportDialog(state: ExportProgress) {
    AlertDialog(
      onDismissRequest = {},
      confirmButton = {},
      dismissButton = { TextButton(onClick = { cancelExport() }) { Text(stringResource(R.string.tasnim_share_cancel)) } },
      text = {
        Column {
          when (state) {
            is ExportProgress.Preparing -> {
              Text(
                stringResource(
                  R.string.tasnim_share_preparing,
                  QuranUtils.getLocalizedNumber(state.done),
                  QuranUtils.getLocalizedNumber(state.total)
                )
              )
              Spacer(Modifier.height(12.dp))
              LinearProgressIndicator(
                progress = { if (state.total == 0) 0f else state.done.toFloat() / state.total },
                modifier = Modifier.fillMaxWidth()
              )
            }
            is ExportProgress.Rendering -> {
              Text(stringResource(R.string.tasnim_share_rendering, QuranUtils.getLocalizedNumber(state.percent)))
              Spacer(Modifier.height(12.dp))
              LinearProgressIndicator(progress = { state.percent / 100f }, modifier = Modifier.fillMaxWidth())
            }
          }
        }
      }
    )
  }

  // --- actions

  private fun shareImage() {
    val current = content ?: return
    logShare("image", current)
    lifecycleScope.launch {
      val file = withContext(Dispatchers.IO) {
        val bitmap = renderer.render(current, options)
        val file = File(shareDirectory(), "tasnim_${fileStem(current)}.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        file
      }
      sendFile(file, "image/png")
    }
  }

  private fun saveImage() {
    val current = content ?: return
    logShare("save", current)
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
    lifecycleScope.launch {
      val saved = withContext(Dispatchers.IO) {
        runCatching {
          val bitmap = renderer.render(current, options)
          val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "tasnim_${fileStem(current)}_${System.currentTimeMillis()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Tasnim")
          }
          val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("no uri")
          contentResolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            ?: error("no stream")
          bitmap.recycle()
        }.isSuccess
      }
      Toast.makeText(
        this@ShareComposerActivity,
        if (saved) R.string.tasnim_share_saved else R.string.tasnim_share_save_failed,
        Toast.LENGTH_SHORT
      ).show()
    }
  }

  private fun exportRecitation(video: Boolean) {
    val selectedQari = qari ?: return
    if (exportJob?.isActive == true) return
    exportJob = lifecycleScope.launch {
      window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
      try {
        // at most one page: ayahs past the first page of the selection are left out
        val (from, selectedTo) = range()
        val pageEnd = loader.pageRange(from).second
        val to = if (selectedTo > pageEnd) pageEnd else selectedTo
        if (to != selectedTo) toast(R.string.tasnim_share_page_limited)

        progress = ExportProgress.Preparing(0, 1)
        val allSegments = clipBuilder.build(selectedQari, from, to) { done, total ->
          lifecycleScope.launch(Dispatchers.Main) {
            // a late update must not replace the rendering step that follows
            if (progress is ExportProgress.Preparing) progress = ExportProgress.Preparing(done, total)
          }
        }
        val segments = limitDuration(allSegments)
        if (segments.size < allSegments.size) toast(R.string.tasnim_share_trimmed)
        if (segments.isEmpty()) error("no recitation")
        val lastAyah = segments.last().ayah

        progress = ExportProgress.Rendering(0)
        val exporter = RecitationExporter(this@ShareComposerActivity)
        val directory = shareDirectory()
        val frames = if (video) {
          val cardOptions = options
          val allAyahs = cardOptions.videoAllAyahs
          // with every ayah on screen there is no room for the tafsir, and the text keeps one size
          val tafsirKey = cardOptions.tafsirKey.takeIf { cardOptions.showTafsir && !allAyahs }
          val loaded = loader.load(from, lastAyah, sources, tafsirKey) ?: error("no text")
          val byAyah = loaded.verses.associateBy { it.suraAyah }
          val frameDirectory = File(directory, "frames")
          exporter.clearFrames(frameDirectory)
          // one card per ayah, written and freed one at a time so long pages fit in memory
          val cards = LinkedHashMap<SuraAyah, File>()
          segments.map { it.ayah }.distinct().forEachIndexed { index, ayah ->
            val verse = byAyah[ayah] ?: loaded.verses.first()
            val bitmap = withContext(Dispatchers.Default) {
              if (allAyahs) {
                renderer.render(loaded, cardOptions.copy(showTafsir = false), highlight = ayah)
              } else {
                renderer.render(loader.forVerse(loaded, verse), cardOptions)
              }
            }
            cards[ayah] = exporter.writeFrame(bitmap, frameDirectory, index)
            bitmap.recycle()
          }
          segments.map { cards.getValue(it.ayah) }
        } else {
          null
        }

        val stem = "tasnim_${from.sura}_${from.ayah}-${lastAyah.ayah}"
        val output = File(directory, if (video) "$stem.mp4" else "$stem.m4a")
        val effects = if (frames != null && options.videoMotion) {
          val palette = ShareBackgrounds.palette(options.background)
          ShareVideoEffects.forVideo(cardBoundaries(segments, frames), palette.base, palette.light)
        } else {
          emptyList()
        }
        exporter.export(segments, frames, effects, output) { percent ->
          progress = ExportProgress.Rendering(percent)
        }
        progress = null
        shareAnalytics.logShare(if (video) "video" else "audio", from, lastAyah, options)
        sendFile(output, if (video) "video/mp4" else "audio/mp4")
      } catch (e: CancellationException) {
        throw e
      } catch (e: RecitationException) {
        errorMessage = getString(
          if (e.reason == RecitationException.Reason.NO_TIMINGS) R.string.tasnim_share_error_timings
          else R.string.tasnim_share_error_download
        )
      } catch (e: Exception) {
        Timber.e(e, "ayah share export failed")
        errorMessage = getString(R.string.tasnim_share_error_export)
      } finally {
        progress = null
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
      }
    }
  }

  private fun cancelExport() {
    exportJob?.cancel()
    progress = null
  }

  /** When each card starts and the video ends, in ms; a card shown for two segments counts once. */
  private fun cardBoundaries(segments: List<RecitationSegment>, frames: List<File>): List<Long> {
    val boundaries = mutableListOf(0L)
    var time = 0L
    segments.forEachIndexed { index, segment ->
      if (index > 0 && frames[index] != frames[index - 1]) boundaries += time
      time += segment.durationMs
    }
    boundaries += time
    return boundaries
  }

  /** Keeps whole ayahs up to [MAX_RECITATION_MS]; always at least the first ayah. */
  private fun limitDuration(segments: List<RecitationSegment>): List<RecitationSegment> {
    var total = 0L
    val kept = mutableListOf<RecitationSegment>()
    for (ayah in segments.map { it.ayah }.distinct()) {
      val parts = segments.filter { it.ayah == ayah }
      val length = parts.sumOf { it.durationMs }
      if (kept.isNotEmpty() && total + length > MAX_RECITATION_MS) break
      kept += parts
      total += length
    }
    return kept
  }

  private fun logShare(kind: String, content: ShareCardContent) =
    shareAnalytics.logShare(kind, content.verses.first().suraAyah, content.verses.last().suraAyah, options)

  private fun sendFile(file: File, mimeType: String) {
    val uri: Uri = FileProvider.getUriForFile(this, getString(R.string.file_authority), file)
    val intent = Intent(Intent.ACTION_SEND).apply {
      type = mimeType
      putExtra(Intent.EXTRA_STREAM, uri)
      content?.let { putExtra(Intent.EXTRA_TEXT, it.rangeLabel) }
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    startActivity(Intent.createChooser(intent, getString(R.string.tasnim_share_composer_title)))
  }

  private fun shareDirectory(): File = File(cacheDir, "share").apply { mkdirs() }

  private fun fileStem(content: ShareCardContent): String {
    val first = content.verses.first().suraAyah
    val last = content.verses.last().suraAyah
    return "${first.sura}_${first.ayah}-${last.ayah}"
  }

  private fun toast(message: Int) =
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

  private fun loadOptions(): ShareCardOptions {
    val defaults = ShareCardOptions()
    return ShareCardOptions(
      aspect = runCatching { ShareAspect.valueOf(prefs.getString(KEY_ASPECT, null) ?: "") }
        .getOrDefault(defaults.aspect),
      background = prefs.getInt(KEY_BACKGROUND, defaults.background).mod(ShareBackgrounds.COUNT),
      showSuraName = prefs.getBoolean(KEY_SURA, defaults.showSuraName),
      showAyahNumbers = prefs.getBoolean(KEY_AYAH_NUMBERS, defaults.showAyahNumbers),
      showPage = prefs.getBoolean(KEY_PAGE, defaults.showPage),
      showJuz = prefs.getBoolean(KEY_JUZ, defaults.showJuz),
      showTafsir = prefs.getBoolean(KEY_TAFSIR, defaults.showTafsir),
      tafsirKey = prefs.getString(KEY_TAFSIR_SOURCE, null) ?: defaults.tafsirKey,
      videoMotion = prefs.getBoolean(KEY_VIDEO_MOTION, defaults.videoMotion),
      videoAllAyahs = prefs.getBoolean(KEY_VIDEO_ALL_AYAHS, defaults.videoAllAyahs)
    )
  }

  private fun saveOptions(options: ShareCardOptions) {
    prefs.edit {
      putString(KEY_ASPECT, options.aspect.name)
      putInt(KEY_BACKGROUND, options.background)
      putBoolean(KEY_SURA, options.showSuraName)
      putBoolean(KEY_AYAH_NUMBERS, options.showAyahNumbers)
      putBoolean(KEY_PAGE, options.showPage)
      putBoolean(KEY_JUZ, options.showJuz)
      putBoolean(KEY_TAFSIR, options.showTafsir)
      putString(KEY_TAFSIR_SOURCE, options.tafsirKey)
      putBoolean(KEY_VIDEO_MOTION, options.videoMotion)
      putBoolean(KEY_VIDEO_ALL_AYAHS, options.videoAllAyahs)
    }
  }

  companion object {
    private const val EXTRA_START_SURA = "start_sura"
    private const val EXTRA_START_AYAH = "start_ayah"
    private const val EXTRA_END_SURA = "end_sura"
    private const val EXTRA_END_AYAH = "end_ayah"
    private const val EXTRA_AUDIO = "audio"
    private const val STATE_WHOLE_PAGE = "whole_page"

    private const val PREFS = "tasnim_share"
    private const val KEY_ASPECT = "aspect"
    private const val KEY_BACKGROUND = "background"
    private const val KEY_SURA = "show_sura"
    private const val KEY_AYAH_NUMBERS = "show_ayah_numbers"
    private const val KEY_PAGE = "show_page"
    private const val KEY_JUZ = "show_juz"
    private const val KEY_TAFSIR = "show_tafsir"
    private const val KEY_TAFSIR_SOURCE = "tafsir_source"
    private const val KEY_VIDEO_MOTION = "video_motion"
    private const val KEY_VIDEO_ALL_AYAHS = "video_all_ayahs"

    private const val PREVIEW_SCALE = 0.5f
    private const val MAX_RECITATION_MS = 3 * 60 * 1000L
    private val GOLD = Color(0xFFD4AF37)

    fun intent(context: Context, start: SuraAyah, end: SuraAyah, audio: Boolean): Intent =
      Intent(context, ShareComposerActivity::class.java).apply {
        putExtra(EXTRA_START_SURA, start.sura)
        putExtra(EXTRA_START_AYAH, start.ayah)
        putExtra(EXTRA_END_SURA, end.sura)
        putExtra(EXTRA_END_AYAH, end.ayah)
        putExtra(EXTRA_AUDIO, audio)
      }
  }
}
