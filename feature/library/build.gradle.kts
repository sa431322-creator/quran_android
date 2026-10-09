plugins {
  id("quran.android.library.compose")
  alias(libs.plugins.metro)
}

android.namespace = "com.quran.mobile.feature.library"

dependencies {
  implementation(project(":common:data"))
  implementation(project(":common:di"))
  implementation(project(":common:ui:core"))

  implementation(libs.androidx.activity.compose)
  // AppCompatActivity applies the app's own light/dark choice (AppCompatDelegate)
  implementation(libs.androidx.appcompat)

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
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.turbine)
}
