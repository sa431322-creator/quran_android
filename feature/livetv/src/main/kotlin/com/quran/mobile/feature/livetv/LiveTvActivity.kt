package com.quran.mobile.feature.livetv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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

    val channels = liveChannelRepository.channels()

    enableEdgeToEdge()

    setContent {
      QuranTheme {
        LiveTvScreen(
          channels = channels,
          onBack = onBackPressedDispatcher::onBackPressed,
          onOpenAudio = {}
        )
      }
    }
  }
}
