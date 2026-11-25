package com.quran.labs.androidquran.ui.mushaf

import android.annotation.SuppressLint
import android.content.Context
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.focus.onFocusChanged
import com.quran.labs.androidquran.common.ui.core.isTv
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.CompositionLocalProvider
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.common.ui.core.QuranIcons
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import com.quran.labs.androidquran.common.ui.core.Vazirmatn
import com.quran.mobile.feature.pagetheme.PageTheme

/** What the mushaf menu shows: the reader's current state. */
data class MushafMenuState(
  /** text pages can be read one under the other (vertical) or turned (horizontal) */
  val canScrollPages: Boolean,
  val isScrollReading: Boolean,
  val nightMode: Boolean,
  val pageTheme: PageTheme,
  /** «اندازهٔ متن آیه» and «اندازه قلم ترجمه» (sp) */
  val ayahTextSize: Int = 15,
  val translationTextSize: Int = 15
)

/** What the mushaf menu asks of the reader. */
interface MushafMenuActions {
  fun onScrollReading(scroll: Boolean)
  fun onReadingMode()
  fun onTranslations()
  fun onAutoScroll()
  fun onIndex()
  fun onSearch()
  fun onAudio()
  fun onNightMode(night: Boolean)
  fun onPageTheme(theme: PageTheme)
  fun onMorePageThemes()
  fun onAyahTextSize(size: Int)
  fun onTranslationTextSize(size: Int)
  fun onBookmarkPage()
  fun onSettings()
}

/**
 * The mushaf's settings menu (the page frame's menu button): a panel sliding in from the right
 * over the page, with every setting about the mushaf in one place.
 */
@SuppressLint("ViewConstructor")
class MushafMenuView(
  context: Context,
  private val state: MushafMenuState,
  private val actions: MushafMenuActions,
  private val onDismissed: () -> Unit
) : FrameLayout(context) {

  init {
    addView(ComposeView(context).apply {
      setContent { QuranTheme { MushafMenu(state, actions, onDismissed) } }
    })
  }
}

private val Panel = Color(0xFFFCF6E8)
private val PanelNight = Color(0xFF162024)

