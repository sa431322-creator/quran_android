package com.quran.labs.androidquran.ui.cards

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.common.ui.core.QuranIcons
import com.quran.labs.androidquran.ui.mushaf.TextMushafStyle
import com.quran.labs.androidquran.util.QuranUtils
import com.quran.mobile.feature.mushaf.data.AyahGlyph
import com.quran.mobile.feature.mushaf.data.MushafData
import com.quran.mobile.feature.mushaf.data.MushafRepository
import com.quran.mobile.feature.mushaf.data.ayahKeyAyah
import com.quran.mobile.feature.mushaf.data.ayahKeySura
import com.quran.mobile.feature.mushaf.ui.MushafAyahMarker
import com.quran.mobile.feature.mushaf.ui.MushafAyahText
import com.quran.mobile.feature.mushaf.ui.MushafPageStyle
import com.quran.mobile.feature.mushaf.ui.MushafSuraStart
import com.quran.mobile.feature.mushaf.ui.PageFontsState
import com.quran.mobile.feature.mushaf.ui.rememberPageFonts
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop

/** What the ayah cards show; the reader keeps it up to date and the cards read it. */
class AyahCardsState {
  var repository by mutableStateOf<MushafRepository?>(null)
  /** the Persian text (Jami' al-Bayan) of every ayah by [com.quran.mobile.feature.mushaf.data.ayahKey] */
  var persian by mutableStateOf<Map<Int, String>?>(null)
  var nightMode by mutableStateOf(false)
  var nightBrightness by mutableIntStateOf(255)
  /** the page theme's paper, or the night background (ARGB): behind the cards, as behind the pages */
  var paper by mutableIntStateOf(0xFFF6EEDD.toInt())
  /** the app's text-size setting (sp), which the cards follow */
  var textSize by mutableIntStateOf(15)
  var columns by mutableIntStateOf(1)
  /** the reader's bars over the cards, in px, and whether they are showing */
  var topInset by mutableIntStateOf(0)
  var bottomInset by mutableIntStateOf(0)
  var barsVisible by mutableStateOf(true)
  var playingAyah by mutableStateOf<Int?>(null)
  var selectedAyah by mutableStateOf<Int?>(null)
  /** the global switch: the Persian text on every card */
  var showAll by mutableStateOf(true)
  /** cards opened or closed on their own, against [showAll] */
  val opened = mutableStateMapOf<Int, Boolean>()
  /** an ayah to bring on screen; cleared once done */
  var scrollTo by mutableStateOf<ScrollRequest?>(null)
}

/** Brings the ayah [key] to the top, or, with [onlyIfHidden], scrolls only when it is off screen. */
data class ScrollRequest(val key: Int, val onlyIfHidden: Boolean = false)

/** What a card hands to the reader. */
interface AyahCardsActions {
  /** a tap on a card: the reader shows or hides its bars, as for a tap on a page */
  fun onTap()
  fun onPlay(sura: Int, ayah: Int)
  fun onBookmark(sura: Int, ayah: Int)
  /** a long press: the reader's ayah toolbar at [bounds] (window coordinates) */
  fun onLongPress(sura: Int, ayah: Int, bounds: Rect)
  /**
   * the first ayah on screen changed (after scrolling settles); [byUser] is false when the cards
   * scrolled themselves (opening at an ayah, following the recitation)
   */
  fun onTopAyah(sura: Int, ayah: Int, byUser: Boolean)
  fun onShowAllChanged(showAll: Boolean)
}

private sealed interface CardItem {
  val id: Any

  data class Opening(val sura: Int) : CardItem {
    override val id: Any get() = "sura-$sura"
  }

  data class Ayah(val key: Int, val glyphs: List<AyahGlyph>) : CardItem {
    override val id: Any get() = key
  }
}

/** Every ayah in its card, in order, with each sura's opening before its first ayah. */
private fun cardItems(data: MushafData): List<CardItem> {
  val items = ArrayList<CardItem>(data.ayahs.size + 114)
  var sura = 0
  for ((key, glyphs) in data.ayahs) {
    if (ayahKeySura(key) != sura) {
      sura = ayahKeySura(key)
      items += CardItem.Opening(sura)
    }
    items += CardItem.Ayah(key, glyphs)
  }
  return items
}

