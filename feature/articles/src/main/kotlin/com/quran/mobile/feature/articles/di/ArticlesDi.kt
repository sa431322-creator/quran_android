package com.quran.mobile.feature.articles.di

import com.quran.data.constant.DependencyInjectionConstants
import com.quran.data.di.ActivityLevelScope
import com.quran.data.di.ActivityScope
import com.quran.data.di.AppScope
import com.quran.mobile.feature.articles.ArticleActivity
import com.quran.mobile.feature.articles.ArticlesActivity
import com.quran.mobile.feature.articles.data.ArticleImages
import com.quran.mobile.feature.articles.data.ArticlesApi
import com.quran.mobile.feature.articles.data.NoArticlesApi
import com.quran.mobile.feature.articles.data.RetrofitArticlesApi
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.GraphExtension
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import okhttp3.OkHttpClient

@BindingContainer
@ContributesTo(AppScope::class)
class ArticlesDataModule {

  /** The Tasnim server when the build names one; otherwise no articles (nothing to show yet). */
  @SingleIn(AppScope::class)
  @Provides
  fun provideArticlesApi(
    okHttpClient: OkHttpClient,
    @Named(DependencyInjectionConstants.TASNIM_API_BASE_URL) baseUrl: String
  ): ArticlesApi =
    if (baseUrl.isBlank()) NoArticlesApi() else RetrofitArticlesApi(okHttpClient, baseUrl)

  @SingleIn(AppScope::class)
  @Provides
  fun provideArticleImages(okHttpClient: OkHttpClient): ArticleImages = ArticleImages(okHttpClient)
}

@ActivityScope
@GraphExtension(ActivityLevelScope::class)
interface ArticlesComponent {
  fun inject(activity: ArticlesActivity)

  fun inject(activity: ArticleActivity)

  @GraphExtension.Factory
  interface Factory {
    fun generate(): ArticlesComponent
  }
}

@ContributesTo(AppScope::class)
interface ArticlesComponentInterface {
  fun articlesComponentFactory(): ArticlesComponent.Factory
}
