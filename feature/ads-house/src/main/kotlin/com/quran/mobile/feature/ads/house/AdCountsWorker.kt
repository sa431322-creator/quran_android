package com.quran.mobile.feature.ads.house

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Sends the saved ad counts. Built by WorkManager's default factory (the app's factory returns
 * null for workers it does not know), so it reads what it needs from disk: the server address
 * saved by [HouseAdProvider] and the counts in filesDir/ads.
 */
class AdCountsWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
  override suspend fun doWork(): Result {
    val prefs = AdsPrefs(applicationContext)
    val store = AdCountStore(countsDir(applicationContext))
    if (!prefs.sendCounts) {
      store.clear()
      return Result.success()
    }
    val baseUrl = prefs.baseUrl
    if (baseUrl.isBlank()) return Result.success()
    val sender = AdEventsSender(AdEventsSender.defaultClient(), baseUrl)
    val today = tehranDay(System.currentTimeMillis())
    store.dropPendingIfOld(today)
    repeat(MAX_BATCHES_PER_RUN) {
      val batch = store.nextBatch(today) ?: return Result.success()
      when (sender.send(batch)) {
        SendResult.DONE -> store.finish(batch.batchId)
        SendResult.RETRY -> return Result.retry()
      }
    }
    return Result.success()
  }

  companion object {
    private const val MAX_BATCHES_PER_RUN = 10
    private const val PERIODIC = "tasnim-ads-counts"
    private const val SOON = "tasnim-ads-counts-soon"

    fun countsDir(context: Context) = File(context.filesDir, "ads/counts")

    private val connected = Constraints.Builder()
      .setRequiredNetworkType(NetworkType.CONNECTED)
      .build()

    /** Every 6 hours while ads are on (kept if already scheduled). */
    fun schedulePeriodic(context: Context) {
      WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        PERIODIC,
        ExistingPeriodicWorkPolicy.KEEP,
        PeriodicWorkRequestBuilder<AdCountsWorker>(6, TimeUnit.HOURS).setConstraints(connected).build(),
      )
    }

    /** Once, ~10 minutes after new counts, so they are not held for 6 hours. */
    fun scheduleSoon(context: Context) {
      WorkManager.getInstance(context).enqueueUniqueWork(
        SOON,
        ExistingWorkPolicy.KEEP,
        OneTimeWorkRequestBuilder<AdCountsWorker>()
          .setInitialDelay(10, TimeUnit.MINUTES)
          .setConstraints(connected)
          .build(),
      )
    }

    /** When ads are switched off on the server: no more work. */
    fun cancel(context: Context) {
      WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
      WorkManager.getInstance(context).cancelUniqueWork(SOON)
    }
  }
}

/**
 * Small settings of house ads. `send_counts` is the «ارسال آمار تبلیغ» switch on the privacy
 * screen (on by default); the app writes it under the same name.
 */
class AdsPrefs(context: Context) {
  private val prefs = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

  var baseUrl: String
    get() = prefs.getString(KEY_BASE_URL, "").orEmpty()
    set(value) = prefs.edit().putString(KEY_BASE_URL, value).apply()

  val sendCounts: Boolean get() = prefs.getBoolean(KEY_SEND_COUNTS, true)

  /** Debug builds: a preview token entered by hand; shows that one draft ad, never counted. */
  var previewToken: String?
    get() = prefs.getString(KEY_PREVIEW, null)
    set(value) = prefs.edit().putString(KEY_PREVIEW, value).apply()

  companion object {
    const val NAME = "tasnim_ads"
    const val KEY_SEND_COUNTS = "send_counts"
    private const val KEY_BASE_URL = "base_url"
    private const val KEY_PREVIEW = "preview_token"
  }
}
