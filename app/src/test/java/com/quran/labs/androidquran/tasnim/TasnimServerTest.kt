package com.quran.labs.androidquran.tasnim

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Config(application = Application::class, sdk = [33])
@RunWith(RobolectricTestRunner::class)
class TasnimServerTest {
  private val server = MockWebServer()
  private lateinit var context: Context

  @Before
  fun setUp() {
    server.start()
    context = ApplicationProvider.getApplicationContext()
    context.getSharedPreferences(TasnimConfigRepository.PREFS, Context.MODE_PRIVATE).edit().clear().commit()
  }

  @After
  fun tearDown() {
    server.shutdown()
  }

  private fun repo(baseUrl: String = server.url("/").toString()) =
    TasnimConfigRepository(context, OkHttpClient(), baseUrl)

  private fun reply(code: Int, body: String) {
    server.enqueue(MockResponse().setResponseCode(code).setBody(body))
  }

  // Exactly what tasneem-backend's GET /api/v1/app/config returns.
  private val config = """
    {"links":{"website":"https://tasnim.example","email":"mailto:hi@tasnim.example","videoChannel":null,
      "messenger":"https://t.me/tasnim"},
     "features":{"library":true,"live":true,"radio":false},
     "maintenance":{"enabled":true,"message":"در حال به‌روزرسانی"},
     "update":{"minVersionCode":100,"latestVersionCode":120,"url":"https://tasnim.example/app","notes":"تازه"}}
  """.trimIndent()

  @Test
  fun `reads the settings, saves them, and uses the saved copy when offline`() = runTest {
    reply(200, config)
    val fresh = repo().refreshConfig()
    assertThat(server.takeRequest().path).isEqualTo("/api/v1/app/config")
    assertThat(fresh.links.messenger).isEqualTo("https://t.me/tasnim")
    assertThat(fresh.features.live).isTrue()
    assertThat(fresh.maintenance.message).isEqualTo("در حال به‌روزرسانی")
    assertThat(fresh.update.minVersionCode).isEqualTo(100)

    reply(503, "")
    val offline = repo().refreshConfig()
    assertThat(offline).isEqualTo(fresh)
    assertThat(repo().cached()).isEqualTo(fresh)
  }

  @Test
  fun `missing fields and a broken answer fall back to the defaults`() = runTest {
    reply(200, """{"links":{"website":"https://x.example"}}""")
    val partial = repo().refreshConfig()
    assertThat(partial.links.website).isEqualTo("https://x.example")
    assertThat(partial.features).isEqualTo(AppFeatures(library = true, live = false, radio = false))

    context.getSharedPreferences(TasnimConfigRepository.PREFS, Context.MODE_PRIVATE).edit().clear().commit()
    reply(200, "not json")
    assertThat(repo().refreshConfig()).isEqualTo(AppConfig())
  }

  @Test
  fun `without a server address nothing is requested and defaults are used`() = runTest {
    val noServer = repo(baseUrl = "")
    assertThat(noServer.refreshConfig()).isEqualTo(AppConfig())
    assertThat(noServer.announcements(10)).isEmpty()
    assertThat(server.requestCount).isEqualTo(0)
  }

  @Test
  fun `live and radio follow the server switches, and are on when there is no server`() = runTest {
    val noServer = TasnimLiveAvailability(repo(baseUrl = ""))
    assertThat(noServer.isLiveAvailable()).isTrue()
    assertThat(noServer.isRadioAvailable()).isTrue()

    val withServer = TasnimLiveAvailability(repo())
    assertThat(withServer.isLiveAvailable()).isFalse()
    assertThat(withServer.isRadioAvailable()).isFalse()

    reply(200, config)
    repo().refreshConfig()
    assertThat(withServer.isLiveAvailable()).isTrue()
    assertThat(withServer.isRadioAvailable()).isFalse()
  }

  @Test
  fun `asks for this version's announcements and remembers which were seen`() = runTest {
    reply(
      200,
      """{"items":[{"id":7,"title":"کتاب‌های تازه","body":"…","items":[{"icon":"library","title":"کتابخانه","body":""}],
        "imageUrl":null,"actionText":"باز کردن","actionUrl":"https://x.example","startsAt":null,"endsAt":null},
        {"id":8,"title":"دوم"}]}"""
    )
    val r = repo()
    val list = r.announcements(versionCode = 123)
    assertThat(server.takeRequest().path).isEqualTo("/api/v1/announcements?versionCode=123&platform=android")
    assertThat(list.map { it.id }).containsExactly(7, 8).inOrder()
    assertThat(list[0].items.single().icon).isEqualTo("library")

    assertThat(firstUnseen(list, r.seenAnnouncements())?.id).isEqualTo(7)
    r.markSeen(7)
    assertThat(firstUnseen(list, repo().seenAnnouncements())?.id).isEqualTo(8)
    r.markSeen(8)
    assertThat(firstUnseen(list, repo().seenAnnouncements())).isNull()

    reply(500, "")
    assertThat(r.announcements(123)).isEmpty()
  }

  @Test
  fun `update rules`() {
    val u = AppUpdate(minVersionCode = 100, latestVersionCode = 120)
    assertThat(updateNeed(u, versionCode = 99, dismissedLatest = null)).isEqualTo(UpdateNeed.REQUIRED)
    assertThat(updateNeed(u, versionCode = 99, dismissedLatest = 120)).isEqualTo(UpdateNeed.REQUIRED)
    assertThat(updateNeed(u, versionCode = 110, dismissedLatest = null)).isEqualTo(UpdateNeed.SUGGESTED)
    assertThat(updateNeed(u, versionCode = 110, dismissedLatest = 120)).isEqualTo(UpdateNeed.NONE)
    assertThat(updateNeed(u, versionCode = 110, dismissedLatest = 115)).isEqualTo(UpdateNeed.SUGGESTED)
    assertThat(updateNeed(u, versionCode = 120, dismissedLatest = null)).isEqualTo(UpdateNeed.NONE)
    assertThat(updateNeed(AppUpdate(), versionCode = 1, dismissedLatest = null)).isEqualTo(UpdateNeed.NONE)
  }

  @Test
  fun `a dismissed suggestion is remembered per latest version`() {
    val r = repo()
    assertThat(r.dismissedUpdate()).isNull()
    r.dismissUpdate(120)
    assertThat(repo().dismissedUpdate()).isEqualTo(120)
  }
}