@Composable
private fun MushafMenu(state: MushafMenuState, actions: MushafMenuActions, onDismissed: () -> Unit) {
  val visible = remember { MutableTransitionState(false).apply { targetState = true } }
  var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
  // close first, then do what was chosen, so the menu never covers a sheet it opened
  fun close(then: (() -> Unit)? = null) {
    pending = then
    visible.targetState = false
  }
  LaunchedEffect(visible.isIdle, visible.currentState) {
    if (visible.isIdle && !visible.currentState) {
      onDismissed()
      pending?.invoke()
    }
  }
  BackHandler { close() }

  val night = state.nightMode
  val teal = colorResource(R.color.page_frame_band)
  val gold = colorResource(R.color.page_frame_gold)
  val ink = if (night) Color(0xFFE8E1D0) else Color(0xFF1C1A16)
  val muted = if (night) Color(0xFFA9A08C) else Color(0xFF5A5347)
  val accent = if (night) gold else teal
  val panel = if (night) PanelNight else Panel
  val section = if (night) Color(0xFF1F2B30) else Color(0xFFF1E7D2)
  val colors = MenuColors(ink, muted, accent, gold, section)

  Box(Modifier.fillMaxSize()) {
    AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut()) {
      Box(
        Modifier
          .fillMaxSize()
          .background(Color(0x66000000))
          // a tap outside closes the menu; the remote never lands on it
          .focusProperties { canFocus = false }
          .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { close() }
      )
    }
    // the panel comes in from the right, whatever the layout direction
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
      AnimatedVisibility(
        visible,
        modifier = Modifier.align(AbsoluteAlignment.CenterRight),
        enter = slideInHorizontally { it },
        exit = slideOutHorizontally { it }
      ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
          Column(
            Modifier
              .fillMaxHeight()
              .widthIn(max = 340.dp)
              .width(320.dp)
              .background(panel)
              .systemBarsPadding()
          ) {
            MenuHeader(colors) { close() }
            // TV: the remote starts on the first setting (docs/tv/PLAN.md, phase 4)
            val firstSetting = remember { FocusRequester() }
            val tv = isTv()
            LaunchedEffect(tv) {
              if (!tv) return@LaunchedEffect
              withFrameNanos { }
              runCatching { firstSetting.requestFocus() }
            }
            Column(
              Modifier
                .focusRequester(firstSetting)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.dp)
            ) {
              if (state.canScrollPages) {
                SectionTitle(stringResource(R.string.tasnim_menu_display_mode), colors)
                DisplayModeToggle(state.isScrollReading, colors) { scroll ->
                  close { actions.onScrollReading(scroll) }
                }
              }
              SectionTitle(stringResource(R.string.tasnim_menu_section_reading), colors)
              MenuRow(painterResource(R.drawable.ic_tasnim_mushaf), stringResource(R.string.tasnim_menu_reading_mode), colors) {
                close { actions.onReadingMode() }
              }
              MenuRow(painterResource(R.drawable.ic_tasnim_download), stringResource(R.string.tasnim_menu_translations), colors) {
                close { actions.onTranslations() }
              }
              if (state.canScrollPages) {
                MenuRow(painterResource(R.drawable.ic_auto_scroll), stringResource(R.string.tasnim_auto_scroll), colors) {
                  close { actions.onAutoScroll() }
                }
              }
              MenuRow(rememberVectorPainter(QuranIcons.Notes), stringResource(R.string.tasnim_menu_index), colors) {
                close { actions.onIndex() }
              }
              MenuRow(rememberVectorPainter(QuranIcons.Search), stringResource(R.string.menu_search), colors) {
                close { actions.onSearch() }
              }
              MenuRow(painterResource(R.drawable.ic_tasnim_listen), stringResource(R.string.tasnim_menu_audio), colors) {
                close { actions.onAudio() }
              }

              SectionTitle(stringResource(R.string.tasnim_menu_section_look), colors)
              NightModeRow(state.nightMode, colors) { night -> close { actions.onNightMode(night) } }
              PageColors(state.pageTheme, state.nightMode, colors,
                onTheme = { theme -> close { actions.onPageTheme(theme) } },
                onMore = { close { actions.onMorePageThemes() } }
              )

              SectionTitle(stringResource(R.string.tasnim_menu_section_text_size), colors)
              TextSizeRow(
                stringResource(R.string.prefs_ayah_text_title),
                state.ayahTextSize,
                preview = stringResource(R.string.tasnim_menu_ayah_preview),
                arabic = true,
                colors = colors,
                onChange = actions::onAyahTextSize
              )
              TextSizeRow(
                stringResource(R.string.prefs_translation_text_title),
                state.translationTextSize,
                preview = stringResource(R.string.tasnim_menu_translation_preview),
                arabic = false,
                colors = colors,
                onChange = actions::onTranslationTextSize
              )

              SectionTitle(stringResource(R.string.tasnim_menu_section_more), colors)
              MenuRow(painterResource(R.drawable.ic_tasnim_bookmark), stringResource(R.string.tasnim_menu_bookmark_page), colors) {
                close { actions.onBookmarkPage() }
              }
              MenuRow(rememberVectorPainter(QuranIcons.Settings), stringResource(R.string.menu_settings), colors, divider = false) {
                close { actions.onSettings() }
              }
            }
          }
        }
      }
    }
  }
}

private class MenuColors(
  val ink: Color,
  val muted: Color,
  val accent: Color,
  val gold: Color,
  val section: Color
)

private val MenuText = TextStyle(fontFamily = Vazirmatn, fontSize = 15.sp, fontWeight = FontWeight.W500)

@Composable
private fun MenuHeader(colors: MenuColors, onClose: () -> Unit) {
  Row(
    Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(
      stringResource(R.string.tasnim_menu_title),
      style = MenuText.copy(fontSize = 18.sp, fontWeight = FontWeight.W700, color = colors.ink),
      modifier = Modifier.weight(1f)
    )
    Box(
      Modifier
        .size(40.dp)
        .menuClickable(CircleShape, colors, onClose),
      contentAlignment = Alignment.Center
    ) {
      Icon(QuranIcons.Close, contentDescription = stringResource(R.string.tasnim_menu_close), tint = colors.accent)
    }
  }
}

