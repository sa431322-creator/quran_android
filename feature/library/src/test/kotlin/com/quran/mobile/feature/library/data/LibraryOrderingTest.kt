package com.quran.mobile.feature.library.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LibraryOrderingTest {

  @Test
  fun nextOrderStartsAtTenAndStepsByTen() {
    assertThat(LibraryOrdering.nextOrder(emptyList())).isEqualTo(10)
    assertThat(LibraryOrdering.nextOrder(listOf(10, 20, 30))).isEqualTo(40)
    assertThat(LibraryOrdering.nextOrder(listOf(10, 15))).isEqualTo(25)
  }

  @Test
  fun orderAtUsesTheGapBetweenNeighbours() {
    val orders = listOf(10, 20, 30, 40)
    assertThat(LibraryOrdering.orderAt(orders, 1)).isEqualTo(15)
    assertThat(LibraryOrdering.orderAt(orders, 0)).isEqualTo(5)
    assertThat(LibraryOrdering.orderAt(orders, 4)).isEqualTo(50)
    assertThat(LibraryOrdering.orderAt(emptyList(), 0)).isEqualTo(10)
  }

  @Test
  fun orderAtIsNullWhenThereIsNoGap() {
    assertThat(LibraryOrdering.orderAt(listOf(10, 11), 1)).isNull()
    assertThat(LibraryOrdering.orderAt(listOf(1), 0)).isNull()
  }

  @Test
  fun renumberSpacesOrdersByTen() {
    assertThat(LibraryOrdering.renumber(listOf("b", "a", "c"))).containsExactly(
      OrderUpdate("b", 10),
      OrderUpdate("a", 20),
      OrderUpdate("c", 30)
    ).inOrder()
  }

  @Test
  fun comparatorBreaksTiesById() {
    val first = MockLibraryData.BOOKS[0].copy(id = "a", order = 10)
    val second = first.copy(id = "b")
    val earlier = first.copy(id = "z", order = 5)
    assertThat(listOf(second, first, earlier).sortedWith(LibraryOrdering.comparator))
      .containsExactly(earlier, first, second).inOrder()
  }
}
