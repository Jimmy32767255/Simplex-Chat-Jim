package chat.simplex.common.views.chat

/**
 * "Select to here" support for the multi-select mode of the chat view.
 *
 * The chat list renders one row per [MergedItem], and a row can contain several chat items
 * when consecutive items are merged (or when a group of items is collapsed).
 * Selecting a range therefore works with row indexes and expands every row to all its chat item ids.
 */

/**
 * Chat item ids of the row rendered by this merged item, in the order they are stored in the merged item.
 */
internal fun mergedItemChatItemIds(merged: MergedItem): List<Long> = when (merged) {
  is MergedItem.Single -> listOf(merged.item.item.id)
  is MergedItem.Grouped -> merged.items.map { it.item.id }
}

/**
 * Rows between the row of the anchor chat item and [edgeRow], inclusive, or null when either of them is unknown.
 */
internal fun selectionRangeRows(mergedItems: MergedItems, anchorId: Long?, edgeRow: Int): IntRange? {
  if (anchorId == null) return null
  val anchorRow = mergedItems.indexInParentItems[anchorId] ?: return null
  if (edgeRow !in mergedItems.items.indices) return null
  return if (anchorRow <= edgeRow) anchorRow..edgeRow else edgeRow..anchorRow
}

/**
 * Chat item ids added by "select to here": every item of every row between the anchor item and [edgeRow].
 */
internal fun selectionToHereIds(mergedItems: MergedItems, anchorId: Long?, edgeRow: Int): List<Long> {
  val rows = selectionRangeRows(mergedItems, anchorId, edgeRow) ?: return emptyList()
  return rows.flatMap { mergedItemChatItemIds(mergedItems.items[it]) }
}

/**
 * Whether "select to here" would add anything on top of the [selected] items,
 * so that the button is only shown when it has an effect.
 */
internal fun selectionToHereAddsItems(mergedItems: MergedItems, anchorId: Long?, edgeRow: Int, selected: Set<Long>): Boolean =
  selectionToHereIds(mergedItems, anchorId, edgeRow).any { it !in selected }

/**
 * Indexes of the topmost and of the bottommost visible rows of the chat list
 * (the list is reversed, so index 0 is at the bottom, i.e. the newest message).
 * The top edge is the larger index and the bottom edge is the smaller one.
 */
internal fun visibleEdgeRows(visibleRowIndexes: List<Int>): Pair<Int, Int>? =
  if (visibleRowIndexes.isEmpty()) null else visibleRowIndexes.max() to visibleRowIndexes.min()
