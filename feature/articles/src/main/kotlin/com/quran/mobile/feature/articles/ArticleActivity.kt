package com.quran.mobile.feature.articles

import android.content.Context
import android.content.Intent
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
import com.quran.mobile.feature.articles.data.Article
import com.quran.mobile.feature.articles.data.ArticlesApi
import com.quran.mobile.feature.articles.data.ArticlesApiException
import com.quran.mobile.feature.articles.data.ArticlesRepository
import com.quran.mobile.feature.articles.di.ArticlesComponentInterface
import com.quran.mobile.feature.articles.ui.ArticleScreen
import com.quran.mobile.feature.articles.ui.ArticleState
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException

/** One article, shown with the same formatting as in the control panel and on the website. */
class ArticleActivity : AppCompatActivity() {

  @Inject
  lateinit var api: ArticlesApi

  private lateinit var repository: ArticlesRepository
  private var state by mutableStateOf<ArticleState>(ArticleState.Loading)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val injector = (application as? QuranApplicationComponentProvider)
      ?.provideQuranApplicationComponent() as? ArticlesComponentInterface
    injector?.articlesComponentFactory()?.generate()?.inject(this)
    repository = ArticlesRepository(api, File(filesDir, "articles"))

    val id = intent.getLongExtra(EXTRA_ID, -1)
    enableEdgeToEdge()
    setContent {
      QuranTheme {
        ArticleScreen(
          state = state,
          onShare = ::share,
          onRetry = { load(id) },
          onBack = onBackPressedDispatcher::onBackPressed
        )
      }
    }
    load(id)
  }

  private fun load(id: Long) {
    state = ArticleState.Loading
    lifecycleScope.launch {
      state = try {
        val (article, offline) = repository.article(id)
        ArticleState.Loaded(article, offline)
      } catch (_: ArticlesApiException.NotFound) {
        ArticleState.NotFound
      } catch (_: ArticlesApiException) {
        ArticleState.Error
      } catch (_: IOException) {
        ArticleState.Error
      }
    }
  }

  /** Shares the title and summary as text (the website link comes once the domain is set). */
  private fun share(article: Article) {
    val text = listOf(article.title, article.summary).filter { it.isNotBlank() }.joinToString("\n\n")
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    startActivity(Intent.createChooser(send, null))
  }

  companion object {
    private const val EXTRA_ID = "article_id"

    fun intent(context: Context, id: Long): Intent =
      Intent(context, ArticleActivity::class.java).putExtra(EXTRA_ID, id)
  }
}
