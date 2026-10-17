plugins {
  id("quran.android.library.compose")
}

android {
  namespace = "com.quran.mobile.feature.tajweed"
  androidResources.noCompress += "json"
}

dependencies {
  implementation(project(":common:data"))

  // compose
  implementation(libs.compose.foundation)
  implementation(libs.compose.material3)
  implementation(libs.compose.ui)

  // implementation but removed for release builds
  implementation(libs.compose.ui.tooling.preview)
  implementation(libs.compose.ui.tooling)

  // coroutines
  implementation(libs.kotlinx.coroutines.core)

  testImplementation(libs.junit)
  testImplementation(libs.truth)
}
