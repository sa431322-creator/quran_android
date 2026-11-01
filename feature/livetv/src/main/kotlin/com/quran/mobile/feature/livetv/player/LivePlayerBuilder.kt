package com.quran.mobile.feature.livetv.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory

/**
 * An [ExoPlayer.Builder] for the live TV and radio players. Stream links from the admin panel may
 * redirect across schemes: the server's official Quran channel link answers a 302 to a freshly
 * signed https link, and in development that server is plain http. Media3 refuses such redirects
 * by default; release builds still refuse cleartext through the network security config.
 */
@OptIn(UnstableApi::class)
fun liveExoPlayerBuilder(context: Context): ExoPlayer.Builder {
  val http = DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true)
  val dataSource = DefaultDataSource.Factory(context, http)
  return ExoPlayer.Builder(context).setMediaSourceFactory(DefaultMediaSourceFactory(dataSource))
}
