package com.quran.mobile.feature.articles

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import com.quran.mobile.di.QuranApplicationComponentProvider
import com.quran.mobile.feature.articles.data.ArticleImages
import com.quran.mobile.feature.articles.data.ArticlesApi
import com.quran.mobile.feature.articles.data.ArticlesApiException
import com.quran.mobile.feature.articles.data.ArticlesRepository
import com.quran.mobile.feature.articles.di.ArticlesComponentInterface
import com.quran.mobile.feature.articles.ui.ArticlesScreen
import com.quran.mobile.feature.articles.ui.ArticlesState
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException

/** The list of articles published in the control panel (ADR 0016), newest first. */
class ArticlesActivity : AppCompatActivity() {

  @Inject
  lateinit var api: ArticlesApi

  @Inject
  lateinit var images: ArticleImages

  private lateinit var repository: ArticlesRepository
  private var state by mutableStateOf<ArticlesState>(ArticlesState.Loading)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val injector = (application as? QuranApplicationComponentProvider)
      ?.provideQuranApplicationComponent() as? ArticlesComponentInterface
    injector?.articlesComponentFactory()?.generate()?.inject(this)
    repository = ArticlesRepository(api, File(filesDir, "articles"))

    enableEdgeToEdge()
    setContent {
      QuranTheme {
        ArticlesScreen(
          state = state,
          loadCover = { images.load(this, it) },
          onOpen = { startActivity(ArticleActivity.intent(this, it.id)) },
          onMore = ::loadMore,
          onRetry = ::load,
          onBack = onBackPressedDispatcher::onBackPressed
        )
      }
    }
    load()
  }

  private fun load() {
    state = ArticlesState.Loading
    lifecycleScope.launch {
      state = try {
        val (page, offline) = repository.firstPage()
        ArticlesState.Loaded(page.items, page.next, offline)
      } catch (_: IOException) {
        ArticlesState.Error
      } catch (_: ArticlesApiException) {
        ArticlesState.Error
      }
    }
  }

  private fun loadMore() {
    val loaded = state as? ArticlesState.Loaded ?: return
    val next = loaded.next ?: return
    if (loaded.loadingMore) return
    state = loaded.copy(loadingMore = true)
    lifecycleScope.launch {
      state = try {
        val page = repository.olderPage(next)
        loaded.copy(items = loaded.items + page.items, next = page.next, loadingMore = false)
      } catch (_: IOException) {
        loaded.copy(loadingMore = false)
      } catch (_: ArticlesApiException) {
        loaded.copy(loadingMore = false)
      }
    }
  }
}
