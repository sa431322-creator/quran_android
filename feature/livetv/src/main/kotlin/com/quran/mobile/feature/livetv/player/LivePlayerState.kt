package com.quran.mobile.feature.livetv.player

sealed interface LivePlayerState {
  data object Buffering : LivePlayerState
  data object Playing : LivePlayerState

  /** Ready but not playing, either paused by the user or after losing audio focus. */
  data object Paused : LivePlayerState
  data class Error(val reason: String) : LivePlayerState
}
