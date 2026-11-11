plugins {
  id("quran.android.library.compose")
  alias(libs.plugins.metro)
}

// Builds without ads (F-Droid, `-PnoAds`): shows nothing, reads no settings, calls no server,
// schedules no work.
android.namespace = "com.quran.mobile.feature.ads.noop"

dependencies {
  implementation(project(":common:data"))
  implementation(project(":feature:ads-api"))
  implementation(libs.compose.runtime)
  implementation(libs.compose.ui)
  implementation(libs.kotlinx.coroutines.core)

  testImplementation(libs.junit)
  testImplementation(libs.truth)
}
