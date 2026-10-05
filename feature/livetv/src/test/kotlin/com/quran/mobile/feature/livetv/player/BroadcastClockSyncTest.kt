package com.quran.mobile.feature.livetv.player

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BroadcastClockSyncTest {

  @Test
  fun livePositionWrapsAroundTheClip() {
    assertThat(BroadcastClockSync.livePosition(durationMs = 1_000, nowMs = 2_500)).isEqualTo(500)
    assertThat(BroadcastClockSync.livePosition(durationMs = 1_000, nowMs = 999)).isEqualTo(999)
    assertThat(BroadcastClockSync.livePosition(durationMs = 1_000, nowMs = 3_000)).isEqualTo(0)
  }
}
