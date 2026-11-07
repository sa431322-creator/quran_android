package com.quran.labs.androidquran.tasnim

import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import java.io.File

/**
 * Ads never on Quran pages (ADR 0010). The Gradle task `checkAdsBoundaries` keeps ads modules out
 * of every other module; this test covers the modules that may use ads, where the Quran reader
 * (app) and the book reader (library) also live: only the listed places may mention ads.
 */
class AdsPlacementGuardTest {
  private val root: File = generateSequence(File(System.getProperty("user.dir")!!).absoluteFile) {
    it.parentFile
  }.first { File(it, "settings.gradle.kts").exists() }

  /** Source folders and the only sub-paths in them that may refer to the ads modules. */
  private val allowed = mapOf(
    "app/src/main" to listOf("java/com/quran/labs/androidquran/tasnim/"),
    "feature/livetv/src/main" to listOf("kotlin/com/quran/mobile/feature/livetv/"),
    "feature/library/src/main" to listOf(
      "kotlin/com/quran/mobile/feature/library/ui/",
      "kotlin/com/quran/mobile/feature/library/details/",
      "kotlin/com/quran/mobile/feature/library/presenter/",
      "kotlin/com/quran/mobile/feature/library/di/",
      "kotlin/com/quran/mobile/feature/library/LibraryActivity.kt",
    ),
  )

  private val adsReference = Regex("""\bcom\.quran\.mobile\.feature\.ads\b""")

  @Test
  fun adsAreOnlyMentionedWhereTheyMayShow() {
    val problems = allowed.flatMap { (dir, places) ->
      val base = File(root, dir)
      base.walkTopDown()
        .filter { it.isFile && it.extension in setOf("kt", "java", "xml") }
        .filter { adsReference.containsMatchIn(it.readText()) }
        .map { it.relativeTo(base).invariantSeparatorsPath }
        .filterNot { rel -> places.any { rel.startsWith(it) } }
        .map { "$dir/$it" }
        .toList()
    }
    assertWithMessage("ads used outside the allowed places (never near Quran pages)")
      .that(problems)
      .isEmpty()
  }

  @Test
  fun theBookReaderAndQuranReaderAreNotAllowedPlaces() {
    val places = allowed.values.flatten()
    for (forbidden in listOf("library/reader/", "androidquran/ui/", "androidquran/presenter/")) {
      assertWithMessage(forbidden).that(places.none { forbidden in it }).isTrue()
    }
  }
}
