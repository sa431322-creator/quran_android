package com.quran.labs.androidquran.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Typeface
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontFamily
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.quran.data.core.QuranInfo
import com.quran.data.dao.HighlightsDao
import com.quran.data.model.SuraAyah
import com.quran.data.model.bookmark.ReadingBookmarkTarget
import com.quran.data.model.selection.AyahSelection
import com.quran.data.model.selection.SelectionIndicator
import com.quran.data.model.selection.SelectionRectangle
import com.quran.data.model.selection.startSuraAyah
import com.quran.labs.androidquran.HelpActivity
import com.quran.labs.androidquran.QuranApplication
import com.quran.labs.androidquran.QuranPreferenceActivity
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.common.audio.model.QariItem.Companion.fromQari
import com.quran.labs.androidquran.common.audio.model.playback.AudioRequest
import com.quran.labs.androidquran.common.audio.repository.AudioStatusRepository
import com.quran.labs.androidquran.common.audio.repository.CurrentQariManager
import com.quran.labs.androidquran.common.ui.core.HighlightColors
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import com.quran.labs.androidquran.data.Constants
import com.quran.labs.androidquran.data.QuranDisplayData
import com.quran.labs.androidquran.di.component.activity.TajweedReaderComponent
import com.quran.labs.androidquran.feature.reading.bridge.AudioStatusRepositoryBridge
import com.quran.labs.androidquran.feature.reading.bridge.DownloadBridge
import com.quran.labs.androidquran.feature.reading.presenter.AudioPresenter
import com.quran.labs.androidquran.feature.reading.presenter.AudioPresenterScreen
import com.quran.labs.androidquran.feature.tajweed.TajweedAyahSharing
import com.quran.labs.androidquran.service.AudioService
import com.quran.labs.androidquran.service.QuranDownloadService
import com.quran.labs.androidquran.service.util.PermissionUtil.buildPostPermissionDialog
import com.quran.labs.androidquran.service.util.PermissionUtil.canRequestPostNotificationPermission
import com.quran.labs.androidquran.service.util.PermissionUtil.havePostNotificationPermission
import com.quran.labs.androidquran.ui.fragment.JumpFragment
import com.quran.labs.androidquran.ui.fragment.SuraJumpSheet
import com.quran.labs.androidquran.ui.helpers.AyahSelectedListener
import com.quran.labs.androidquran.ui.listener.AudioBarListener
import com.quran.labs.androidquran.ui.listener.AudioBarRecitationListener
import com.quran.labs.androidquran.ui.util.TypefaceManager
import com.quran.labs.androidquran.util.AudioUtils
import com.quran.labs.androidquran.util.OrientationLockUtils
import com.quran.labs.androidquran.util.QuranSettings
import com.quran.labs.androidquran.util.QuranUtils
import com.quran.mobile.common.download.DownloadInfoStreams
import com.quran.mobile.feature.audiobar.AudioBarInjector
import com.quran.mobile.feature.audiobar.AudioBarWrapper
import com.quran.mobile.feature.audiobar.presenter.AudioBarEventRepository
import com.quran.mobile.feature.ayahbookmark.AyahBookmarkWrapper
import com.quran.mobile.feature.ayahbookmark.di.AyahBookmarkWrapperInjector
import com.quran.mobile.feature.ayahbookmark.readingbookmark.ReadingBookmarkSheetWrapper
import com.quran.mobile.feature.ayahbookmark.readingbookmark.di.ReadingBookmarkSheetWrapperInjector
import com.quran.mobile.feature.qarilist.QariListWrapper
import com.quran.mobile.feature.qarilist.di.QariListWrapperInjector
import com.quran.mobile.feature.tajweed.data.TajweedPage
import com.quran.mobile.feature.tajweed.data.TajweedRepository
import com.quran.mobile.feature.tajweed.ui.TajweedAyahBounds
import com.quran.mobile.feature.tajweed.ui.TajweedPageLabels
import com.quran.mobile.feature.tajweed.ui.TajweedPageStyle
import com.quran.mobile.feature.tajweed.ui.TajweedPages
import com.quran.mobile.feature.tajweed.ui.TajweedPagesController
import com.quran.mobile.feature.tajweed.ui.tajweedAyahKey
import com.quran.page.common.toolbar.AyahToolBar
import com.quran.page.common.toolbar.di.AyahToolBarInjector
import com.quran.reading.common.ReadingEventPresenter
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import com.quran.labs.androidquran.common.toolbar.R as ToolbarR

