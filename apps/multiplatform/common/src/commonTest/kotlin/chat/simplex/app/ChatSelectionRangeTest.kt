package chat.simplex.app

import chat.simplex.common.model.ChatItem
import chat.simplex.common.views.chat.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.*

class ChatSelectionRangeTest {
  private fun single(id: Long): MergedItem.Single =
    MergedItem.Single(ListItem(ChatItem.getSampleData(id = id), null, null, 0), 0)

  private fun grouped(ids: List<Long>): MergedItem.Grouped = MergedItem.Grouped(
    items = ArrayList(ids.map { ListItem(ChatItem.getSampleData(id = it), null, null, 0) }),
    revealed = false,
    revealedIdsWithinGroup = mutableListOf(),
    rangeInReversed = MutableStateFlow(0..0),
    mergeCategory = null,
    unreadIds = mutableSetOf(),
    startIndexInReversedItems = 0
  )

  // rows are numbered from the bottom: row 0 is the newest message
  private fun mergedItems(rows: List<MergedItem>): MergedItems {
    val indexInParentItems = mutableMapOf<Long, Int>()
    rows.forEachIndexed { row, merged ->
      mergedItemChatItemIds(merged).forEach { indexInParentItems[it] = row }
    }
    return MergedItems(rows, emptyList(), indexInParentItems)
  }

  @Test
  fun testRowOfSingleItemHasOneId() {
    assertEquals(listOf(7L), mergedItemChatItemIds(single(7L)))
  }

  @Test
  fun testRowOfCollapsedGroupHasAllIds() {
    assertEquals(listOf(3L, 2L, 1L), mergedItemChatItemIds(grouped(listOf(3L, 2L, 1L))))
  }

  @Test
  fun testRangeRowsFromAnchorTowardsTopOfList() {
    val items = mergedItems(listOf(single(10L), single(9L), single(8L), single(7L)))
    // anchor is in row 1, the top edge of the list is row 3
    assertEquals(1..3, selectionRangeRows(items, anchorId = 9L, edgeRow = 3))
  }

  @Test
  fun testRangeRowsFromAnchorTowardsBottomOfList() {
    val items = mergedItems(listOf(single(10L), single(9L), single(8L), single(7L)))
    assertEquals(0..2, selectionRangeRows(items, anchorId = 8L, edgeRow = 0))
  }

  @Test
  fun testRangeRowsOfSingleRowIsThatRow() {
    val items = mergedItems(listOf(single(10L), single(9L)))
    assertEquals(1..1, selectionRangeRows(items, anchorId = 9L, edgeRow = 1))
  }

  @Test
  fun testNoRangeWithoutAnchorOrWithUnknownEdge() {
    val items = mergedItems(listOf(single(10L), single(9L)))
    assertNull(selectionRangeRows(items, anchorId = null, edgeRow = 1))
    assertNull(selectionRangeRows(items, anchorId = 11L, edgeRow = 1))
    assertNull(selectionRangeRows(items, anchorId = 10L, edgeRow = 2))
    assertNull(selectionRangeRows(items, anchorId = 10L, edgeRow = -1))
    assertTrue(selectionToHereIds(items, null, 1).isEmpty())
  }

  @Test
  fun testSelectToHereExpandsMergedRowsToAllTheirItems() {
    val items = mergedItems(listOf(single(10L), grouped(listOf(9L, 8L)), single(7L), single(6L)))
    // from the anchored newest-of-group item in row 1 to the top edge row 3
    assertEquals(listOf(9L, 8L, 7L, 6L), selectionToHereIds(items, anchorId = 9L, edgeRow = 3))
    // and towards the bottom edge, covering that group again
    assertEquals(listOf(10L, 9L, 8L), selectionToHereIds(items, anchorId = 8L, edgeRow = 0))
  }

  @Test
  fun testSelectToHereAddsOnlyUnselectedItems() {
    val items = mergedItems(listOf(single(10L), single(9L), single(8L)))
    assertTrue(selectionToHereAddsItems(items, anchorId = 10L, edgeRow = 2, selected = setOf(10L)))
    assertTrue(selectionToHereAddsItems(items, anchorId = 10L, edgeRow = 2, selected = setOf(10L, 9L)))
    assertFalse(selectionToHereAddsItems(items, anchorId = 10L, edgeRow = 2, selected = setOf(10L, 9L, 8L)))
    assertFalse(selectionToHereAddsItems(items, anchorId = 10L, edgeRow = 0, selected = setOf(10L)))
  }

  @Test
  fun testVisibleEdgeRows() {
    assertNull(visibleEdgeRows(emptyList()))
    assertEquals(3 to 1, visibleEdgeRows(listOf(1, 2, 3)))
    assertEquals(4 to 4, visibleEdgeRows(listOf(4)))
  }
}
