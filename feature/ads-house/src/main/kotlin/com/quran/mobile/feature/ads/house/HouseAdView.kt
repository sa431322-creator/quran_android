package com.quran.mobile.feature.ads.house

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.quran.mobile.feature.ads.AdContent
import com.quran.mobile.feature.ads.adLinkProblem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val LABEL = "تبلیغ"
private const val REPORT = "گزارش تبلیغ"
private val BOOK_LINK = Regex("""^tasneem://library/book/([1-9][0-9]{0,9})$""")

/**
 * The ad as the app draws it: a rounded picture (never mirrored), the «تبلیغ» label at the top
 * start corner, a 1 dp outline in dark mode, one accessibility node. Counts an impression once it
 * has been at least half visible, on a resumed screen, with its picture drawn, for one second.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun HouseAdView(content: AdContent, provider: HouseAdProvider, modifier: Modifier) {
  val context = LocalContext.current
  val bitmap by produceState<ImageBitmap?>(null, content.image) {
    value = withContext(Dispatchers.IO) {
      runCatching { BitmapFactory.decodeFile(content.image.path)?.asImageBitmap() }.getOrNull()
    }
  }

  // ---- counting
  val lifecycleOwner = LocalLifecycleOwner.current
  var resumed by remember {
    mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
  }
  var halfVisible by remember { mutableStateOf(false) }
  // Once per ad per screen visit; a new visit starts after the screen was stopped.
  var counted by remember(content.id) { mutableStateOf(false) }
  DisposableEffect(lifecycleOwner, content.id) {
    var stopped = false
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_RESUME -> resumed = true
        Lifecycle.Event.ON_PAUSE -> resumed = false
        Lifecycle.Event.ON_STOP -> stopped = true
        Lifecycle.Event.ON_START -> if (stopped) {
          stopped = false
          counted = false
        }
        else -> Unit
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }
  val drawn = bitmap != null
  LaunchedEffect(halfVisible, resumed, drawn, counted) {
    if (halfVisible && resumed && drawn && !counted) {
      delay(IMPRESSION_MS)
      counted = true
      provider.record(content, impressions = 1, clicks = 0)
    }
  }

  var menu by remember { mutableStateOf(false) }
  val link = content.actionUrl?.takeIf { adLinkProblem(it) == null }
  val dark = isSystemInDarkTheme()
  val shape = RoundedCornerShape(12.dp)

  Column(modifier.fillMaxWidth()) {
    Box(
      Modifier
        .fillMaxWidth()
        .aspectRatio(content.width.toFloat() / content.height)
        .heightIn(min = 48.dp)
        .clip(shape)
        .then(if (dark) Modifier.border(1.dp, Color(0xFF3A4A47), shape) else Modifier)
        .onGloballyPositioned { c ->
          val total = c.size.width.toFloat() * c.size.height
          val b = c.boundsInWindow()
          halfVisible = total > 0 && b.width * b.height / total >= 0.5f
        }
        .semantics(mergeDescendants = true) {
          contentDescription = "$LABEL: ${content.altText}"
          if (link != null) role = Role.Button
        }
        .combinedClickable(
          enabled = true,
          onClickLabel = if (link != null) "باز کردن" else null,
          onLongClickLabel = REPORT,
          onClick = {
            if (link == null) return@combinedClickable
            // A tap also counts the view if the 1-second rule had not yet (clicks ≤ impressions).
            provider.record(content, impressions = if (counted) 0 else 1, clicks = 1)
            counted = true
            open(context, link, provider)
          },
          onLongClick = { menu = true },
        )
    ) {
      bitmap?.let {
        Image(
          bitmap = it,
          contentDescription = null,
          contentScale = ContentScale.Crop,
          modifier = Modifier.fillMaxSize(),
        )
      }
      Text(
        text = LABEL,
        color = Color.White,
        fontSize = 11.sp,
        modifier = Modifier
          .align(Alignment.TopStart)
          .padding(6.dp)
          .background(Color(0xD91C1A16), RoundedCornerShape(4.dp))
          .padding(horizontal = 6.dp, vertical = 2.dp),
      )
      DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
        DropdownMenuItem(
          text = { Text(REPORT) },
          onClick = {
            menu = false
            report(context, content, provider.reportLinks())
          },
        )
      }
    }
    content.actionText?.let {
      Text(
        text = it,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier
          .padding(top = 8.dp)
          .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
          .padding(horizontal = 12.dp, vertical = 6.dp),
      )
    }
  }
}

private const val IMPRESSION_MS = 1_000L

/** Checked again here (same rules as the server); a book link opens inside the app. */
private fun open(context: Context, link: String, provider: HouseAdProvider) {
  if (adLinkProblem(link) != null) return
  val book = BOOK_LINK.matchEntire(link)?.groupValues?.get(1)
  val intent = if (book != null) provider.bookIntent(book) else Intent(Intent.ACTION_VIEW, link.toUri())
  intent ?: return
  try {
    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
  } catch (_: ActivityNotFoundException) {
    // no app can open it: nothing happens
  }
}

/** «گزارش تبلیغ»: email (with the ad number in the subject) or Telegram, from the server links. */
private fun report(context: Context, content: AdContent, links: List<String>) {
  val subject = "$REPORT #${content.id}"
  for (link in links) {
    val intent = if (link.startsWith("mailto:", ignoreCase = true)) {
      Intent(Intent.ACTION_SENDTO, link.substringBefore('?').toUri())
        .putExtra(Intent.EXTRA_SUBJECT, subject)
    } else {
      Intent(Intent.ACTION_VIEW, link.toUri())
    }
    try {
      context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
      return
    } catch (_: ActivityNotFoundException) {
      // try the next one
    }
  }
}
