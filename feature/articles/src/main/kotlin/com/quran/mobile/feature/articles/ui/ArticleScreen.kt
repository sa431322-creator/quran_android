package com.quran.mobile.feature.articles.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.quran.labs.androidquran.common.ui.core.LocalTasnimPalette
import com.quran.labs.androidquran.common.ui.core.TasnimPaletteProvider
import com.quran.labs.androidquran.common.ui.core.Vazirmatn
import com.quran.mobile.feature.articles.R
import com.quran.mobile.feature.articles.data.Article

sealed interface ArticleState {
  data object Loading : ArticleState
  data object Error : ArticleState
  data object NotFound : ArticleState
  data class Loaded(val article: Article, val offline: Boolean) : ArticleState
}

@Composable
fun ArticleScreen(
  state: ArticleState,
  onShare: (Article) -> Unit,
  onRetry: () -> Unit,
  onBack: () -> Unit
) {
  CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
    TasnimPaletteProvider {
      val palette = LocalTasnimPalette.current
      Column(
        modifier = Modifier
          .fillMaxSize()
          .background(palette.background)
          .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))
      ) {
        Header(
          title = stringResource(R.string.articles_title),
          onBack = onBack,
          actions = {
            if (state is ArticleState.Loaded) {
              IconButton(onClick = { onShare(state.article) }, modifier = Modifier.size(44.dp)) {
                Icon(
                  imageVector = ArticleIcons.Share,
                  contentDescription = stringResource(R.string.articles_share),
                  tint = palette.accent
                )
              }
            }
          }
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
          when (state) {
            ArticleState.Loading -> CircularProgressIndicator(
              color = palette.accent,
              modifier = Modifier.align(Alignment.Center)
            )
            ArticleState.Error -> Message(
              text = stringResource(R.string.articles_error),
              action = stringResource(R.string.articles_retry),
              onAction = onRetry
            )
            ArticleState.NotFound -> Message(text = stringResource(R.string.articles_not_found))
            is ArticleState.Loaded -> Column {
              if (state.offline) {
                Text(
                  text = stringResource(R.string.articles_offline),
                  fontFamily = Vazirmatn,
                  fontSize = 13.sp,
                  color = palette.muted,
                  modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )
              }
              val by = stringResource(R.string.articles_by)
              val byline = ArticleDates.byline(state.article.author, state.article.publishedAt) {
                by.format(it)
              }
              ArticleWebView(
                html = ArticleHtml.page(state.article, byline, dark = isSystemInDarkTheme()),
                offline = state.offline,
                background = if (isSystemInDarkTheme()) 0xFF0E1518.toInt() else 0xFFF7F0E1.toInt()
              )
            }
          }
        }
      }
    }
  }
}

/**
 * The article in a WebView: no JavaScript (the HTML is the panel editor's, cleaned on the server),
 * no file or content access beyond the app's own CSS and font, links open in the browser.
 */
@Composable
private fun ArticleWebView(html: String, offline: Boolean, background: Int) {
  AndroidView(
    modifier = Modifier.fillMaxSize(),
    factory = { context ->
      WebView(context).apply {
        setBackgroundColor(background)
        settings.javaScriptEnabled = false
        settings.allowFileAccess = true // only file:///android_asset and android_res (CSS, font)
        settings.allowContentAccess = false
        settings.blockNetworkLoads = false
        settings.mediaPlaybackRequiresUserGesture = true
        webViewClient = object : WebViewClient() {
          override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val uri = request.url
            if (uri.scheme in setOf("http", "https", "mailto", "tel")) {
              try {
                view.context.startActivity(Intent(Intent.ACTION_VIEW, uri))
              } catch (_: ActivityNotFoundException) {
                // no app for this link
              }
            }
            return true
          }
        }
      }
    },
    update = { view ->
      // offline: images come from the WebView's cache when it has them
      view.settings.cacheMode =
        if (offline) WebSettings.LOAD_CACHE_ELSE_NETWORK else WebSettings.LOAD_DEFAULT
      view.setBackgroundColor(background)
      if (view.tag != html) {
        view.tag = html
        view.loadDataWithBaseURL(ArticleHtml.BASE_URL, html, "text/html", "utf-8", null)
      }
    }
  )
}
