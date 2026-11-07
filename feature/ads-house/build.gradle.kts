plugins {
  id("quran.android.library.compose")
  alias(libs.plugins.metro)
}

// House ads (ADR 0010): our own ads from the Tasnim server, in four slots only (live TV, radio,
// library list, book details). No ad SDK, no device id. Builds with -PnoAds use ads-noop instead.
// Never depend on this from a Quran reading module: `checkAdsBoundaries` fails the build.
android {
  namespace = "com.quran.mobile.feature.ads.house"
  testOptions.unitTests.isIncludeAndroidResources = true
}

dependencies {
  implementation(project(":common:data"))
  implementation(project(":common:di"))
  implementation(project(":feature:ads-api"))

  implementation(libs.androidx.activity.compose)
  implementation(libs.compose.foundation)
  implementation(libs.compose.material3)
  implementation(libs.compose.ui)

  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.moshi)
  implementation(platform(libs.okhttp.bom))
  implementation(libs.okhttp)
  implementation(libs.androidx.work.runtime.ktx)
  // «حریم خصوصی و تبلیغات» section in the app settings
  implementation(libs.androidx.preference.ktx)

  testImplementation(libs.junit)
  testImplementation(libs.truth)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.okhttp.mockserver)
  testImplementation(libs.robolectric)
}
