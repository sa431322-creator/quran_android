package com.quran.mobile.feature.ads.house

import android.graphics.BitmapFactory
import android.util.AtomicFile
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * `GET /api/v1/ads` with the last good answer kept in filesDir/ads (24 h at most) and pictures
 * kept in cacheDir/ads. The ETag is stored by hand (the shared client has no HTTP cache).
 */
class AdsRepository(
  private val client: OkHttpClient,
  private val baseUrl: String,
  private val dataDir: File,
  private val imageDir: File,
  private val now: () -> Long = System::currentTimeMillis,
) {
  private val answerFile = AtomicFile(File(dataDir, "answer.json"))
  private val metaFile = AtomicFile(File(dataDir, "answer-meta.json"))
  private val metaAdapter = Moshi.Builder().build().adapter(Map::class.java)
  private val apiHost = baseUrl.toHttpUrlOrNull()?.host.orEmpty()

  init {
    dataDir.mkdirs()
    imageDir.mkdirs()
  }

  /** The saved answer while it is younger than 24 h, else nothing. */
  fun cached(): ServedAds {
    val meta = readMeta()
    val fetchedAt = (meta["fetchedAt"] as? Number)?.toLong() ?: return ServedAds.EMPTY
    if (now() - fetchedAt > MAX_AGE_MS) return ServedAds.EMPTY
    val json = runCatching { String(answerFile.readFully()) }.getOrNull() ?: return ServedAds.EMPTY
    return ServedAds.parse(json, apiHost)
  }

  /** Asks the server (sending the saved ETag); on any failure keeps the saved answer. */
  suspend fun fetch(versionCode: Int): ServedAds = withContext(Dispatchers.IO) {
    val url = baseUrl.toHttpUrlOrNull()?.newBuilder()
      ?.addPathSegments("api/v1/ads")
      ?.addQueryParameter("versionCode", versionCode.toString())
      ?.addQueryParameter("platform", "android")
      ?.build() ?: return@withContext ServedAds.EMPTY
    val etag = readMeta()["etag"] as? String
    val request = Request.Builder().url(url).apply {
      if (etag != null && cachedRaw() != null) header("If-None-Match", etag)
    }.build()
    try {
      client.newCall(request).execute().use { res ->
        when {
          res.code == 304 -> {
            writeMeta(etag, now())
            cached()
          }
          res.isSuccessful -> {
            val body = res.body.string()
            write(answerFile, body)
            writeMeta(res.header("ETag"), now())
            ServedAds.parse(body, apiHost)
          }
          else -> cached()
        }
      }
    } catch (e: CancellationException) {
      throw e
    } catch (_: Exception) {
      cached()
    }
  }

  /** The picture on disk, downloading it once if [mayDownload]; null when it cannot be shown. */
  suspend fun image(ad: ServedAd, mayDownload: Boolean): File? = withContext(Dispatchers.IO) {
    val file = File(imageDir, "${ad.id}-${sha(ad.imageUrl).take(16)}.img")
    if (file.isFile && decodable(file)) return@withContext file
    if (!mayDownload) return@withContext null
    val tmp = File(imageDir, "${file.name}.part")
    try {
      client.newCall(Request.Builder().url(ad.imageUrl).build()).execute().use { res ->
        if (!res.isSuccessful) return@withContext null
        val bytes = res.body.byteStream().use { readAtMost(it, MAX_IMAGE_BYTES) } ?: return@withContext null
        tmp.writeBytes(bytes)
      }
      if (!decodable(tmp) || !tmp.renameTo(file)) {
        tmp.delete()
        return@withContext null
      }
      file
    } catch (e: CancellationException) {
      tmp.delete()
      throw e
    } catch (_: Exception) {
      tmp.delete()
      null
    }
  }

  /** One draft ad for a debug build (`/api/v1/ads/preview`); never saved, never counted. */
  suspend fun preview(token: String): ServedAds = withContext(Dispatchers.IO) {
    val url = baseUrl.toHttpUrlOrNull()?.newBuilder()
      ?.addPathSegments("api/v1/ads/preview")
      ?.addQueryParameter("token", token)
      ?.build() ?: return@withContext ServedAds.EMPTY
    try {
      client.newCall(Request.Builder().url(url).build()).execute().use { res ->
        if (res.isSuccessful) ServedAds.parse(res.body.string(), apiHost) else ServedAds.EMPTY
      }
    } catch (e: CancellationException) {
      throw e
    } catch (_: Exception) {
      ServedAds.EMPTY
    }
  }

  /** Removes pictures of ads the server no longer sends. */
  fun keepOnly(ads: List<ServedAd>) {
    val keep = ads.map { "${it.id}-${sha(it.imageUrl).take(16)}.img" }.toSet()
    imageDir.listFiles()?.filter { it.name !in keep }?.forEach { it.delete() }
  }

  fun clear() {
    answerFile.delete()
    metaFile.delete()
    imageDir.listFiles()?.forEach { it.delete() }
  }

  private fun cachedRaw() = runCatching { answerFile.readFully() }.getOrNull()

  private fun readMeta(): Map<*, *> =
    runCatching { metaAdapter.fromJson(String(metaFile.readFully())) }.getOrNull().orEmpty()

  private fun writeMeta(etag: String?, fetchedAt: Long) =
    write(metaFile, metaAdapter.toJson(mapOf("etag" to etag, "fetchedAt" to fetchedAt)))

  private fun write(file: AtomicFile, text: String) {
    val out = file.startWrite()
    try {
      out.write(text.toByteArray())
      file.finishWrite(out)
    } catch (e: Exception) {
      file.failWrite(out)
      throw e
    }
  }

  private fun decodable(file: File): Boolean {
    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, o)
    return o.outWidth > 0 && o.outHeight > 0
  }

  companion object {
    const val MAX_AGE_MS = 24 * 3_600_000L
    const val MAX_IMAGE_BYTES = 5 * 1024 * 1024

    /** The whole stream, or null when it is longer than [max] bytes. */
    fun readAtMost(input: java.io.InputStream, max: Int): ByteArray? {
      val out = java.io.ByteArrayOutputStream()
      val buf = ByteArray(16 * 1024)
      while (true) {
        val n = input.read(buf)
        if (n < 0) return out.toByteArray()
        if (out.size() + n > max) return null
        out.write(buf, 0, n)
      }
    }

    fun sha(text: String): String =
      MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
        .joinToString("") { "%02x".format(it) }
  }
}