/**
 * The ayah cards (Phase 2 of docs/text-mushaf/PLAN.md): every ayah of the mushaf in its own card,
 * in its page's mushaf font, with the Persian text of «جامع البیان» under it. One switch shows or
 * hides the Persian text on every card, and each card can be opened or closed on its own.
 */
@Composable
fun AyahCards(state: AyahCardsState, suraName: (Int) -> String, actions: AyahCardsActions) {
  val context = LocalContext.current
  val repository = state.repository ?: return
  val colors = remember(state.nightMode, state.paper) { CardColors.of(context, state.nightMode, Color(state.paper)) }
  val style = remember(state.nightMode, state.nightBrightness) {
    TextMushafStyle.pageStyle(context, state.nightMode, state.nightBrightness)
  }
  val data by produceState<MushafData?>(null, repository) {
    value = null
    value = runCatching { repository.data() }.getOrNull()
  }
  CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
    Box(Modifier.fillMaxSize().background(colors.background)) {
      val current = data
      if (current == null) {
        CircularProgressIndicator(Modifier.align(Alignment.Center), color = colors.action)
      } else {
        CardList(state, current, repository, style, colors, suraName, actions)
      }
    }
  }
}

@Composable
private fun CardList(
  state: AyahCardsState,
  data: MushafData,
  repository: MushafRepository,
  style: MushafPageStyle,
  colors: CardColors,
  suraName: (Int) -> String,
  actions: AyahCardsActions
) {
  val context = LocalContext.current
  val density = LocalDensity.current
  val items = remember(data) { cardItems(data) }
  // the switch row is the grid's first item; a sura's first ayah is reached at its opening
  val indexOf = remember(items) {
    items.withIndex().mapNotNull { (index, item) ->
      (item as? CardItem.Ayah)?.let { it.key to if (ayahKeyAyah(it.key) == 1) index else index + 1 }
    }.toMap()
  }
  val grid = rememberLazyGridState()
  val uiFont = remember { FontFamily(Font(com.quran.mobile.common.ui.core.R.font.tasnim_ui)) }
  val arabicSize = (state.textSize * 1.75f).coerceAtMost(64f).sp
  val persianSize = state.textSize.toFloat().sp
  val openingRow = with(density) { (arabicSize.toPx() * 1.9f).toDp() }

  // set while the cards scroll themselves, so the settled top ayah is not taken for the reader's
  var selfScroll by remember { mutableStateOf(false) }
  LaunchedEffect(state.scrollTo, indexOf) {
    val request = state.scrollTo ?: return@LaunchedEffect
    val index = indexOf[request.key]
    if (index != null) {
      // wholly in view: not partly under the reader's bars
      val info = grid.layoutInfo
      val visible = info.visibleItemsInfo.any {
        it.index == index &&
          it.offset.y >= info.viewportStartOffset + info.beforeContentPadding &&
          it.offset.y + it.size.height <= info.viewportEndOffset - info.afterContentPadding
      }
      when {
        !request.onlyIfHidden -> {
          if (grid.firstVisibleItemIndex != index) selfScroll = true
          grid.scrollToItem(index)
        }
        !visible -> {
          selfScroll = true
          grid.animateScrollToItem(index)
        }
      }
    }
    state.scrollTo = null
  }
  LaunchedEffect(grid, items) {
    // the first value is the list before the reader's first scroll: not where the reader is
    snapshotFlow { grid.firstVisibleItemIndex }.drop(1).collectLatest { first ->
      delay(TOP_AYAH_SETTLE_MS)
      val byUser = !selfScroll
      selfScroll = false
      val key = (maxOf(first - 1, 0) until items.size).firstNotNullOfOrNull { (items[it] as? CardItem.Ayah)?.key }
      if (key != null) actions.onTopAyah(ayahKeySura(key), ayahKeyAyah(key), byUser)
    }
  }

  val top = with(density) { (if (state.barsVisible) state.topInset else 0).toDp() }
  val bottom = with(density) { (if (state.barsVisible) state.bottomInset else 0).toDp() }
  LazyVerticalGrid(
    columns = GridCells.Fixed(state.columns.coerceAtLeast(1)),
    state = grid,
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = top + 12.dp, bottom = bottom + 16.dp),
    horizontalArrangement = Arrangement.spacedBy(14.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp)
  ) {
    item(key = "show-all", span = { GridItemSpan(maxLineSpan) }) {
      ShowAllRow(state, colors, uiFont, actions)
    }
    items(
      items,
      key = { it.id },
      span = { if (it is CardItem.Opening) GridItemSpan(maxLineSpan) else GridItemSpan(1) },
      contentType = { it::class }
    ) { item ->
      when (item) {
        is CardItem.Opening -> MushafSuraStart(
          sura = item.sura,
          name = suraName(item.sura),
          style = style,
          basmalaFont = TextMushafStyle.basmalaFont(context),
          rowHeight = openingRow,
          modifier = Modifier.fillMaxWidth()
        )
        is CardItem.Ayah -> {
          val open = state.opened[item.key] ?: state.showAll
          AyahCard(
            item = item,
            persian = state.persian?.get(item.key),
            open = open,
            playing = state.playingAyah == item.key,
            selected = state.selectedAyah == item.key,
            repository = repository,
            style = style,
            colors = colors,
            uiFont = uiFont,
            arabicSize = arabicSize.value,
            persianSize = persianSize.value,
            actions = actions,
            onToggle = { state.opened[item.key] = !open }
          )
        }
      }
    }
    item(key = "source", span = { GridItemSpan(maxLineSpan) }) {
      Text(
        text = stringResource(R.string.tasnim_cards_source),
        fontFamily = uiFont,
        fontSize = 12.sp,
        color = colors.secondary,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
      )
    }
  }
}

