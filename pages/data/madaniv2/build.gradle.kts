plugins {
  id("quran.android.library.android")
}

android.namespace = "com.quran.labs.androidquran.pages.data.madaniv2"

dependencies {
  implementation(project(":common:data"))

  testImplementation(libs.junit)
  testImplementation(libs.truth)
}
