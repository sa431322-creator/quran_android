package com.quran.mobile.feature.ads.house

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceGroup
import androidx.preference.SwitchPreferenceCompat
import com.quran.data.di.AppScope
import com.quran.mobile.di.ExtraPreferencesProvider
import com.quran.mobile.di.qualifier.ApplicationContext
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * «حریم خصوصی و تبلیغات» in the app settings (plan §6.7): what house ads send, and the switch that
 * stops sending ad counts (on by default). Debug builds also get a field for a preview token.
 * Only in builds with ads: ads-noop adds nothing.
 */
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class)
@Inject
class AdsPrivacyPreferences(
  @ApplicationContext private val appContext: Context,
  private val provider: HouseAdProvider,
) : ExtraPreferencesProvider {
  override val order: Int = 100

  override fun addPreferences(root: PreferenceGroup) {
    val context = root.context
    val category = PreferenceCategory(context).apply {
      key = CATEGORY_KEY
      title = "حریم خصوصی و تبلیغات"
      isIconSpaceReserved = false
    }
    root.addPreference(category)
    category.addPreference(
      Preference(context).apply {
        key = ABOUT_KEY
        isSelectable = false
        isIconSpaceReserved = false
        summary = "تبلیغ‌ها فقط در پخش زنده، رادیو، فهرست کتاب‌ها و صفحهٔ کتاب نمایش داده " +
          "می‌شوند؛ هرگز در صفحه‌های قرآن. برنامه فقط شمار نمایش و لمس هر تبلیغ در هر روز " +
          "را می‌فرستد؛ بدون شناسهٔ دستگاه یا اطلاعات شخصی."
      }
    )
    category.addPreference(
      SwitchPreferenceCompat(context).apply {
        key = AdsPrefs.KEY_SEND_COUNTS
        isPersistent = false
        isIconSpaceReserved = false
        title = "ارسال آمار تبلیغ‌ها"
        summary = "شمار نمایش و لمس، برای گزارش به تبلیغ‌دهنده"
        isChecked = AdsPrefs(appContext).sendCounts
        setOnPreferenceChangeListener { _, value ->
          val on = value as Boolean
          appContext.getSharedPreferences(AdsPrefs.NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(AdsPrefs.KEY_SEND_COUNTS, on).apply()
          if (!on) {
            // nothing more is sent; counts already on the phone are deleted
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
              AdCountStore(AdCountsWorker.countsDir(appContext)).clear()
            }
          }
          true
        }
      }
    )
    if (appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
      category.addPreference(
        EditTextPreference(context).apply {
          key = PREVIEW_KEY
          isPersistent = false
          isIconSpaceReserved = false
          title = "معاینهٔ تبلیغ (نسخهٔ آزمایشی)"
          val prefs = AdsPrefs(appContext)
          text = prefs.previewToken.orEmpty()
          summary = if (prefs.previewToken.isNullOrBlank()) "رمز معاینه از پنل مدیریت" else "فعال"
          setOnPreferenceChangeListener { pref, value ->
            val token = (value as String).trim()
            prefs.previewToken = token.ifBlank { null }
            pref.summary = if (token.isBlank()) "رمز معاینه از پنل مدیریت" else "فعال"
            provider.forceRefresh()
            true
          }
        }
      )
    }
  }

  override fun onPreferenceClick(preference: Preference): Boolean = false

  private companion object {
    const val CATEGORY_KEY = "tasnim_ads_privacy"
    const val ABOUT_KEY = "tasnim_ads_about"
    const val PREVIEW_KEY = "tasnim_ads_preview"
  }
}
