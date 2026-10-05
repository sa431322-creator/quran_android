package com.quran.mobile.feature.livetv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import com.quran.mobile.di.QuranApplicationComponentProvider
import com.quran.mobile.feature.livetv.data.LiveChannelRepository
import com.quran.mobile.feature.livetv.di.LiveTvComponentInterface
import com.quran.mobile.feature.livetv.ui.LiveTvScreen
import dev.zacsweers.metro.Inject

class LiveTvActivity : ComponentActivity() {

  @Inject
  lateinit var liveChannelRepository: LiveChannelRepository

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val injector = (application as? QuranApplicationComponentProvider)
      ?.provideQuranApplicationComponent() as? LiveTvComponentInterface
    injector?.liveTvComponentFactory()?.generate()?.inject(this)

    val channel = liveChannelRepository.currentChannel()

    enableEdgeToEdge()

    setContent {
      QuranTheme {
        LiveTvScreen(
          channel = channel,
          modifier = Modifier
            .background(MaterialTheme.colorScheme.surface)
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))
        )
      }
    }
  }
}
