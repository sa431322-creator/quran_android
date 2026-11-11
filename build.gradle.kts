import java.util.Locale

plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.android.library) apply false
  alias(libs.plugins.crashlytics) apply false
  alias(libs.plugins.kotlin.parcelize) apply false
  alias(libs.plugins.compose.compiler) apply false
  alias(libs.plugins.ksp) apply false
  alias(libs.plugins.errorprone) apply false
  alias(libs.plugins.google.services) apply false
  alias(libs.plugins.sqldelight) apply false
  alias(libs.plugins.metro) apply false
  alias(libs.plugins.kover) apply false
}

subprojects {
  tasks.configureEach {
    doFirst {
      Locale.setDefault(Locale.US)
    }
  }
}

// ---- Ads never on Quran pages (ADR 0010) -------------------------------------------------
// Only these modules may depend on an ads module, and only with `implementation` (never `api`,
// which would hand the ads classes to every module that depends on them). The Quran reader,
// mushaf, tajweed, translations and audio modules can therefore never show an ad.
val adsAllowedModules = setOf(":app", ":feature:livetv", ":feature:library")

val checkAdsBoundaries by tasks.registering {
  group = "verification"
  description = "Fails if an ads module is reachable from a module that is not allowed to show ads."
  val root = rootProject
  doLast {
    val problems = root.allprojects.flatMap { p ->
      if (p.path.startsWith(":feature:ads-")) return@flatMap emptyList<String>()
      p.configurations.flatMap { conf ->
        conf.dependencies.withType(ProjectDependency::class.java)
          .filter { it.path.startsWith(":feature:ads-") }
          .mapNotNull { dep ->
            when {
              p.path !in adsAllowedModules -> "${p.path} (${conf.name}) depends on ${dep.path}"
              conf.name == "api" || conf.name.endsWith("Api") ->
                "${p.path} exposes ${dep.path} with ${conf.name}; use implementation"
              else -> null
            }
          }
      }
    }.distinct()
    if (problems.isNotEmpty()) {
      val list = problems.joinToString(separator = "\n") { "  - $it" }
      throw GradleException("Ads must never reach other screens (ADR 0010):\n$list")
    }
  }
}

subprojects {
  tasks.matching { it.name == "check" }.configureEach { dependsOn(checkAdsBoundaries) }
}
