package com.quran.mobile.feature.livetv.radio

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import com.quran.mobile.di.QuranApplicationComponentProvider
import com.quran.mobile.feature.livetv.R
import com.quran.mobile.feature.livetv.data.LiveAvailability
import com.quran.mobile.feature.livetv.data.RadioStationRepository
import com.quran.mobile.feature.livetv.di.LiveTvComponentInterface
import com.quran.mobile.feature.livetv.ui.RadioScreen
import com.quran.mobile.feature.livetv.ui.UnavailableScreen
import dev.zacsweers.metro.Inject

class RadioActivity : AppCompatActivity() {

  @Inject
  lateinit var radioStationRepository: RadioStationRepository

  @Inject
  lateinit var liveAvailability: LiveAvailability

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val injector = (application as? QuranApplicationComponentProvider)
      ?.provideQuranApplicationComponent() as? LiveTvComponentInterface
    injector?.liveTvComponentFactory()?.generate()?.inject(this)

    // our own station first, then the Quran station
    val stations = radioStationRepository.stations()
    val isAvailable = liveAvailability.isRadioAvailable() && stations.isNotEmpty()

    enableEdgeToEdge()

    setContent {
      QuranTheme {
        if (isAvailable) {
          RadioScreen(stations = stations, onBack = onBackPressedDispatcher::onBackPressed)
        } else {
          UnavailableScreen(
            message = getString(R.string.livetv_unavailable_radio),
            onBack = onBackPressedDispatcher::onBackPressed
          )
        }
      }
    }
  }
}
