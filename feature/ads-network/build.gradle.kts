plugins {
  id("quran.android.library.compose")
  alias(libs.plugins.metro)
}

// The ad network next to house ads (ads plan §9): only in builds made with -PnetworkAds (the
// Google Play release). Step 6.1 holds a stand-in that draws a placeholder; step 6.2 replaces
// it with the Google Mobile Ads SDK. Do not release a -PnetworkAds build before step 6.2.
// Never depend on this from a Quran reading module: `checkAdsBoundaries` fails the build.
android.namespace = "com.quran.mobile.feature.ads.network"

dependencies {
  implementation(project(":common:data"))
  implementation(project(":feature:ads-api"))

  implementation(libs.compose.foundation)
  implementation(libs.compose.material3)
  implementation(libs.compose.ui)
  implementation(libs.kotlinx.coroutines.core)

  testImplementation(libs.junit)
  testImplementation(libs.truth)
  testImplementation(libs.kotlinx.coroutines.test)
}
