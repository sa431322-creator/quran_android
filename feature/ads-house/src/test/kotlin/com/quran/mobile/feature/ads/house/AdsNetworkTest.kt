package com.quran.mobile.feature.ads.house

import android.graphics.Bitmap
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AdsNetworkTest {
  @get:Rule val tmp = TemporaryFolder()
  private lateinit var server: MockWebServer
  private var now = 1_800_000_000_000L

  @Before
  fun setUp() {
    server = MockWebServer()
    server.start()
  }

  @After
  fun tearDown() = server.shutdown()

  private fun base() = server.url("/").toString()
  private fun repo() = AdsRepository(OkHttpClient(), base(), tmp.root.resolve("data"), tmp.root.resolve("img")) { now }

  private fun answer(host: String = server.hostName) = """
    {"slots":{"live_tv":[{"id":7,"imageUrl":"http://$host:${server.port}/files/ads/7-a.webp","width":1280,
      "height":200,"altText":"x","actionUrl":null,"actionText":null,"weight":10,
      "startsAt":"2020-01-01T00:00:00.000Z","endsAt":"2099-01-01T00:00:00.000Z"}],
     "radio":[],"library_list":[],"library_details":[]},"refreshAfterSeconds":900}
  """.trimIndent()

  @Test
  fun sendsTheSavedETagAndUsesTheSavedAnswerOn304() = runTest {
    server.enqueue(MockResponse().setBody(answer()).setHeader("ETag", "\"v1\""))
    server.enqueue(MockResponse().setResponseCode(304))
    val repo = repo()
    assertThat(repo.fetch(3700).ads.map { it.id }).containsExactly(7)
    val first = server.takeRequest()
    assertThat(first.path).isEqualTo("/api/v1/ads?versionCode=3700&platform=android")
    assertThat(first.getHeader("If-None-Match")).isNull()

    assertThat(repo.fetch(3700).ads.map { it.id }).containsExactly(7)
    assertThat(server.takeRequest().getHeader("If-None-Match")).isEqualTo("\"v1\"")
  }

  @Test
  fun keepsTheSavedAnswerOfflineForAtMostADay() = runTest {
    server.enqueue(MockResponse().setBody(answer()))
    val repo = repo()
    repo.fetch(1)
    server.enqueue(MockResponse().setResponseCode(503))
    assertThat(repo.fetch(1).ads).hasSize(1)
    now += AdsRepository.MAX_AGE_MS + 1
    server.enqueue(MockResponse().setResponseCode(503))
    assertThat(repo.fetch(1).ads).isEmpty()
  }

  @Test
  fun downloadsAPictureOnceAndRefusesBrokenOnes() = runTest {
    server.enqueue(MockResponse().setBody(answer()))
    val repo = repo()
    val ad = repo.fetch(1).ads.single()
    server.enqueue(MockResponse().setBody(Buffer().write(png())))
    val file = repo.image(ad, mayDownload = true)
    assertThat(file).isNotNull()
    // second time: from disk, no request
    val count = server.requestCount
    assertThat(repo.image(ad, mayDownload = true)).isEqualTo(file)
    assertThat(server.requestCount).isEqualTo(count)

    file!!.delete()
    server.enqueue(MockResponse().setBody("not a picture"))
    assertThat(repo.image(ad, mayDownload = true)).isNull()
    assertThat(repo.image(ad, mayDownload = false)).isNull()
  }

  @Test
  fun eventsAreRetriedOnlyOnServerOrNetworkTrouble() = runTest {
    val sender = AdEventsSender(OkHttpClient(), base())
    val batch = PendingBatch("b-1", listOf(CountRow(7, "live_tv", "2026-11-07", 3, 1)))
    for ((code, expected) in listOf(
      204 to SendResult.DONE, 400 to SendResult.DONE, 413 to SendResult.DONE,
      429 to SendResult.RETRY, 503 to SendResult.RETRY,
    )) {
      server.enqueue(MockResponse().setResponseCode(code))
      assertThat(sender.send(batch)).isEqualTo(expected)
    }
    val req = server.takeRequest()
    assertThat(req.path).isEqualTo("/api/v1/ads/events")
    assertThat(req.body.readUtf8()).contains("\"batchId\":\"b-1\"")
  }

  @Test
  fun aPendingBatchKeepsItsIdUntilFinished() = runTest {
    val store = AdCountStore(tmp.root.resolve("counts"))
    store.add(7, "live_tv", "2026-11-07", 1, 0)
    store.add(7, "live_tv", "2026-11-07", 1, 1)
    val first = store.nextBatch("2026-11-07")!!
    assertThat(first.rows).containsExactly(CountRow(7, "live_tv", "2026-11-07", 2, 1))
    // a retry gets the same batch even after new counts
    store.add(7, "live_tv", "2026-11-07", 1, 0)
    assertThat(store.nextBatch("2026-11-07")!!.batchId).isEqualTo(first.batchId)
    store.finish(first.batchId)
    val second = store.nextBatch("2026-11-07")!!
    assertThat(second.batchId).isNotEqualTo(first.batchId)
    assertThat(second.rows.single().impressions).isEqualTo(1)
  }

  private fun png(): ByteArray {
    val bmp = Bitmap.createBitmap(4, 2, Bitmap.Config.ARGB_8888)
    return ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
  }
}
