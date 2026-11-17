package com.quran.mobile.feature.livetv.radio

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import com.quran.labs.androidquran.common.ui.core.isTv
import com.quran.mobile.di.QuranApplicationComponentProvider
import com.quran.mobile.feature.livetv.LiveTvActivity
import com.quran.mobile.feature.livetv.R
import com.quran.mobile.feature.livetv.data.LiveAvailability
import com.quran.mobile.feature.livetv.data.RadioStationRepository
import com.quran.mobile.feature.livetv.di.LiveTvComponentInterface
import com.quran.mobile.feature.livetv.ui.RadioScreen
import com.quran.mobile.feature.livetv.ui.UnavailableScreen
import com.quran.mobile.feature.livetv.ui.tv.RadioTvScreen
import androidx.compose.runtime.CompositionLocalProvider
import com.quran.mobile.feature.ads.AdProvider
import com.quran.mobile.feature.ads.LocalAdProvider
import dev.zacsweers.metro.Inject

class RadioActivity : AppCompatActivity() {

  @Inject
  lateinit var radioStationRepository: RadioStationRepository

  @Inject
  lateinit var liveAvailability: LiveAvailability

  @Inject
  lateinit var adProvider: AdProvider

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val injector = (application as? QuranApplicationComponentProvider)
      ?.provideQuranApplicationComponent() as? LiveTvComponentInterface
    injector?.liveTvComponentFactory()?.generate()?.inject(this)

    // our own station first, then the Quran station
    val stations = radioStationRepository.stations()
    // from the live video's «صوتی» switch: the viewer wants to listen, so a paused station plays
    // (only on the first start, not again after a rotation)
    val playOnOpen = savedInstanceState == null && intent.getBooleanExtra(EXTRA_PLAY, false)
    val isAvailable = liveAvailability.isRadioAvailable() && stations.isNotEmpty()

    enableEdgeToEdge()

    val openVideo = {
      // the switch swaps screens rather than stacking them, as the live screen's does
      startActivity(Intent(this, LiveTvActivity::class.java))
      finish()
    }
    setContent {
      QuranTheme {
        if (!isAvailable) {
          UnavailableScreen(
            message = getString(R.string.livetv_unavailable_radio),
            onBack = onBackPressedDispatcher::onBackPressed
          )
        } else if (isTv()) {
          // driven by the remote (docs/tv/PLAN.md, phase 2); no ads on TV
          RadioTvScreen(stations = stations, playOnOpen = playOnOpen, onOpenVideo = openVideo)
        } else {
          CompositionLocalProvider(LocalAdProvider provides adProvider) {
            RadioScreen(
              stations = stations,
              playOnOpen = playOnOpen,
              onBack = onBackPressedDispatcher::onBackPressed
            )
          }
        }
      }
    }
  }

  companion object {
    /** Set by the live video's «صوتی» switch: play even if the station was paused. */
    const val EXTRA_PLAY = "com.quran.mobile.feature.livetv.radio.PLAY"
  }
}
