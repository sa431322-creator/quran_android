plugins {
  id("quran.android.library.compose")
}

// The ads interface every screen with an ad slot depends on. No network code and no SDKs:
// the implementation (ads-house, or ads-noop for builds without ads) is bound by the app.
// Never depend on this from a Quran reading module: `checkAdsBoundaries` fails the build.
android.namespace = "com.quran.mobile.feature.ads"

dependencies {
  implementation(libs.compose.foundation)
  implementation(libs.compose.runtime)
  implementation(libs.compose.ui)
  implementation(libs.kotlinx.coroutines.core)
  // forgiving read of the app-config `ads` section (AdsConfig)
  implementation(libs.moshi)

  testImplementation(libs.junit)
  testImplementation(libs.truth)
}