@Composable
private fun ShowAllRow(state: AyahCardsState, colors: CardColors, uiFont: FontFamily, actions: AyahCardsActions) {
  Row(
    Modifier
      .fillMaxWidth()
      .heightIn(min = 48.dp)
      .toggleable(value = state.showAll, role = Role.Switch) { show ->
        state.showAll = show
        state.opened.clear()
        actions.onShowAllChanged(show)
      },
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(
      text = stringResource(R.string.tasnim_cards_show_text),
      fontFamily = uiFont,
      fontSize = 15.sp,
      color = colors.text,
      modifier = Modifier.weight(1f)
    )
    Switch(
      checked = state.showAll,
      onCheckedChange = null,
      colors = SwitchDefaults.colors(
        checkedTrackColor = colors.action,
        checkedThumbColor = colors.card,
        uncheckedTrackColor = colors.rule,
        uncheckedThumbColor = colors.card,
        uncheckedBorderColor = colors.border
      )
    )
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AyahCard(
  item: CardItem.Ayah,
  persian: String?,
  open: Boolean,
  playing: Boolean,
  selected: Boolean,
  repository: MushafRepository,
  style: MushafPageStyle,
  colors: CardColors,
  uiFont: FontFamily,
  arabicSize: Float,
  persianSize: Float,
  actions: AyahCardsActions,
  onToggle: () -> Unit
) {
  val sura = ayahKeySura(item.key)
  val ayah = ayahKeyAyah(item.key)
  val number = QuranUtils.getLocalizedNumber(ayah)
  var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
  val shape = RoundedCornerShape(16.dp)
  val background = when {
    selected -> colors.selected
    playing -> colors.playing
    else -> colors.card
  }
  Column(
    Modifier
      .fillMaxWidth()
      .onGloballyPositioned { coordinates = it }
      .clip(shape)
      .background(background)
      .border(1.dp, if (playing) colors.playingBorder else colors.border, shape)
      .combinedClickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onLongClickLabel = stringResource(R.string.tasnim_cards_actions),
        onLongClick = { coordinates?.let { actions.onLongPress(sura, ayah, it.boundsInWindow()) } },
        onClick = actions::onTap
      )
      .padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 4.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp)
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      MushafAyahMarker(ayah, style, 40.dp)
      Spacer(Modifier.weight(1f))
      IconButton(onClick = { actions.onPlay(sura, ayah) }) {
        // the audio bar's own play icon
        Icon(
          QuranIcons.PlayArrow,
          contentDescription = stringResource(R.string.tasnim_cards_play, number),
          tint = colors.action
        )
      }
      IconButton(onClick = { actions.onBookmark(sura, ayah) }) {
        Icon(
          painterResource(R.drawable.ic_tasnim_bookmark),
          contentDescription = stringResource(R.string.tasnim_cards_bookmark, number),
          tint = colors.action
        )
      }
    }

    val pages = remember(item) { item.glyphs.map { it.page }.distinct() }
    var attempt by remember { mutableIntStateOf(0) }
    when (val fonts = rememberPageFonts(repository, pages, attempt)) {
      is PageFontsState.Ready -> MushafAyahText(
        glyphs = item.glyphs,
        ayahNumber = ayah,
        fonts = fonts.fonts,
        fontSize = arabicSize.sp,
        style = style,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
      )
      PageFontsState.Loading -> Box(
        Modifier.fillMaxWidth().height((arabicSize * 2f).dp),
        contentAlignment = Alignment.Center
      ) {
        CircularProgressIndicator(Modifier.size(24.dp), color = colors.action, strokeWidth = 2.dp)
      }
      // as on the page: the page font could not be downloaded (offline)
      PageFontsState.Failed -> Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
          text = stringResource(com.quran.mobile.feature.mushaf.R.string.mushaf_error),
          fontFamily = uiFont,
          fontSize = 13.sp,
          color = colors.secondary
        )
        TextButton(onClick = { attempt++ }) {
          Text(
            text = stringResource(com.quran.mobile.feature.mushaf.R.string.mushaf_retry),
            fontFamily = uiFont,
            color = colors.action
          )
        }
      }
    }

    if (open && persian != null) {
      HorizontalDivider(color = colors.rule)
      Text(
        text = persian,
        fontFamily = uiFont,
        fontSize = persianSize.sp,
        lineHeight = (persianSize * 1.9f).sp,
        color = colors.text
      )
    }

    TextButton(onClick = onToggle) {
      Icon(
        painterResource(R.drawable.ic_expand_more_24),
        contentDescription = null,
        tint = colors.action,
        modifier = Modifier.size(18.dp).rotate(if (open) 180f else 0f)
      )
      Text(
        text = stringResource(if (open) R.string.tasnim_cards_close_text else R.string.tasnim_cards_open_text),
        fontFamily = uiFont,
        fontSize = 13.sp,
        color = colors.action,
        modifier = Modifier.padding(start = 6.dp)
      )
    }
  }
}

