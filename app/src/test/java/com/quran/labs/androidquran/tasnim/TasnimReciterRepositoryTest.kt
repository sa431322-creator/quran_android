package com.quran.labs.androidquran.tasnim

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

@Config(application = Application::class, sdk = [33])
@RunWith(RobolectricTestRunner::class)
class TasnimReciterRepositoryTest {
  private val server = MockWebServer()
  private lateinit var context: Context

  @Before
  fun setUp() {
    server.start()
    context = ApplicationProvider.getApplicationContext()
    prefs().edit().clear().commit()
  }

  @After
  fun tearDown() {
    server.shutdown()
  }

  private fun prefs() =
    context.getSharedPreferences(TasnimConfigRepository.PREFS, Context.MODE_PRIVATE)

  private fun repo(language: String = "fa", withServer: Boolean = true) = TasnimReciterRepository(
    if (withServer) Retrofit.Builder()
      .baseUrl(server.url("/"))
      .addConverterFactory(MoshiConverterFactory.create())
      .build()
      .create(TasnimReciterService::class.java) else null,
    prefs(),
    { language }
  )

  // The shape of tasneem-backend's GET /api/v1/reciters.
  private val list = """
    {"version":3,"items":[
      {"id":0,"names":{"fa":"المنشاوي","ar":"المنشاوي","en":"Minshawi Murattal"},
       "url":"https://download.quranicaudio.com/quran/muhammad_siddeeq_al-minshaawee/",
       "path":"minshawi_murattal","db":"minshawi_murattal","hasGaplessAlternative":false},
      {"id":1000,"names":{"fa":null,"ar":null,"en":"Sahl Yassin"},
       "url":"https://everyayah.com/data/Sahl_Yassin_128kbps/","path":"Sahl_Yassin_128kbps",
       "db":null,"hasGaplessAlternative":false},
      {"id":1001,"names":{"fa":"بدون پوشه","ar":null,"en":"No folder"},
       "url":"https://everyayah.com/data/x/","path":"","db":null,"hasGaplessAlternative":false}
    ]}
  """.trimIndent()

  @Test
  fun `saves the list for the next start and turns it into qaris`() = runTest {
    server.enqueue(MockResponse().setBody(list))
    assertThat(repo().refresh()).isTrue()
    assertThat(server.takeRequest().path).isEqualTo("/api/v1/reciters")

    // A new process (new repository) reads the saved list.
    val qaris = repo().cached()!!
    assertThat(qaris.map { it.id }).containsExactly(0, 1000).inOrder()
    assertThat(qaris[0].name).isEqualTo("المنشاوي")
    assertThat(qaris[0].isGapless).isTrue()
    // No Persian name: English is used; gapped = no timing database.
    assertThat(qaris[1].name).isEqualTo("Sahl Yassin")
    assertThat(qaris[1].isGapless).isFalse()
  }

  @Test
  fun `the list in use does not change until the next start`() = runTest {
    val current = repo()
    assertThat(current.cached()).isNull()
    server.enqueue(MockResponse().setBody(list))
    assertThat(current.refresh()).isTrue()
    assertThat(current.cached()).isNull()
    assertThat(repo().cached()).hasSize(2)
  }

  @Test
  fun `keeps the saved list when the server fails`() = runTest {
    server.enqueue(MockResponse().setBody(list))
    repo().refresh()
    server.enqueue(MockResponse().setResponseCode(500))
    assertThat(repo().refresh()).isFalse()
    assertThat(repo().cached()).hasSize(2)
  }

  @Test
  fun `without a server the built-in list is used`() = runTest {
    prefs().edit().putString(TasnimReciterRepository.KEY_RECITERS, list).commit()
    val none = repo(withServer = false)
    assertThat(none.refresh()).isFalse()
    assertThat(none.cached()).isNull()
  }

  @Test
  fun `picks the name for the device language`() {
    val names = ReciterNames(fa = "عبدالباسط عبدالصمد", ar = "عبد الباسط عبد الصمد", en = "Abd Al-Basit")
    assertThat(names.pick("fa")).isEqualTo("عبدالباسط عبدالصمد")
    assertThat(names.pick("ar")).isEqualTo("عبد الباسط عبد الصمد")
    assertThat(names.pick("en")).isEqualTo("Abd Al-Basit")
    assertThat(ReciterNames(fa = null, ar = "علي جابر", en = "Ali Jaber").pick("fa")).isEqualTo("علي جابر")
  }
}
