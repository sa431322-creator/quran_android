package com.quran.mobile.feature.livetv.player

sealed interface LivePlayerState {
  data object Buffering : LivePlayerState
  data object Playing : LivePlayerState

  /** Ready but not playing, for example after losing audio focus to a call. */
  data object Paused : LivePlayerState
  data class Error(val reason: String) : LivePlayerState
}
