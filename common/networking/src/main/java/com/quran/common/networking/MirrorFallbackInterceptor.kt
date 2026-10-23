package com.quran.common.networking

import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * Downloads Mushaf files from upstream first; when that fails (no connection, or an HTTP error
 * other than 416, which only means a partial download is already complete), the same request is
 * sent to the Tasnim server's copy. If the copy fails too, the upstream result is kept.
 * Requests with no copy on the Tasnim server (see [MirrorFallback]) are left alone.
 *
 * After an upstream host fails, the copy is asked first for a few minutes, so a blocked or down
 * host does not cost a full connect timeout on every page.
 */
class MirrorFallbackInterceptor(
  private val tasnimBase: String,
  private val now: () -> Long = System::currentTimeMillis
) : Interceptor {
  private val hostDownSince = ConcurrentHashMap<String, Long>()

  override fun intercept(chain: Interceptor.Chain): Response {
    val request = chain.request()
    val mirrorUrl = MirrorFallback.fallbackUrl(request.url, tasnimBase)
      ?: return chain.proceed(request)

    val host = request.url.host
    if (isDown(host)) {
      tryUrl(chain, request.newBuilder().url(mirrorUrl).build())?.let { return it }
    }

    val upstream = try {
      chain.proceed(request)
    } catch (e: IOException) {
      if (chain.call().isCanceled()) throw e
      hostDownSince[host] = now()
      return tryMirror(chain, mirrorUrl, upstreamFailure = e) ?: throw e
    }
    if (upstream.code < 400 || upstream.code == HTTP_RANGE_NOT_SATISFIABLE) {
      hostDownSince.remove(host)
      return upstream
    }
    if (upstream.code >= 500) hostDownSince[host] = now()

    // keep a small copy of the error so it can still be returned if the mirror fails as well
    val kept = upstream.newBuilder().body(upstream.peekBody(MAX_KEPT_ERROR_BYTES)).build()
    upstream.close()
    return tryMirror(chain, mirrorUrl, upstreamFailure = null) ?: kept
  }

  private fun isDown(host: String): Boolean {
    val since = hostDownSince[host] ?: return false
    if (now() - since < HOST_DOWN_MS) return true
    hostDownSince.remove(host, since)
    return false
  }

  private fun tryMirror(
    chain: Interceptor.Chain,
    mirrorUrl: HttpUrl,
    upstreamFailure: IOException?
  ): Response? {
    Timber.d("upstream failed for %s, trying %s", chain.request().url, mirrorUrl)
    return try {
      tryUrl(chain, chain.request().newBuilder().url(mirrorUrl).build())
    } catch (e: IOException) {
      upstreamFailure?.addSuppressed(e)
      null
    }
  }

  /** The response when it is usable, else null (closed). */
  private fun tryUrl(chain: Interceptor.Chain, request: Request): Response? {
    val response = try {
      chain.proceed(request)
    } catch (e: IOException) {
      if (chain.call().isCanceled()) throw e
      return null
    }
    if (response.isSuccessful || response.code == HTTP_RANGE_NOT_SATISFIABLE) return response
    response.close()
    return null
  }

  private companion object {
    const val HTTP_RANGE_NOT_SATISFIABLE = 416
    const val MAX_KEPT_ERROR_BYTES = 64L * 1024
    const val HOST_DOWN_MS = 5 * 60 * 1000L
  }
}
