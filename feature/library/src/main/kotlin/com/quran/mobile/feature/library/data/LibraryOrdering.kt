package com.quran.mobile.feature.library.data

/**
 * The library's ordering rules. Each category is ordered on its own by [LibraryBook.order]
 * in steps of [STEP], which leaves gaps so a book can be moved without touching the others.
 */
object LibraryOrdering {
  const val STEP = 10

  /** Ascending order, with the id breaking ties so equal orders always sort the same way. */
  val comparator: Comparator<LibraryBook> = compareBy<LibraryBook>({ it.order }, { it.id })

  /** Categories in their display order, then each category by [comparator]. */
  val displayComparator: Comparator<LibraryBook> =
    compareBy<LibraryBook> { it.category.ordinal }.then(comparator)

  /** The order for a book added after [orders]: 10 for an empty category, else the last + 10. */
  fun nextOrder(orders: Collection<Int>): Int = (orders.maxOrNull() ?: 0) + STEP

  /**
   * The order that puts a book at [index] among [sortedOrders] (the other books of the
   * category, ascending), or null when there's no gap left and the category needs [renumber].
   */
  fun orderAt(sortedOrders: List<Int>, index: Int): Int? {
    require(index in 0..sortedOrders.size) { "index $index out of 0..${sortedOrders.size}" }
    val before = sortedOrders.getOrNull(index - 1) ?: 0
    val after = sortedOrders.getOrNull(index) ?: return before + STEP
    val middle = before + (after - before) / 2
    return middle.takeIf { it > before && it < after }
  }

  /** Orders 10, 20, 30… for [ids], which are in their new display order. */
  fun renumber(ids: List<String>): List<OrderUpdate> =
    ids.mapIndexed { index, id -> OrderUpdate(id, (index + 1) * STEP) }
}
