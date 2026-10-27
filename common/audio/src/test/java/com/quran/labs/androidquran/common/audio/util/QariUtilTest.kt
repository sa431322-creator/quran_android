package com.quran.labs.androidquran.common.audio.util

import com.google.common.truth.Truth.assertThat
import com.quran.data.model.audio.Qari
import com.quran.data.source.PageProvider
import org.junit.Test
import java.lang.reflect.Proxy

class QariUtilTest {

  private fun qari(id: Int, name: String? = null) =
    Qari(id, nameResource = 0, url = "https://a.test/$id/", path = "q$id", hasGaplessAlternative = false, name = name)

  private val builtIn = listOf(qari(0), qari(1))

  /** A page provider that only answers getQaris(). */
  private val pageProvider = Proxy.newProxyInstance(
    PageProvider::class.java.classLoader,
    arrayOf(PageProvider::class.java)
  ) { _, method, _ ->
    if (method.name == "getQaris") builtIn else throw NotImplementedError(method.name)
  } as PageProvider

  @Test
  fun `uses the server list when there is one`() {
    val server = listOf(qari(100, "عبدالباسط"))
    assertThat(QariUtil(pageProvider) { server }.getQariList()).isEqualTo(server)
  }

  @Test
  fun `falls back to the built-in list without a server list`() {
    assertThat(QariUtil(pageProvider, ServerQariSource.None).getQariList()).isEqualTo(builtIn)
    assertThat(QariUtil(pageProvider) { emptyList() }.getQariList()).isEqualTo(builtIn)
  }
}
