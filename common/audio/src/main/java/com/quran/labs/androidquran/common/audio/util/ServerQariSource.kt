package com.quran.labs.androidquran.common.audio.util

import com.quran.data.model.audio.Qari

/**
 * The reciter list last received from the Tasnim server, if any. Must be instant (it is read on
 * the main thread); null or empty means "use the built-in list".
 */
fun interface ServerQariSource {
  fun cached(): List<Qari>?

  companion object {
    /** No server list: always the built-in reciters. */
    val None = ServerQariSource { null }
  }
}
