package com.ryccoatika.journeyrecorder.recorder

import android.graphics.Rect
import android.os.Build
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat

/**
 * Tracks every [AccessibilityNodeInfoCompat] obtained during one extraction so
 * they can be recycled in a single `finally`. Recycling is required on
 * API 24-32 and a documented no-op from 33 on.
 */
internal class NodeRecycler {
    private val nodes = ArrayList<AccessibilityNodeInfoCompat>()

    fun track(node: AccessibilityNodeInfoCompat?): AccessibilityNodeInfoCompat? {
        if (node != null) nodes.add(node)
        return node
    }

    fun recycleAll() {
        if (Build.VERSION.SDK_INT < 33) {
            for (node in nodes) {
                try {
                    @Suppress("DEPRECATION")
                    node.recycle()
                } catch (_: Throwable) {
                    // already recycled / stale — nothing to do
                }
            }
        }
        nodes.clear()
    }
}

/**
 * Element identity fallback chain. The ONLY place (besides [EventInterpreter])
 * that touches AccessibilityNodeInfo; everything it returns is plain Kotlin.
 *
 * Chain: viewIdResourceName -> contentDescription -> own text -> descendant
 * text join (BFS depth<=2, cap 5 text nodes, <=120 chars) -> hint (API 26+)
 * -> className+bounds. When the node has neither id nor contentDescription,
 * walk up <=5 parents to the first clickable-or-id ancestor and anchor on it.
 */
internal object ElementIdentity {
    // isChecked is deprecated at API 35 (replaced by getChecked()), but the
    // replacement needs API 35 while we support minSdk 24 — the boolean works
    // across all levels, so suppress the unavoidable deprecation.
    @Suppress("DEPRECATION")
    fun describe(source: AccessibilityNodeInfoCompat, recycler: NodeRecycler): ElementInfo {
        val rect = Rect()
        source.getBoundsInScreen(rect)
        val bounds = "[${rect.left},${rect.top}][${rect.right},${rect.bottom}]"

        val viewId = source.viewIdResourceName?.takeUnless { it.isBlank() }
        val contentDesc = source.contentDescription?.toString()?.takeUnless { it.isBlank() }
        val ownText = source.text?.toString()?.takeUnless { it.isBlank() }
        val hint = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            source.hintText?.toString()?.takeUnless { it.isBlank() }
        } else {
            null
        }
        val className = source.className?.toString()
        val collectionIndex = source.collectionItemInfo?.rowIndex

        var elementId = viewId
        var ancestorAnchor: String? = null
        if (viewId == null && contentDesc == null) {
            // Walk UP <=5 parents to the first clickable or id-bearing node.
            var parent = recycler.track(source.parent)
            var hops = 0
            while (parent != null && hops < MAX_ANCESTOR_HOPS) {
                val parentId = parent.viewIdResourceName?.takeUnless { it.isBlank() }
                if (parentId != null || parent.isClickable) {
                    ancestorAnchor = parentId ?: parent.className?.toString()
                    if (parentId != null) elementId = parentId
                    break
                }
                parent = recycler.track(parent.parent)
                hops++
            }
        }

        // Keep the deepest source-subtree text as the label.
        val label = ownText ?: descendantText(source, recycler)

        return ElementInfo(
            elementId = elementId,
            text = label,
            contentDescription = contentDesc,
            className = className,
            bounds = bounds,
            hint = hint,
            ancestorAnchor = ancestorAnchor,
            collectionIndex = collectionIndex,
            isPassword = source.isPassword,
            isEditable = source.isEditable,
            isCheckable = source.isCheckable,
            isChecked = source.isChecked,
        )
    }

    /**
     * Stable identity for text coalescing + sticky masking. Must never be
     * derived from mutable text or bounds. Null => unknown => fail closed.
     */
    fun fieldKey(info: ElementInfo): String? = when {
        !info.elementId.isNullOrBlank() -> "id:${info.elementId}"
        !info.contentDescription.isNullOrBlank() -> "desc:${info.contentDescription}"
        !info.hint.isNullOrBlank() -> "hint:${info.hint}"
        else -> null
    }

    private fun descendantText(
        root: AccessibilityNodeInfoCompat,
        recycler: NodeRecycler,
    ): String? {
        val texts = ArrayList<String>(MAX_TEXT_NODES)
        val queue = ArrayDeque<Pair<AccessibilityNodeInfoCompat, Int>>()
        queue.add(root to 0)
        while (queue.isNotEmpty() && texts.size < MAX_TEXT_NODES) {
            val (node, depth) = queue.removeFirst()
            if (node !== root) {
                val text = node.text?.toString()?.trim()
                if (!text.isNullOrEmpty()) texts.add(text)
            }
            if (depth < MAX_DESCENDANT_DEPTH) {
                val childCount = node.childCount
                for (i in 0 until childCount) {
                    if (queue.size >= MAX_QUEUE) break
                    val child = recycler.track(node.getChild(i)) ?: continue
                    queue.add(child to depth + 1)
                }
            }
        }
        if (texts.isEmpty()) return null
        val joined = texts.joinToString(" ")
        return if (joined.length > MAX_JOIN_CHARS) joined.take(MAX_JOIN_CHARS) else joined
    }

    private const val MAX_ANCESTOR_HOPS = 5
    private const val MAX_DESCENDANT_DEPTH = 2
    private const val MAX_TEXT_NODES = 5
    private const val MAX_JOIN_CHARS = 120
    private const val MAX_QUEUE = 40
}
