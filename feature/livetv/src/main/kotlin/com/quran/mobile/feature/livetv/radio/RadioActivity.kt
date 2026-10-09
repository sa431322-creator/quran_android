package com.quran.mobile.feature.livetv.radio

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import com.quran.mobile.di.QuranApplicationComponentProvider
import com.quran.mobile.feature.livetv.data.RadioStationRepository
import com.quran.mobile.feature.livetv.di.LiveTvComponentInterface
import com.quran.mobile.feature.livetv.ui.RadioScreen
import dev.zacsweers.metro.Inject

class RadioActivity : AppCompatActivity() {

  @Inject
  lateinit var radioStationRepository: RadioStationRepository

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val injector = (application as? QuranApplicationComponentProvider)
      ?.provideQuranApplicationComponent() as? LiveTvComponentInterface
    injector?.liveTvComponentFactory()?.generate()?.inject(this)

    // Tasnim broadcasts a single station
    val station = radioStationRepository.stations().first()

    enableEdgeToEdge()

    setContent {
      QuranTheme {
        RadioScreen(station = station, onBack = onBackPressedDispatcher::onBackPressed)
      }
    }
  }
}