@Composable
private fun SectionTitle(title: String, colors: MenuColors) {
  Text(
    title,
    style = MenuText.copy(fontSize = 13.sp, color = colors.muted, fontWeight = FontWeight.W600),
    modifier = Modifier
      .fillMaxWidth()
      .background(colors.section)
      .padding(horizontal = 20.dp, vertical = 6.dp)
  )
}

@Composable
private fun MenuRow(
  icon: Painter,
  title: String,
  colors: MenuColors,
  divider: Boolean = true,
  trailing: (@Composable () -> Unit)? = null,
  onClick: () -> Unit
) {
  Column {
    Row(
      Modifier
        .fillMaxWidth()
        .menuClickable(RectangleShape, colors, onClick)
        .padding(horizontal = 20.dp, vertical = 14.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Icon(icon, contentDescription = null, tint = colors.accent, modifier = Modifier.size(26.dp))
      Spacer(Modifier.width(16.dp))
      Text(title, style = MenuText.copy(color = colors.ink), modifier = Modifier.weight(1f))
      trailing?.invoke()
    }
    if (divider) {
      HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = colors.gold.copy(alpha = 0.35f))
    }
  }
}

/** Vertical (one page under the other) or horizontal (turned pages), as two halves of a pill. */
@Composable
private fun DisplayModeToggle(isScroll: Boolean, colors: MenuColors, onChange: (Boolean) -> Unit) {
  Row(
    Modifier
      .fillMaxWidth()
      .padding(horizontal = 20.dp, vertical = 12.dp)
      .height(48.dp)
      .clip(RoundedCornerShape(24.dp))
      .border(1.5.dp, colors.accent, RoundedCornerShape(24.dp))
  ) {
    ModeHalf(stringResource(R.string.tasnim_menu_mode_vertical), isScroll, colors, Modifier.weight(1f)) {
      if (!isScroll) onChange(true)
    }
    ModeHalf(stringResource(R.string.tasnim_menu_mode_horizontal), !isScroll, colors, Modifier.weight(1f)) {
      if (isScroll) onChange(false)
    }
  }
}

@Composable
private fun ModeHalf(title: String, selected: Boolean, colors: MenuColors, modifier: Modifier, onClick: () -> Unit) {
  Box(
    modifier
      .fillMaxHeight()
      .clip(RoundedCornerShape(24.dp))
      .background(if (selected) colors.accent else Color.Transparent)
      .menuClickable(RoundedCornerShape(24.dp), colors, onClick),
    contentAlignment = Alignment.Center
  ) {
    Text(
      title,
      style = MenuText.copy(fontSize = 14.sp, color = if (selected) Color.White else colors.ink)
    )
  }
}

@Composable
private fun NightModeRow(night: Boolean, colors: MenuColors, onChange: (Boolean) -> Unit) {
  val tv = isTv()
  MenuRow(
    painterResource(R.drawable.ic_tasnim_moon),
    stringResource(R.string.tasnim_menu_night_mode),
    colors,
    trailing = {
      Switch(
        checked = night,
        onCheckedChange = onChange,
        // the row itself toggles it, so the remote doesn't stop twice on one setting
        modifier = Modifier.focusProperties { canFocus = !tv },
        colors = SwitchDefaults.colors(checkedTrackColor = colors.accent)
      )
    }
  ) { onChange(!night) }
}

/** The page's paper: one dot per page theme, and the full page theme sheet. */
@Composable
private fun PageColors(
  selected: PageTheme,
  night: Boolean,
  colors: MenuColors,
  onTheme: (PageTheme) -> Unit,
  onMore: () -> Unit
) {
  val context = androidx.compose.ui.platform.LocalContext.current
  Column {
    MenuRow(
      painterResource(R.drawable.ic_page_theme),
      stringResource(R.string.tasnim_menu_page_colors),
      colors,
      divider = false,
      onClick = onMore
    )
    Row(
      Modifier.fillMaxWidth().padding(start = 62.dp, end = 20.dp, bottom = 14.dp),
      horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      PageTheme.entries.forEach { theme ->
        val isSelected = !night && theme == selected
        Box(
          Modifier
            .size(38.dp)
            .menuClickable(CircleShape, colors) { onTheme(theme) }
            .clip(CircleShape)
            .border(if (isSelected) 3.dp else 1.dp, if (isSelected) colors.accent else colors.gold, CircleShape)
            .padding(if (isSelected) 5.dp else 2.dp)
            .clip(CircleShape)
            .background(Color(theme.backgroundColor(context)))
        )
      }
    }
    HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = colors.gold.copy(alpha = 0.35f))
  }
}