/** The cards' colors, day or the reader's night mode (res/values/colors.xml, mushaf_card*). */
private class CardColors(
  val background: Color,
  val card: Color,
  val border: Color,
  val text: Color,
  val secondary: Color,
  val rule: Color,
  val action: Color,
  val playing: Color,
  val playingBorder: Color,
  val selected: Color
) {
  companion object {
    /** On [paper], the page theme's paper by day (or the night background). */
    fun of(context: Context, night: Boolean, paper: Color): CardColors {
      fun color(day: Int, nightId: Int) = Color(ContextCompat.getColor(context, if (night) nightId else day))
      return CardColors(
        background = paper,
        // a card is a lighter sheet of the same paper: white on the Light theme by day, and a
        // shade above the night background (which the reader may brighten) at night
        card = lerp(paper, Color.White, if (night) 0.07f else 0.55f),
        border = color(R.color.mushaf_card_border, R.color.mushaf_card_border_night),
        text = color(R.color.mushaf_card_text, R.color.mushaf_card_text_night),
        secondary = color(R.color.mushaf_label, R.color.mushaf_label_night),
        rule = color(R.color.mushaf_card_rule, R.color.mushaf_card_rule_night),
        action = color(R.color.mushaf_card_action, R.color.mushaf_card_action_night),
        playing = color(R.color.mushaf_card_playing, R.color.mushaf_card_playing_night),
        playingBorder = color(R.color.mushaf_card_playing_border, R.color.mushaf_card_playing_border_night),
        selected = color(R.color.mushaf_card_selected, R.color.mushaf_card_selected_night)
      )
    }
  }
}

private const val TOP_AYAH_SETTLE_MS = 400L
