package com.quran.labs.androidquran.tasnim

import com.google.common.truth.Truth.assertThat
import com.quran.labs.androidquran.core.worker.di.AudioUpdateModule
import com.quran.labs.androidquran.data.Constants
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Test

class TasnimDataHostTest {

  private val server = MockWebServer()

  @After
  fun tearDown() {
    server.shutdown()
  }

  @Test
  fun `blank base url keeps quran app`() {
    assertThat(TasnimDataHost.of("")).isEqualTo(Constants.HOST)
    assertThat(TasnimDataHost.of("  ")).isEqualTo(Constants.HOST)
  }

  @Test
  fun `tasnim base url always ends with a slash`() {
    assertThat(TasnimDataHost.of("http://10.0.2.2:3000")).isEqualTo("http://10.0.2.2:3000/")
    assertThat(TasnimDataHost.of("http://10.0.2.2:3000/")).isEqualTo("http://10.0.2.2:3000/")
  }

  private fun audioService() =
    AudioUpdateModule.provideAudioUpdateService(server.url("/").toString())

  @Test
  fun `audio updates are read from the tasnim server`() = runTest {
    server.enqueue(
      MockResponse().setBody(
        """{"current_revision":41,"updates":[{"path":"https://download.quranicaudio.com/quran/x/",""" +
          """"database_version":7,"files":[{"filename":"007.mp3","md5":"abc"}]}]}"""
      )
    )

    val response = audioService().getUpdates(40)

    assertThat(server.takeRequest().path).isEqualTo("/data/audio_updates.php?revision=40")
    val body = response.body()!!
    assertThat(body.currentRevision).isEqualTo(41)
    assertThat(body.updates.single().files.single().md5sum).isEqualTo("abc")
  }

  @Test
  fun `up to date (204) and not synced yet (503) give no updates`() = runTest {
    server.enqueue(MockResponse().setResponseCode(204))
    server.enqueue(MockResponse().setResponseCode(503).setBody("""{"error":{}}"""))

    val upToDate = audioService().getUpdates(41)
    assertThat(upToDate.isSuccessful).isTrue()
    assertThat(upToDate.body()).isNull()

    val notSynced = audioService().getUpdates(0)
    assertThat(notSynced.isSuccessful).isFalse()
    assertThat(notSynced.body()).isNull()
  }
}