/**
 * A text size: its name and size, a slider (saved when let go, so the pages behind follow) and a
 * preview line at that size.
 */
@Composable
private fun TextSizeRow(
  title: String,
  size: Int,
  preview: String,
  arabic: Boolean,
  colors: MenuColors,
  onChange: (Int) -> Unit
) {
  var value by remember { mutableStateOf(size.toFloat()) }
  var sliderFocused by remember { mutableStateOf(false) }
  val tv = isTv()
  Column(
    Modifier
      .fillMaxWidth()
      // TV: the remote's left/right move the slider; a gold frame shows it has the focus
      .then(if (sliderFocused && tv) Modifier.border(3.dp, colors.gold) else Modifier)
      .padding(horizontal = 20.dp, vertical = 10.dp)
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(title, style = MenuText.copy(color = colors.ink), modifier = Modifier.weight(1f))
      Text(
        com.quran.labs.androidquran.util.QuranUtils.getLocalizedNumber(value.toInt()),
        style = MenuText.copy(color = colors.muted, fontSize = 14.sp)
      )
    }
    Slider(
      value = value,
      onValueChange = { value = it },
      onValueChangeFinished = { onChange(value.toInt()) },
      modifier = Modifier
        .onFocusChanged { sliderFocused = it.isFocused }
        // TV: left/right step the size, saved at once (the track runs right to left, so left
        // is bigger); up/down move on through the menu
        .onPreviewKeyEvent { event ->
          if (!tv || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
          val step = when (event.key) {
            Key.DirectionLeft -> 1
            Key.DirectionRight -> -1
            else -> return@onPreviewKeyEvent false
          }
          val next = (value.toInt() + step).coerceIn(MIN_TEXT_SIZE, MAX_TEXT_SIZE)
          value = next.toFloat()
          onChange(next)
          true
        },
      valueRange = MIN_TEXT_SIZE.toFloat()..MAX_TEXT_SIZE.toFloat(),
      // whole sizes only, one stop for each
      steps = MAX_TEXT_SIZE - MIN_TEXT_SIZE - 1,
      colors = SliderDefaults.colors(
        thumbColor = colors.accent,
        activeTrackColor = colors.accent,
        inactiveTrackColor = colors.gold.copy(alpha = 0.35f)
      )
    )
    Text(
      preview,
      style = if (arabic) {
        TextStyle(fontSize = (value * 1.4f).sp, color = colors.ink)
      } else {
        MenuText.copy(fontSize = value.sp, color = colors.ink)
      },
      maxLines = 1,
      modifier = Modifier.fillMaxWidth()
    )
  }
  HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = colors.gold.copy(alpha = 0.35f))
}

/** The settings screen's range for both sizes (its sliders run to 40); a size under 10 is unreadable. */
private const val MIN_TEXT_SIZE = 10
private const val MAX_TEXT_SIZE = 40

/**
 * A menu control's click; on TV a gold frame marks the one the remote is on (the default
 * focus highlight is too faint across a room). The same as a plain click on a phone.
 */
@Composable
private fun Modifier.menuClickable(shape: Shape, colors: MenuColors, onClick: () -> Unit): Modifier {
  val interaction = remember { MutableInteractionSource() }
  val focused by interaction.collectIsFocusedAsState()
  val tv = isTv()
  return this
    .clip(shape)
    .then(if (tv && focused) Modifier.border(3.dp, colors.gold, shape) else Modifier)
    .clickable(interactionSource = interaction, indication = LocalIndication.current, onClick = onClick)
}
