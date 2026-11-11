package com.quran.common.networking

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * A server started on a computer names its files `http://localhost:3000/...`, which a phone or
 * emulator cannot open; such links are pointed at the address the app used to reach the server.
 * Real hosts are left as they are.
 */
object ReachableUrl {
  private val LOCAL_HOSTS = setOf("localhost", "127.0.0.1", "0.0.0.0")

  fun of(url: String, server: HttpUrl?): String {
    if (server == null) return url
    val parsed = url.toHttpUrlOrNull() ?: return url
    if (parsed.host !in LOCAL_HOSTS) return url
    return parsed.newBuilder()
      .scheme(server.scheme)
      .host(server.host)
      .port(server.port)
      .build()
      .toString()
  }
}
