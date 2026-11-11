package com.quran.common.networking

import com.google.common.truth.Truth.assertThat
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.net.InetAddress
import java.util.concurrent.TimeUnit

class MirrorFallbackInterceptorTest {
  private val upstream = MockWebServer()
  private val mirror = MockWebServer()

  @Before
  fun setUp() {
    upstream.start()
    mirror.start()
  }

  @After
  fun tearDown() {
    upstream.shutdown()
    mirror.shutdown()
  }

  /** A client whose android.quran.com is [upstreamServer], with the Tasnim server at [mirror]. */
  private fun client(upstreamServer: MockWebServer = upstream): OkHttpClient {
    val upstreamAddress = InetAddress.getByName(upstreamServer.hostName)
    return OkHttpClient.Builder()
      .dns { host -> if (host == "android.quran.com") listOf(upstreamAddress) else Dns.SYSTEM.lookup(host) }
      .addInterceptor(MirrorFallbackInterceptor(mirror.url("/").toString()))
      .build()
  }

  private fun pageRequest(port: Int = upstream.port) = Request.Builder()
    .url("http://android.quran.com:$port/data/width_1260/page001.png")
    .header("Range", "bytes=10-")
    .build()

  @Test
  fun `upstream error is served from the mirror`() {
    upstream.enqueue(MockResponse().setResponseCode(500))
    mirror.enqueue(MockResponse().setResponseCode(206).setBody("page"))

    client().newCall(pageRequest()).execute().use { response ->
      assertThat(response.code).isEqualTo(206)
      assertThat(response.body.string()).isEqualTo("page")
    }

    val mirrorRequest = mirror.takeRequest()
    assertThat(mirrorRequest.path).isEqualTo("/data/width_1260/page001.png")
    assertThat(mirrorRequest.method).isEqualTo("GET")
    assertThat(mirrorRequest.getHeader("Range")).isEqualTo("bytes=10-")
  }

  @Test
  fun `upstream success never calls the mirror`() {
    upstream.enqueue(MockResponse().setBody("upstream page"))

    client().newCall(pageRequest()).execute().use { response ->
      assertThat(response.code).isEqualTo(200)
      assertThat(response.body.string()).isEqualTo("upstream page")
    }
    assertThat(mirror.requestCount).isEqualTo(0)
  }

  @Test
  fun `range not satisfiable is not retried`() {
    upstream.enqueue(MockResponse().setResponseCode(416))

    client().newCall(pageRequest()).execute().use { response ->
      assertThat(response.code).isEqualTo(416)
    }
    assertThat(mirror.requestCount).isEqualTo(0)
  }

  @Test
  fun `when both fail the upstream response is kept`() {
    upstream.enqueue(MockResponse().setResponseCode(404).setBody("not on upstream"))
    mirror.enqueue(MockResponse().setResponseCode(503))

    client().newCall(pageRequest()).execute().use { response ->
      assertThat(response.code).isEqualTo(404)
      assertThat(response.body.string()).isEqualTo("not on upstream")
    }
    assertThat(mirror.requestCount).isEqualTo(1)
  }

  @Test
  fun `connection failure upstream is served from the mirror`() {
    val dead = MockWebServer().apply { start() }
    val deadPort = dead.port
    dead.shutdown()
    mirror.enqueue(MockResponse().setBody("page"))

    client().newCall(pageRequest(deadPort)).execute().use { response ->
      assertThat(response.code).isEqualTo(200)
      assertThat(response.body.string()).isEqualTo("page")
    }
    assertThat(mirror.takeRequest(1, TimeUnit.SECONDS)!!.path)
      .isEqualTo("/data/width_1260/page001.png")
  }

  @Test
  fun `connection failure everywhere rethrows`() {
    val dead = MockWebServer().apply { start() }
    val deadPort = dead.port
    dead.shutdown()
    mirror.enqueue(MockResponse().setResponseCode(500))

    assertThrows(IOException::class.java) {
      client().newCall(pageRequest(deadPort)).execute()
    }
  }

  @Test
  fun `requests with no copy are left alone`() {
    mirror.enqueue(MockResponse().setResponseCode(500))
    val request = Request.Builder().url(mirror.url("/data/zips/images_1260.zip")).build()

    client().newCall(request).execute().use { response ->
      assertThat(response.code).isEqualTo(500)
    }
    assertThat(mirror.requestCount).isEqualTo(1)
  }

  @Test
  fun `after an upstream failure the mirror is asked first for a while`() {
    val client = client()
    upstream.enqueue(MockResponse().setResponseCode(503))
    mirror.enqueue(MockResponse().setBody("first"))
    mirror.enqueue(MockResponse().setBody("second"))

    client.newCall(pageRequest()).execute().use { assertThat(it.body.string()).isEqualTo("first") }
    client.newCall(pageRequest()).execute().use { assertThat(it.body.string()).isEqualTo("second") }

    // the second page went straight to the mirror
    assertThat(upstream.requestCount).isEqualTo(1)
    assertThat(mirror.requestCount).isEqualTo(2)
  }

  @Test
  fun `a down host is tried again when the mirror fails`() {
    val client = client()
    upstream.enqueue(MockResponse().setResponseCode(503))
    mirror.enqueue(MockResponse().setBody("first"))
    mirror.enqueue(MockResponse().setResponseCode(404))
    upstream.enqueue(MockResponse().setBody("upstream again"))

    client.newCall(pageRequest()).execute().close()
    client.newCall(pageRequest()).execute().use {
      assertThat(it.body.string()).isEqualTo("upstream again")
    }
  }

  @Test
  fun `a 404 upstream does not mark the host as down`() {
    val client = client()
    upstream.enqueue(MockResponse().setResponseCode(404))
    mirror.enqueue(MockResponse().setBody("copy"))
    upstream.enqueue(MockResponse().setBody("upstream"))

    client.newCall(pageRequest()).execute().close()
    client.newCall(pageRequest()).execute().use {
      assertThat(it.body.string()).isEqualTo("upstream")
    }
  }
}
