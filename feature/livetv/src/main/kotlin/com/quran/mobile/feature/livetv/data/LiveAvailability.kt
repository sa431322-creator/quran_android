package com.quran.mobile.feature.livetv.data

/**
 * Whether the video broadcast and the radio are switched on. When one is off its screen opens
 * on a «در حال حاضر در دسترس نیست» note instead of the player.
 */
interface LiveAvailability {
  fun isLiveAvailable(): Boolean
  fun isRadioAvailable(): Boolean
}
