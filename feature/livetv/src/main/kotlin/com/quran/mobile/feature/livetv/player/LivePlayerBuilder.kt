package com.quran.mobile.feature.livetv.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import java.net.CookieManager
import java.net.URI

/**
 * An [ExoPlayer.Builder] for the live TV and radio players.
 *
 * Stream links from the admin panel may redirect across schemes: the server's official Quran
 * links answer a 302 to freshly signed https links, and in development that server is plain http.
 * Media3 refuses such redirects by default; release builds still refuse cleartext through the
 * network security config.
 *
 * Each player also keeps its own cookies: the Quran radio's DASH manifest sets a session cookie
 * that its audio chunks need (HLS carries the same token in its links instead).
 */
@OptIn(UnstableApi::class)
fun liveExoPlayerBuilder(context: Context): ExoPlayer.Builder {
  val http = DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true)
  val cookies = CookieManager()
  val withCookies = DataSource.Factory { CookieDataSource(http.createDataSource(), cookies) }
  val dataSource = DefaultDataSource.Factory(context, withCookies)
  return ExoPlayer.Builder(context).setMediaSourceFactory(DefaultMediaSourceFactory(dataSource))
}

/** Sends the cookies stored for a link with its request, and stores the ones it answers with. */
@OptIn(UnstableApi::class)
private class CookieDataSource(
  private val upstream: HttpDataSource,
  private val cookies: CookieManager
) : DataSource by upstream {

  override fun open(dataSpec: DataSpec): Long {
    val stored = cookies.get(URI(dataSpec.uri.toString()), emptyMap())["Cookie"].orEmpty()
    val spec = if (stored.isEmpty()) {
      dataSpec
    } else {
      dataSpec.withAdditionalHeaders(mapOf("Cookie" to stored.joinToString("; ")))
    }
    val length = upstream.open(spec)
    // after redirects, the cookies belong to the final address
    val finalUri = upstream.uri ?: dataSpec.uri
    cookies.put(URI(finalUri.toString()), upstream.responseHeaders.filterKeys { it != null })
    return length
  }
}