/** Answer of one send: done (delete the batch) or try again later with the same id. */
enum class SendResult { DONE, RETRY }

/**
 * `POST /api/v1/ads/events`. 2xx, 400/422 (refused as invalid) and 413 → done, the batch is
 * dropped; 429, 5xx and network errors → retry with the same batch id.
 */
class AdEventsSender(private val client: OkHttpClient, private val baseUrl: String) {
  suspend fun send(batch: PendingBatch): SendResult = withContext(Dispatchers.IO) {
    val url = baseUrl.toHttpUrlOrNull()?.newBuilder()?.addPathSegments("api/v1/ads/events")?.build()
      ?: return@withContext SendResult.DONE
    val json = adapter.toJson(
      mapOf(
        "batchId" to batch.batchId,
        "events" to batch.rows.map {
          mapOf(
            "adId" to it.adId,
            "slot" to it.slot,
            "day" to it.day,
            "impressions" to it.impressions,
            "clicks" to it.clicks,
          )
        },
      )
    )
    try {
      client.newCall(
        Request.Builder().url(url).post(json.toRequestBody(JSON)).build()
      ).execute().use { res ->
        if (res.isSuccessful || res.code in DONE_CODES) SendResult.DONE else SendResult.RETRY
      }
    } catch (e: CancellationException) {
      throw e
    } catch (_: Exception) {
      SendResult.RETRY
    }
  }

  companion object {
    private val JSON = "application/json".toMediaType()
    /** The server answers 422 for a body it will never accept (zod validation). */
    private val DONE_CODES = setOf(400, 413, 422)
    private val adapter = Moshi.Builder().build().adapter(Map::class.java)

    /** A small client of its own for the worker, which may run before the app's graph exists. */
    fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
      .connectTimeout(15, TimeUnit.SECONDS)
      .readTimeout(15, TimeUnit.SECONDS)
      .build()
  }
}
