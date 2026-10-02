plugins {
  id("quran.android.library.android")
  id("app.cash.sqldelight")
  alias(libs.plugins.metro)
}

sqldelight {
  databases {
    create("PersianTafsirDatabase") {
      packageName.set("com.quran.mobile.persiantafsir.data")
    }
  }
}

android.namespace = "com.quran.mobile.persiantafsir"

dependencies {
  implementation(project(":common:di"))
  implementation(project(":common:data"))

  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.sqldelight.android.driver)
  implementation(libs.sqldelight.coroutines.extensions)
  implementation(libs.timber)

  // testing
  testImplementation(project(":pages:data:madani"))
  testImplementation(libs.junit)
  testImplementation(libs.truth)
  testImplementation(libs.sqldelight.sqlite.driver)
}
