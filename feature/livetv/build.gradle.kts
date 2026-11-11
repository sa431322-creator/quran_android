plugins {
  id("quran.android.library.compose")
  alias(libs.plugins.metro)
}

android {
  namespace = "com.quran.mobile.feature.livetv"
  androidResources.noCompress += "mp4"
  testOptions.unitTests.isIncludeAndroidResources = true
}

dependencies {
  implementation(project(":common:data"))
  implementation(project(":common:di"))
  implementation(project(":common:ui:core"))

  implementation(libs.androidx.activity.compose)
  // AppCompatActivity applies the app's own light/dark choice (AppCompatDelegate)
  implementation(libs.androidx.appcompat)

  // compose
  implementation(libs.compose.animation)
  implementation(libs.compose.foundation)
  implementation(libs.compose.material3)
  implementation(libs.compose.ui)

  // implementation but removed for release builds
  implementation(libs.compose.ui.tooling.preview)
  implementation(libs.compose.ui.tooling)

  // video playback; hls and dash are here so a remote stream is a data-only change
  implementation(libs.androidx.media3.exoplayer)
  implementation(libs.androidx.media3.exoplayer.hls)
  implementation(libs.androidx.media3.exoplayer.dash)
  implementation(libs.androidx.media3.ui)
  implementation(libs.androidx.media3.session)

  // coroutines
  implementation(libs.kotlinx.coroutines.core)

  testImplementation(libs.junit)
  testImplementation(libs.truth)
  testImplementation(libs.robolectric)
}
