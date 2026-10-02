package com.ryccoatika.journeyrecorder.ui.home

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

private const val DRAG_SELECT_EDGE_PX = 140f

/**
 * Google-Photos-style long-press + drag range select installed on the list.
 * A single long-press (press, lift) toggles the anchor and enters selection
 * mode; dragging extends/shrinks the covered range. Long-press stays OFF the
 * rows — a row-level long-press would consume the pointer and kill this.
 */
internal fun Modifier.dragToSelect(
    listState: LazyListState,
    orderedIds: List<Long>,
    onSelectAnchor: (Long) -> Set<Long>,
    setSelection: (Set<Long>) -> Unit,
    scrollBy: (Float) -> Unit,
): Modifier = pointerInput(orderedIds) {
    var anchorId: Long? = null
    var base: Set<Long> = emptySet()
    detectDragGesturesAfterLongPress(
        onDragStart = { position ->
            anchorId = listState.journeyIdAt(position.y)
            base = anchorId?.let(onSelectAnchor) ?: emptySet()
        },
        onDragEnd = { anchorId = null },
        onDragCancel = { anchorId = null },
        onDrag = { change, _ ->
            val anchor = anchorId ?: return@detectDragGesturesAfterLongPress
            val y = change.position.y
            val edge = DRAG_SELECT_EDGE_PX
            when {
                y < edge -> scrollBy(y - edge)
                y > size.height - edge -> scrollBy(y - (size.height - edge))
            }
            val overId = listState.journeyIdAt(y) ?: return@detectDragGesturesAfterLongPress
            val anchorIndex = orderedIds.indexOf(anchor)
            val overIndex = orderedIds.indexOf(overId)
            if (anchorIndex == -1 || overIndex == -1) return@detectDragGesturesAfterLongPress
            val range = if (anchorIndex <= overIndex) {
                orderedIds.subList(anchorIndex, overIndex + 1)
            } else {
                orderedIds.subList(overIndex, anchorIndex + 1)
            }.toSet()
            setSelection((base - range) + (range - base))
        },
    )
}

/** Map a y-coordinate to the journey id of the row under it (null otherwise). */
private fun LazyListState.journeyIdAt(y: Float): Long? =
    layoutInfo.visibleItemsInfo
        .firstOrNull { y.toInt() in it.offset..(it.offset + it.size) }
        ?.key as? Long