/**
 * The tajweed mushaf in the reader's own chrome: the same toolbar and mode toggle, page slider,
 * audio bar, floating ayah toolbar and bookmark and qari sheets as [PagerActivity], around the
 * tajweed pages. The reader's design is shared, not copied, and the reader itself is unchanged.
 *
 * The two mushafs lay their pages out differently (Madani 1405 / V4 1441), so they hand each other
 * an ayah: it opens at the reader's ayah and returns the ayah it was left at, and the reader's
 * Persian and translation panels open in the reader at the chosen ayah.
 */
class TajweedReaderActivity : AppCompatActivity(), AudioPresenterScreen, AudioBarListener,
  AudioBarRecitationListener, AyahToolBarInjector, AudioBarInjector, QariListWrapperInjector,
  AyahBookmarkWrapperInjector, ReadingBookmarkSheetWrapperInjector, SuraJumpSheet.Host {

  @Inject override lateinit var quranInfo: QuranInfo
  @Inject override lateinit var quranDisplayData: QuranDisplayData
  @Inject lateinit var quranSettings: QuranSettings
  @Inject lateinit var audioUtils: AudioUtils
  @Inject lateinit var audioPresenter: AudioPresenter
  @Inject lateinit var qariManager: CurrentQariManager
  @Inject lateinit var readingEventPresenter: ReadingEventPresenter
  @Inject lateinit var audioStatusRepository: AudioStatusRepository
  @Inject lateinit var audioBarEventRepository: AudioBarEventRepository
  @Inject lateinit var downloadInfoStreams: DownloadInfoStreams
  @Inject lateinit var downloadBridge: DownloadBridge
  @Inject lateinit var highlightsDao: HighlightsDao
  @Inject lateinit var sharing: TajweedAyahSharing

  private val component: TajweedReaderComponent by lazy {
    (application as QuranApplication)
      .applicationComponent
      .activityComponentFactory()
      .generate(this)
      .tajweedReaderComponentFactory()
      .generate(object : AyahSelectedListener {})
  }

  private lateinit var repository: TajweedRepository
  private lateinit var toolBarArea: View
  private lateinit var ayahToolBar: AyahToolBar
  private lateinit var audioStatusBar: AudioBarWrapper
  private lateinit var pageSliderArea: View
  private lateinit var pageSlider: SeekBar
  private lateinit var pageSliderBubble: TextView
  private lateinit var overlay: FrameLayout
  private lateinit var readerModeMushaf: TextView
  private lateinit var readerModePersian: TextView
  private lateinit var readerModeTajweed: TextView
  private lateinit var windowInsetsController: WindowInsetsControllerCompat
  private lateinit var audioStatusRepositoryBridge: AudioStatusRepositoryBridge
  private var requestPermissionLauncher: androidx.activity.result.ActivityResultLauncher<String>? = null

  private val controller = TajweedPagesController()
  private val selectedAyah = mutableStateOf<Int?>(null)
  private val recitedAyah = mutableStateOf<Int?>(null)
  private val highlights = mutableStateOf<Map<Int, Color>>(emptyMap())
  private var pages: List<TajweedPage> = emptyList()
  private var currentPage: TajweedPage? = null

  private var isActionBarHidden = true
  private var isDraggingPageSlider = false
  private var needsPermissionToDownloadOver3g = true
  private var promptDialog: AlertDialog? = null

  // where it was opened and left, to send the reader back to the matching page
  private var startPosition: Pair<Int, Int>? = null
  private var position: Pair<Int, Int>? = null
  private var panelOnReturn = Panel.NONE

  override fun onCreate(savedInstanceState: Bundle?) {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    super.onCreate(savedInstanceState)
    component.inject(this)
    setContentView(R.layout.tajweed_reader_activity)
    windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)

    savedInstanceState?.let { state ->
      val sura = state.getInt(STATE_START_SURA, 0)
      val ayah = state.getInt(STATE_START_AYAH, 0)
      if (sura > 0 && ayah > 0) startPosition = sura to ayah
    }

    repository = TajweedRepository(this)
    toolBarArea = findViewById(R.id.toolbar_area)
    ayahToolBar = findViewById(R.id.ayah_toolbar)
    audioStatusBar = findViewById(R.id.audio_area)
    overlay = findViewById(R.id.overlay)

    // the reader's insets: the toolbar sits under the status bar
    ViewCompat.setOnApplyWindowInsetsListener(toolBarArea) { view, windowInsets ->
      val insets = windowInsets.getInsets(
        WindowInsetsCompat.Type.statusBars() or
          WindowInsetsCompat.Type.displayCutout() or
          WindowInsetsCompat.Type.navigationBars()
      )
      view.updatePadding(insets.left, insets.top, insets.right, 0)
      windowInsets
    }

    val toolbar = findViewById<Toolbar>(R.id.toolbar)
    setSupportActionBar(toolbar)
    supportActionBar?.setDisplayShowHomeEnabled(true)
    supportActionBar?.setDisplayHomeAsUpEnabled(true)
    toolbar.setOnClickListener { showSuraJumpSheet() }

    initReaderModeToggle()
    initPageSlider()
    initAyahToolBar()
    initPages()
    registerBackPressed()

    audioStatusRepositoryBridge = AudioStatusRepositoryBridge(
      audioStatusRepository,
      audioBarEventRepository,
      { suraAyah -> onAudioPlaybackAyahChanged(suraAyah) },
      this,
      this
    )
    audioStatusRepository.audioPlaybackFlow
      .onEach { status -> if (status !is com.quran.labs.androidquran.common.audio.model.playback.AudioStatus.Playback) recitedAyah.value = null }
      .launchIn(lifecycleScope)
    requestPermissionLauncher =
      registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        audioPresenter.onPostNotificationsPermissionResponse(isGranted)
      }
    downloadBridge.subscribeToDownloads { audioPresenter.onDownloadSuccess() }

    // the reader's highlight colors, behind the ayat they mark
    highlightsDao.highlightsFlow()
      .map { list ->
        list.associate { highlight ->
          tajweedAyahKey(highlight.suraAyah.sura, highlight.suraAyah.ayah) to
            Color(ContextCompat.getColor(this, HighlightColors[highlight.color].colorResourceId))
        }
      }
      .onEach { highlights.value = it }
      .launchIn(lifecycleScope)

    // the selection drives the highlight, like the reader's pages
    readingEventPresenter.ayahSelectionFlow
      .onEach { selection ->
        selectedAyah.value = selection.startSuraAyah()?.let { tajweedAyahKey(it.sura, it.ayah) }
      }
      .launchIn(lifecycleScope)

    lifecycleScope.launch {
      pages = runCatching { repository.data().pages }.getOrDefault(emptyList())
    }
    // like the reader: open with the bars showing, then hide them once the window has focus
    audioStatusBar.visibility = View.VISIBLE
    toggleActionBarVisibility(true)
  }

  private val hideActionBar = Runnable { toggleActionBarVisibility(false) }

  override fun onWindowFocusChanged(hasFocus: Boolean) {
    super.onWindowFocusChanged(hasFocus)
    toolBarArea.removeCallbacks(hideActionBar)
    if (hasFocus) {
      toolBarArea.postDelayed(hideActionBar, HIDE_BARS_AFTER_MS)
    }
  }

  override fun onStart() {
    super.onStart()
    audioPresenter.bind(this)
  }

  override fun onStop() {
    audioPresenter.unbind(this)
    promptDialog?.dismiss()
    promptDialog = null
    super.onStop()
  }

  override fun onDestroy() {
    audioStatusRepositoryBridge.dispose()
    super.onDestroy()
  }

  override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    startPosition?.let { (sura, ayah) ->
      outState.putInt(STATE_START_SURA, sura)
      outState.putInt(STATE_START_AYAH, ayah)
    }
  }

  //region pages

  private fun initPages() {
    val sura = intent.getIntExtra(EXTRA_SURA, 0)
    val ayah = intent.getIntExtra(EXTRA_AYAH, 0)
    val startAyah = if (sura > 0 && ayah > 0) sura to ayah else null
    val preferences = getSharedPreferences(PREFERENCES, MODE_PRIVATE)
    // the app's own fonts: Hafs for the basmala, the page overlay font for headers and footers
    val basmalaFont = runCatching { FontFamily(TypefaceManager.getUthmaniTypeface(this)) }
      .getOrDefault(FontFamily.Default)
    val headerFont = FontFamily(TypefaceManager.getHeaderFooterTypeface(this))
    val style = TajweedPageStyle(
      paper = Color(ContextCompat.getColor(this, R.color.page_background)),
      headerFont = headerFont,
      headerColor = Color(ContextCompat.getColor(this, R.color.overlay_text_color)),
      suraFrameFill = Color(ContextCompat.getColor(this, R.color.mushaf_sura_header_fill)),
      suraFrameGold = Color(ContextCompat.getColor(this, R.color.mushaf_sura_header_gold)),
      suraFramePill = Color(ContextCompat.getColor(this, R.color.mushaf_sura_header_pill)),
      selection = Color(ContextCompat.getColor(this, R.color.selection_highlight)),
      recitation = Color(ContextCompat.getColor(this, R.color.audio_highlight))
    )

    findViewById<ComposeView>(R.id.tajweed_pages).setContent {
      QuranTheme {
        val selected by selectedAyah
        val recited by recitedAyah
        val highlighted by highlights
        TajweedPages(
          repository = repository,
          basmalaFont = basmalaFont,
          style = style,
          labels = ::labelsFor,
          suraHeaderName = { suraNumber -> quranDisplayData.getSuraName(this, suraNumber, true) },
          initialPage = preferences.getInt(KEY_LAST_PAGE, 1),
          controller = controller,
          selectedAyah = selected,
          recitedAyah = recited,
          highlights = highlighted,
          onPageShown = { page ->
            currentPage = page
            preferences.edit().putInt(KEY_LAST_PAGE, page.page).apply()
            if (startPosition == null) startPosition = page.firstAyah
            position = page.firstAyah
            updateActionBarTitle(page)
            updatePageSlider(page.page)
          },
          onTap = ::onPageClicked,
          onAyahLongPress = ::onAyahLongPress,
          startAyah = startAyah,
          modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
        )
      }
    }
  }

  private fun labelsFor(page: TajweedPage): TajweedPageLabels =
    TajweedPageLabels(
      suraName = quranDisplayData.getSuraName(this, page.sura, true),
      juz = String.format(getString(R.string.juz2_description), QuranUtils.getLocalizedNumber(page.juz)),
      pageNumber = QuranUtils.getLocalizedNumber(page.page)
    )

  private fun subtitleFor(page: TajweedPage): String =
    String.format(
      getString(R.string.page_description),
      QuranUtils.getLocalizedNumber(page.page),
      QuranUtils.getLocalizedNumber(page.juz)
    )

  private fun updateActionBarTitle(page: TajweedPage) {
    val sura = quranDisplayData.getSuraName(this, page.sura, true)
    supportActionBar?.let { actionBar ->
      actionBar.setDisplayShowTitleEnabled(true)
      // the arrow hints that the title opens the sura list, as in the reader
      actionBar.title = if (sura.isEmpty()) sura else "$sura ▾"
      actionBar.subtitle = subtitleFor(page)
    }
  }

  private fun onPageClicked() {
    if (selectedAyah.value != null) {
      endAyahMode()
    } else {
      toggleActionBar()
    }
  }

  private fun onAyahLongPress(sura: Int, ayah: Int, bounds: TajweedAyahBounds) {
    val indicator = SelectionIndicator.SelectedItemPosition(bounds.first.toSelection(), bounds.last.toSelection())
    readingEventPresenter.onAyahSelection(AyahSelection.Ayah(SuraAyah(sura, ayah), indicator))
    toggleActionBarVisibility(false)
  }

  private fun Rect.toSelection() = SelectionRectangle(left, top, right, bottom)

  private fun endAyahMode() {
    readingEventPresenter.onAyahSelection(AyahSelection.None)
  }

  private fun onAudioPlaybackAyahChanged(suraAyah: SuraAyah?) {
    recitedAyah.value = suraAyah?.let { tajweedAyahKey(it.sura, it.ayah) }
    suraAyah?.let { controller.follow(it.sura, it.ayah) }
  }

  //endregion

  //region chrome (as in PagerActivity)

  private fun initReaderModeToggle() {
    readerModeMushaf = findViewById(R.id.reader_mode_mushaf)
    readerModePersian = findViewById(R.id.reader_mode_persian)
    readerModeTajweed = findViewById(R.id.reader_mode_tajweed)
    readerModeMushaf.setOnClickListener { finish() }
    readerModePersian.setOnClickListener { returnToReader(Panel.PERSIAN_TAFSIR) }
    val selectedText = ContextCompat.getColor(this, R.color.tasnim_on_primary)
    val normalText = ContextCompat.getColor(this, R.color.title_color)
    listOf(readerModeMushaf to false, readerModePersian to false, readerModeTajweed to true)
      .forEach { (view, selected) ->
        view.isSelected = selected
        view.setBackgroundResource(if (selected) R.drawable.tasnim_segment_selected else 0)
        view.setTextColor(if (selected) selectedText else normalText)
      }
  }

  /** Back to the reader, opening one of its panels at the selected ayah or this page's first. */
  private fun returnToReader(panel: Panel) {
    val selection = readingEventPresenter.currentAyahSelection().startSuraAyah()
    selection?.let { position = it.sura to it.ayah }
    panelOnReturn = panel
    finish()
  }

  fun toggleActionBarVisibility(visible: Boolean) {
    if (visible == isActionBarHidden) {
      toggleActionBar()
    }
  }

  private fun toggleActionBar() {
    if (!isActionBarHidden) toolBarArea.removeCallbacks(hideActionBar)
    setUiVisibility(isActionBarHidden)
  }

  private fun setUiVisibility(isVisible: Boolean) {
    if (isVisible) {
      windowInsetsController.show(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
    } else {
      windowInsetsController.hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
      windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
    toolBarArea.post { animateToolBar(isVisible) }
  }

  private fun animateToolBar(visible: Boolean) {
    isActionBarHidden = !visible
    toolBarArea.animate()
      .translationY((if (visible) 0 else -toolBarArea.height).toFloat())
      .setDuration(250)
      .start()
    audioStatusBar.animate()
      .translationY((if (visible) 0 else audioStatusBar.height).toFloat())
      .setDuration(250)
      .start()
    pageSliderArea.animate()
      .translationY(pageSliderTranslation(visible))
      .setDuration(250)
      .start()
  }

  private fun pageSliderTranslation(visible: Boolean): Float {
    return if (visible) {
      -audioStatusBar.height.toFloat()
    } else {
      val params = pageSliderArea.layoutParams as ViewGroup.MarginLayoutParams
      (params.height + params.bottomMargin).toFloat()
    }
  }

  private fun initPageSlider() {
    pageSliderArea = findViewById(R.id.page_slider_area)
    pageSlider = findViewById(R.id.page_slider)
    pageSliderBubble = findViewById(R.id.page_slider_bubble)
    findViewById<TextView>(R.id.page_slider_first).text = QuranUtils.getLocalizedNumber(1)
    findViewById<TextView>(R.id.page_slider_last).text = QuranUtils.getLocalizedNumber(PAGE_COUNT)
    pageSlider.max = PAGE_COUNT - 1

    audioStatusBar.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
      if (bottom - top != oldBottom - oldTop) {
        pageSliderArea.translationY = pageSliderTranslation(!isActionBarHidden)
        pageSliderBubble.translationY = -audioStatusBar.height.toFloat()
      }
    }

    pageSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
      override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
        if (fromUser) {
          pages.getOrNull(progress)?.let { page ->
            pageSliderBubble.text = quranDisplayData.getSuraName(this@TajweedReaderActivity, page.sura, true) +
              " · " + subtitleFor(page)
          }
        }
      }

      override fun onStartTrackingTouch(seekBar: SeekBar) {
        isDraggingPageSlider = true
        toolBarArea.removeCallbacks(hideActionBar)
        pageSliderBubble.translationY = -audioStatusBar.height.toFloat()
        onProgressChanged(seekBar, seekBar.progress, true)
        pageSliderBubble.visibility = View.VISIBLE
      }

      override fun onStopTrackingTouch(seekBar: SeekBar) {
        isDraggingPageSlider = false
        pageSliderBubble.visibility = View.GONE
        controller.showPage(seekBar.progress + 1)
      }
    })
  }

  private fun updatePageSlider(page: Int) {
    if (!isDraggingPageSlider) {
      pageSlider.progress = page - 1
    }
  }

  private fun showSuraJumpSheet() {
    if (supportFragmentManager.findFragmentByTag(SuraJumpSheet.TAG) == null) {
      SuraJumpSheet.newInstance(readerPage()).show(supportFragmentManager, SuraJumpSheet.TAG)
    }
  }

  /** The reader's page holding this page's first ayah, for the reader's own sheets. */
  private fun readerPage(): Int =
    currentPage?.firstAyah?.let { (sura, ayah) -> quranInfo.getPageFromSuraAyah(sura, ayah) } ?: 1

  // the reader's sura/juz list and jump dialog give a reader page; show its first ayah here
  override fun jumpTo(page: Int) {
    val bounds = quranInfo.getPageBounds(page)
    controller.showAyah(bounds[0], bounds[1])
  }

  override fun jumpToAndHighlight(page: Int, sura: Int, ayah: Int) {
    controller.showAyah(sura, ayah)
  }

  override fun onCreateOptionsMenu(menu: Menu): Boolean {
    super.onCreateOptionsMenu(menu)
    menuInflater.inflate(R.menu.tajweed_reader_menu, menu)
    return true
  }

  override fun onPrepareOptionsMenu(menu: Menu): Boolean {
    super.onPrepareOptionsMenu(menu)
    menu.findItem(R.id.rotate_screen)?.isVisible =
      OrientationLockUtils.isOrientationLockSupported(resources.configuration)
    return true
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    when (item.itemId) {
      R.id.favorite_item -> {
        // an ayah target, since tajweed pages are not the reader's pages
        val (sura, ayah) = currentPage?.firstAyah ?: return true
        showReadingBookmarkSheet(ReadingBookmarkTarget.Ayah(SuraAyah(sura, ayah)))
      }
      R.id.goto_translation -> returnToReader(Panel.TRANSLATION_MODE)
      R.id.rotate_screen -> {
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        if (quranSettings.isLockOrientation) {
          PreferenceManager.getDefaultSharedPreferences(this).edit()
            .putBoolean(Constants.PREF_LANDSCAPE_ORIENTATION, !isLandscape)
            .apply()
        }
        requestedOrientation = if (isLandscape) {
          ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
          ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
      }
      R.id.settings -> startActivity(Intent(this, QuranPreferenceActivity::class.java))
      R.id.help -> startActivity(Intent(this, HelpActivity::class.java))
      R.id.jump -> JumpFragment().show(supportFragmentManager, JumpFragment.TAG)
      android.R.id.home -> finish()
      else -> return super.onOptionsItemSelected(item)
    }
    return true
  }

  private fun registerBackPressed() {
    onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
      override fun handleOnBackPressed() {
        when {
          selectedAyah.value != null -> endAyahMode()
          else -> {
            isEnabled = false
            onBackPressedDispatcher.onBackPressed()
          }
        }
      }
    })
  }

  //endregion

  //region ayah toolbar and sheets (as in PagerActivity)

  private fun initAyahToolBar() {
    ayahToolBar.flavor = com.quran.labs.androidquran.BuildConfig.FLAVOR
    ayahToolBar.longPressLambda = { text: CharSequence -> Toast.makeText(this, text, Toast.LENGTH_SHORT).show() }
    ViewCompat.setOnApplyWindowInsetsListener(ayahToolBar) { _, windowInsets ->
      ayahToolBar.insets = windowInsets.getInsets(
        WindowInsetsCompat.Type.statusBars() or
          WindowInsetsCompat.Type.displayCutout() or
          WindowInsetsCompat.Type.navigationBars()
      )
      windowInsets
    }
    ayahToolBar.setOnItemSelectedListener { item ->
      val start = readingEventPresenter.currentAyahSelection().startSuraAyah() ?: return@setOnItemSelectedListener false
      when (item.itemId) {
        ToolbarR.id.cab_bookmark_ayah -> showAyahBookmarkSheet(start)
        ToolbarR.id.cab_translate_ayah -> returnToReader(Panel.TRANSLATION)
        ToolbarR.id.cab_persian_tafsir -> returnToReader(Panel.PERSIAN_TAFSIR)
        ToolbarR.id.cab_play_from_here -> {
          playFromAyah(start)
          toggleActionBarVisibility(true)
        }
        ToolbarR.id.cab_share_ayah_link -> sharing.shareLink(this, start.sura, start.ayah)
        ToolbarR.id.cab_share_ayah_text ->
          if (!sharing.shareText(this, start.sura, start.ayah)) showArabicTextMissing()
        ToolbarR.id.cab_copy_ayah ->
          if (!sharing.copy(this, start.sura, start.ayah)) showArabicTextMissing()
        else -> return@setOnItemSelectedListener false
      }
      endAyahMode()
      true
    }
  }

  // the reader offers to download its Arabic text from its own pages; point there
  private fun showArabicTextMissing() {
    Toast.makeText(this, R.string.download_extra_data, Toast.LENGTH_LONG).show()
  }

  private fun showAyahBookmarkSheet(suraAyah: SuraAyah) {
    showInOverlay(AyahBookmarkWrapper(this, suraAyah) { hideOverlay() })
  }

  private fun showReadingBookmarkSheet(target: ReadingBookmarkTarget) {
    showInOverlay(ReadingBookmarkSheetWrapper(context = this, target = target, onDismissed = { hideOverlay() }))
  }

  private fun showInOverlay(view: View) {
    overlay.removeAllViews()
    overlay.addView(view, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    overlay.visibility = View.VISIBLE
    toggleActionBarVisibility(false)
  }

  private fun hideOverlay() {
    overlay.removeAllViews()
    overlay.visibility = View.GONE
    toggleActionBarVisibility(true)
  }

  override fun injectToolBar(ayahToolBar: AyahToolBar) = component.inject(ayahToolBar)
  override fun inject(audioBarWrapper: AudioBarWrapper) = component.inject(audioBarWrapper)
  override fun injectQariListWrapper(qariListWrapper: QariListWrapper) = component.inject(qariListWrapper)
  override fun injectAyahBookmarkWrapper(ayahBookmarkWrapper: AyahBookmarkWrapper) =
    component.inject(ayahBookmarkWrapper)
  override fun injectReadingBookmarkSheetWrapper(readingBookmarkSheetWrapper: ReadingBookmarkSheetWrapper) =
    component.inject(readingBookmarkSheetWrapper)

  //endregion

  //region audio (as in PagerActivity)

  private fun currentQariItem() = fromQari(this, qariManager.currentQari())

  private fun pageStart(): SuraAyah? =
    currentPage?.firstAyah?.let { (sura, ayah) -> SuraAyah(sura, ayah) }

  private fun playFromAyah(start: SuraAyah) {
    val readerPage = quranInfo.getPageFromSuraAyah(start.sura, start.ayah)
    val end = audioUtils.getLastAyahToPlay(start, readerPage, quranSettings.preferredDownloadAmount, false)
      ?: return
    audioPresenter.play(start, end, currentQariItem(), 0, 0, false, 1.0f, quranSettings.shouldStream())
  }

  override fun onPlayPressed() {
    pageStart()?.let { playFromAyah(it) }
  }

  override fun onContinuePlaybackPressed() = handlePlayback(null)

  override fun onPausePressed() {
    startService(audioUtils.getAudioIntent(this, AudioService.ACTION_PAUSE))
  }

  override fun onNextPressed() {
    startService(audioUtils.getAudioIntent(this, AudioService.ACTION_SKIP))
  }

  override fun onPreviousPressed() {
    startService(audioUtils.getAudioIntent(this, AudioService.ACTION_REWIND))
  }

  override fun onStopPressed() {
    startService(audioUtils.getAudioIntent(this, AudioService.ACTION_STOP))
  }

  override fun setPlaybackSpeed(speed: Float) = updateAudioRequest { it.copy(playbackSpeed = speed) }

  override fun setRepeatCount(repeatCount: Int) = updateAudioRequest { it.copy(repeatInfo = repeatCount) }

  private fun updateAudioRequest(update: (AudioRequest) -> AudioRequest) {
    val lastAudioRequest = audioStatusRepositoryBridge.audioRequest() ?: return
    val intent = Intent(this, AudioService::class.java)
      .setAction(AudioService.ACTION_UPDATE_SETTINGS)
      .putExtra(AudioService.EXTRA_PLAY_INFO, update(lastAudioRequest))
    startService(intent)
  }

  override fun onCancelPressed(stopDownload: Boolean) {
    if (stopDownload) {
      needsPermissionToDownloadOver3g = true
      startService(Intent(this, QuranDownloadService::class.java).setAction(QuranDownloadService.ACTION_CANCEL_DOWNLOADS))
    } else {
      startService(audioUtils.getAudioIntent(this, AudioService.ACTION_STOP))
    }
  }

  override fun onAcceptPressed() {
    needsPermissionToDownloadOver3g = false
    audioPresenter.onDownloadPermissionGranted()
  }

  // the reader shows its playback settings in its panel; the audio bar's own repeat and speed
  // controls cover them here
  override fun onAudioSettingsPressed() = Unit

  override fun onShowQariList() {
    val start = readingEventPresenter.currentAyahSelection().startSuraAyah() ?: pageStart() ?: return
    val end = audioUtils.getLastAyahToPlay(
      start, quranInfo.getPageFromSuraAyah(start.sura, start.ayah), quranSettings.preferredDownloadAmount, false
    ) ?: return
    showInOverlay(QariListWrapper(this, start, end))
  }

  override fun handlePlayback(request: AudioRequest?) {
    needsPermissionToDownloadOver3g = true
    val intent = Intent(this, AudioService::class.java).setAction(AudioService.ACTION_PLAYBACK)
    request?.let { intent.putExtra(AudioService.EXTRA_PLAY_INFO, it) }
    startService(intent)
  }

  override fun handleRequiredDownload(downloadIntent: Intent?) {
    val needsPermission = needsPermissionToDownloadOver3g && !QuranUtils.isOnWifiNetwork(this)
    if (needsPermission) {
      downloadInfoStreams.requestDownloadNetworkPermission()
    } else if (!havePostNotificationPermission(this)) {
      if (canRequestPostNotificationPermission(this)) {
        promptDialog = buildPostPermissionDialog(
          this,
          {
            promptDialog = null
            requestPermissionLauncher?.launch(Manifest.permission.POST_NOTIFICATIONS)
          }, {
            proceedWithDownload(downloadIntent)
            promptDialog = null
          })
        promptDialog?.show()
      } else {
        requestPermissionLauncher?.launch(Manifest.permission.POST_NOTIFICATIONS)
      }
    } else {
      proceedWithDownload(downloadIntent)
    }
  }

  override fun proceedWithDownload(downloadIntent: Intent?) {
    toggleActionBarVisibility(true)
    downloadInfoStreams.downloadRequested()
    downloadIntent?.let { startService(it) }
  }

  // ayah recitation (the "recite" mode) is not offered in the tajweed mushaf
  override fun onRecitationPressed() = Unit
  override fun onRecitationLongPressed() = Unit
  override fun onRecitationTranscriptPressed() = Unit
  override fun onHideVersesPressed() = Unit
  override fun onEndRecitationSessionPressed() = Unit
  override fun onPlayRecitationPressed() = Unit
  override fun onPauseRecitationPressed() = Unit

  //endregion

  override fun finish() {
    val returned = position
    if (returned != null && (returned != startPosition || panelOnReturn != Panel.NONE)) {
      setResult(
        RESULT_OK,
        Intent()
          .putExtra(EXTRA_SURA, returned.first)
          .putExtra(EXTRA_AYAH, returned.second)
          .putExtra(EXTRA_PANEL, panelOnReturn.name)
      )
    }
    super.finish()
  }

  /** What the reader opens when it comes back from tajweed. */
  enum class Panel { NONE, PERSIAN_TAFSIR, TRANSLATION, TRANSLATION_MODE }

  /** The ayah the reader should show on return, and the panel to open there. */
  data class Result(val sura: Int, val ayah: Int, val panel: Panel)

  /** Opens tajweed at a sura and ayah; returns where it was left, or null if not moved. */
  class Contract : ActivityResultContract<Pair<Int, Int>, Result?>() {
    override fun createIntent(context: Context, input: Pair<Int, Int>): Intent =
      Intent(context, TajweedReaderActivity::class.java)
        .putExtra(EXTRA_SURA, input.first)
        .putExtra(EXTRA_AYAH, input.second)

    override fun parseResult(resultCode: Int, intent: Intent?): Result? {
      if (resultCode != RESULT_OK || intent == null) return null
      val sura = intent.getIntExtra(EXTRA_SURA, 0)
      val ayah = intent.getIntExtra(EXTRA_AYAH, 0)
      val panel = runCatching { Panel.valueOf(intent.getStringExtra(EXTRA_PANEL).orEmpty()) }
        .getOrDefault(Panel.NONE)
      return if (sura > 0 && ayah > 0) Result(sura, ayah, panel) else null
    }
  }

  companion object {
    private const val EXTRA_SURA = "sura"
    private const val EXTRA_AYAH = "ayah"
    private const val EXTRA_PANEL = "panel"
    private const val STATE_START_SURA = "start_sura"
    private const val STATE_START_AYAH = "start_ayah"
    // kept from the earlier tajweed screen, so its last page carries over
    private const val PREFERENCES = "tajweed_mushaf"
    private const val KEY_LAST_PAGE = "last_page"
    private const val PAGE_COUNT = 604
    private const val HIDE_BARS_AFTER_MS = 2000L
  }
}
