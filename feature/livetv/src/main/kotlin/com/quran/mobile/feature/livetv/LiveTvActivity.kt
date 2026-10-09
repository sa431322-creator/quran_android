package com.quran.mobile.feature.livetv

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import com.quran.mobile.di.QuranApplicationComponentProvider
import com.quran.mobile.feature.livetv.data.LiveChannelRepository
import com.quran.mobile.feature.livetv.di.LiveTvComponentInterface
import com.quran.mobile.feature.livetv.radio.RadioActivity
import com.quran.mobile.feature.livetv.ui.LiveTvScreen
import dev.zacsweers.metro.Inject

class LiveTvActivity : AppCompatActivity() {

  @Inject
  lateinit var liveChannelRepository: LiveChannelRepository

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val injector = (application as? QuranApplicationComponentProvider)
      ?.provideQuranApplicationComponent() as? LiveTvComponentInterface
    injector?.liveTvComponentFactory()?.generate()?.inject(this)

    val channel = liveChannelRepository.channel()

    enableEdgeToEdge()

    setContent {
      QuranTheme {
        LiveTvScreen(
          channel = channel,
          onBack = onBackPressedDispatcher::onBackPressed,
          onOpenAudio = {
            // the switch swaps screens rather than stacking them
            startActivity(Intent(this, RadioActivity::class.java))
            finish()
          }
        )
      }
    }
  }
}
