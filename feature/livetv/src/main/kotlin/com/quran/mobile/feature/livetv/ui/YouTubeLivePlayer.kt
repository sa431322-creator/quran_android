package com.quran.mobile.feature.livetv.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color as AndroidColor
import android.util.Log
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.quran.labs.androidquran.common.ui.core.LocalTasnimPalette
import com.quran.labs.androidquran.common.ui.core.Vazirmatn
import com.quran.mobile.feature.livetv.R
import com.quran.mobile.feature.livetv.data.LiveStreamSource
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * A YouTube video or channel live stream in YouTube's own embedded (IFrame) player. YouTube's
 * terms forbid drawing over the embedded player, so unlike [LiveVideoPlayer] it has no badge,
 * watermark or control bar of ours: YouTube's controls are used, and fullscreen is toggled
 * from the screen below the video.
 *
 * Like [LiveVideoPlayer], playback stops on ON_STOP and the WebView is destroyed when this
 * composable leaves. The screen is kept awake only while YouTube reports it is playing.
 */
@Composable
fun YouTubeLivePlayer(source: LiveStreamSource.YouTube, modifier: Modifier = Modifier) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val palette = LocalTasnimPalette.current
  val bridge = remember(source) { YouTubeBridge() }
  val webView = remember(source) { youTubeWebView(context, source, bridge) }
  val isPlaying by bridge.isPlaying.collectAsState()
  val failed by bridge.failed.collectAsState()

  DisposableEffect(lifecycleOwner, webView) {
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_START -> {
          webView.onResume()
          // back from the background the live stream plays again, as the ExoPlayer one does
          webView.evaluateJavascript("player && player.playVideo && player.playVideo()", null)
        }
        Lifecycle.Event.ON_STOP -> {
          webView.evaluateJavascript("player && player.pauseVideo && player.pauseVideo()", null)
          webView.onPause()
        }
        else -> Unit
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
      // a WebView must leave its window before it is destroyed
      (webView.parent as? ViewGroup)?.removeView(webView)
      webView.destroy()
    }
  }

  Box(modifier = modifier.background(palette.videoBackground)) {
    if (failed) {
      // replaces the player instead of covering it
      Column(
        modifier = Modifier.align(Alignment.Center),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Text(
          text = stringResource(R.string.livetv_error),
          color = palette.onVideo,
          fontFamily = Vazirmatn
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { bridge.failed.value = false; loadPlayer(webView, source) }) {
          Text(
            text = stringResource(R.string.livetv_retry),
            color = palette.onVideo,
            fontFamily = Vazirmatn
          )
        }
      }
    } else {
      // keyed so a new source's WebView replaces the old one; the factory only runs once per key
      key(webView) {
        AndroidView(
          factory = { webView },
          update = { it.keepScreenOn = isPlaying },
          modifier = Modifier.fillMaxSize()
        )
      }
    }
  }
}

/** Receives the IFrame player's events; WebView calls these on a background thread. */
private class YouTubeBridge {
  val isPlaying = MutableStateFlow(false)
  val failed = MutableStateFlow(false)

  @JavascriptInterface
  fun onState(state: Int) {
    isPlaying.value = state == PLAYING
  }

  @JavascriptInterface
  fun onError(code: Int) {
    // -1 the api did not load (offline), 2 bad id, 5 html5 player, 100 not found or private,
    // 101/150 owner forbids embedding, 152/153 the embedding page was refused (no origin/referrer)
    Log.w(TAG, "YouTube player error $code")
    isPlaying.value = false
    failed.value = true
  }

  private companion object {
    const val TAG = "YouTubeLivePlayer"

    // YT.PlayerState.PLAYING
    const val PLAYING = 1
  }
}

@SuppressLint("SetJavaScriptEnabled")
private fun youTubeWebView(
  context: Context,
  source: LiveStreamSource.YouTube,
  bridge: YouTubeBridge
): WebView = WebView(context).apply {
  setBackgroundColor(AndroidColor.BLACK)
  settings.javaScriptEnabled = true
  // a live screen starts playing on its own, as the ExoPlayer one does
  settings.mediaPlaybackRequiresUserGesture = false
  addJavascriptInterface(bridge, "TasnimYouTube")
  loadPlayer(this, source)
}

private fun loadPlayer(webView: WebView, source: LiveStreamSource.YouTube) {
  // YouTube refuses to embed for a page without an origin (errors 152/153), so the page gets
  // an https origin of its own and passes it to the player
  val origin = "https://${webView.context.packageName}"
  webView.loadDataWithBaseURL(origin, youTubePlayerHtml(source, origin), "text/html", "utf-8", null)
}

internal fun youTubeEmbedUrl(source: LiveStreamSource.YouTube, origin: String): String {
  val path = source.videoId?.let { "embed/$it?" } ?: "embed/live_stream?channel=${source.channelId}&"
  // fs=0: YouTube's fullscreen needs a WebChromeClient custom view; the screen has its own
  return "https://www.youtube.com/$path" +
    "enablejsapi=1&autoplay=1&playsinline=1&fs=0&rel=0&hl=fa&origin=$origin"
}

private fun youTubePlayerHtml(source: LiveStreamSource.YouTube, origin: String): String = """
  <!doctype html>
  <html><head>
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <style>html,body{margin:0;height:100%;background:#000;overflow:hidden}
  iframe{border:0;width:100%;height:100%}</style>
  </head><body>
  <iframe id="player" src="${youTubeEmbedUrl(source, origin)}"
    allow="autoplay; encrypted-media; picture-in-picture"
    referrerpolicy="strict-origin-when-cross-origin"></iframe>
  <script>
    var player;
    function onYouTubeIframeAPIReady() {
      player = new YT.Player('player', { events: {
        onStateChange: function (e) { TasnimYouTube.onState(e.data); },
        onError: function (e) { TasnimYouTube.onError(e.data); }
      }});
    }
  </script>
  <!-- offline, the api never loads and no player event would come: report it as an error -->
  <script src="https://www.youtube.com/iframe_api"
    onerror="TasnimYouTube.onError(-1)"></script>
  </body></html>
""".trimIndent()
