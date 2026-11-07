package com.quran.mobile.feature.ads.house

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ImpressionGateTest {
  private val counts = mutableListOf<Pair<Int, Int>>()
  private fun TestScope.gate() = ImpressionGate(backgroundScope) { i, c -> counts += i to c }

  @Test
  fun countsAfterOneSecondOfBeingVisible() = runTest {
    val gate = gate()
    gate.update(ready = true)
    advanceTimeBy(999)
    runCurrent()
    assertThat(counts).isEmpty()
    advanceTimeBy(2)
    runCurrent()
    assertThat(counts).containsExactly(1 to 0)
  }

  @Test
  fun aBreakBeforeOneSecondStartsOver() = runTest {
    val gate = gate()
    gate.update(ready = true)
    advanceTimeBy(800)
    gate.update(ready = false) // scrolled away or screen paused
    gate.update(ready = true)
    advanceTimeBy(800)
    runCurrent()
    assertThat(counts).isEmpty()
    advanceTimeBy(300)
    runCurrent()
    assertThat(counts).containsExactly(1 to 0)
  }

  @Test
  fun onlyOncePerVisitUntilTheScreenComesBack() = runTest {
    val gate = gate()
    gate.update(ready = true)
    advanceTimeBy(1_100)
    gate.update(ready = false)
    gate.update(ready = true)
    advanceTimeBy(5_000)
    runCurrent()
    assertThat(counts).containsExactly(1 to 0)

    gate.newVisit()
    gate.update(ready = true)
    advanceTimeBy(1_100)
    runCurrent()
    assertThat(counts).containsExactly(1 to 0, 1 to 0)
  }

  @Test
  fun anEarlyTapCountsTheViewTooAndOnlyOneTapPerVisitCounts() = runTest {
    val gate = gate()
    gate.update(ready = true)
    advanceTimeBy(300)
    gate.click()
    advanceTimeBy(2_000)
    runCurrent()
    gate.click()
    assertThat(counts).containsExactly(1 to 1)
    gate.newVisit()
    gate.update(ready = true)
    advanceTimeBy(1_100)
    runCurrent()
    gate.click()
    assertThat(counts).containsExactly(1 to 1, 1 to 0, 0 to 1)
    assertThat(counts.sumOf { it.second }).isAtMost(counts.sumOf { it.first })
  }
}
