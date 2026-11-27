package com.quran.mobile.feature.articles.ui

import com.quran.mobile.feature.articles.data.Article

/**
 * The page the WebView shows: the article's HTML exactly as written in the control panel's
 * editor (already cleaned on the server), inside the same `.article-body` rules as the panel and
 * the website (assets/articles/article.css). Title, byline and summary are plain text, escaped.
 */
object ArticleHtml {
  /** Base URL for the page, so `article.css` and the bundled font load from the app. */
  const val BASE_URL = "file:///android_asset/articles/"

  fun page(article: Article, byline: String, dark: Boolean): String = buildString {
    append("<!doctype html><html lang=\"fa\" dir=\"rtl\"")
    if (dark) append(" class=\"dark\"")
    append("><head><meta charset=\"utf-8\">")
    append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
    append("<link rel=\"stylesheet\" href=\"article.css\"></head><body>")
    article.coverUrl?.let { append("<img class=\"cover\" alt=\"\" src=\"").append(escape(it)).append("\">") }
    append("<h1 class=\"title\">").append(escape(article.title)).append("</h1>")
    append("<div class=\"byline\">").append(escape(byline)).append("</div>")
    if (article.summary.isNotBlank()) {
      append("<p class=\"summary\">").append(escape(article.summary)).append("</p>")
    }
    append("<div class=\"article-body\">").append(article.html).append("</div>")
    append("</body></html>")
  }

  fun escape(text: String): String = buildString(text.length) {
    for (c in text) {
      when (c) {
        '&' -> append("&amp;")
        '<' -> append("&lt;")
        '>' -> append("&gt;")
        '"' -> append("&quot;")
        '\'' -> append("&#39;")
        else -> append(c)
      }
    }
  }
}
