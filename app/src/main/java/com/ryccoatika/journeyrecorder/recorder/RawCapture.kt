package com.ryccoatika.journeyrecorder.recorder

import com.ryccoatika.journeyrecorder.data.db.Confidence

/**
 * Immutable, framework-free snapshot of an accessibility node taken on the
 * a11y callback thread. Deliberately contains NO Android types (bounds are a
 * pre-formatted "[l,t][r,b]" string) so the capture pipeline is JVM-testable.
 */
data class ElementInfo(
    /** viewIdResourceName of the node itself, or of the anchoring ancestor. */
    val elementId: String? = null,
    /** Own text, or descendant-text join when the node has none. */
    val text: String? = null,
    val contentDescription: String? = null,
    val className: String? = null,
    /** Screen bounds pre-formatted as "[l,t][r,b]". */
    val bounds: String? = null,
    /** Hint text (API 26+). */
    val hint: String? = null,
    /** id or className of the clickable/id-bearing ancestor used as anchor. */
    val ancestorAnchor: String? = null,
    /** collectionItemInfo rowIndex when the node lives in a list/grid. */
    val collectionIndex: Int? = null,
    val isPassword: Boolean = false,
    val isEditable: Boolean = false,
    val isCheckable: Boolean = false,
    val isChecked: Boolean = false,
) {
    /** Stable-ish identity used for click/select dedup and scroll coalescing. */
    val stableKey: String?
        get() = when {
            !elementId.isNullOrBlank() -> "id:$elementId"
            !contentDescription.isNullOrBlank() -> "desc:$contentDescription"
            !className.isNullOrBlank() && !bounds.isNullOrBlank() -> "cb:$className$bounds"
            else -> null
        }
}

/**
 * One raw thing that happened, extracted synchronously by [EventInterpreter]
 * and shipped to [StepPipeline]. Pure Kotlin data — no Android classes.
 *
 * [uptimeMs] is pipeline-internal (dedup/coalescing math); only [wallClockMs]
 * is ever persisted.
 */
sealed class RawCapture {
    abstract val uptimeMs: Long
    abstract val wallClockMs: Long
    abstract val screenName: String?
    abstract val confidence: Confidence

    data class Click(
        override val uptimeMs: Long,
        override val wallClockMs: Long,
        override val screenName: String?,
        override val confidence: Confidence,
        val element: ElementInfo,
        val longClick: Boolean = false,
    ) : RawCapture()

    data class Select(
        override val uptimeMs: Long,
        override val wallClockMs: Long,
        override val screenName: String?,
        override val confidence: Confidence,
        val element: ElementInfo,
    ) : RawCapture()

    data class Text(
        override val uptimeMs: Long,
        override val wallClockMs: Long,
        override val screenName: String?,
        override val confidence: Confidence,
        val element: ElementInfo,
        val windowId: Int,
        /** Stable field identity; null means unknown => fail-closed masking. */
        val fieldKey: String?,
        /** Full current text of the field (latest wins in the coalescer). */
        val text: String?,
        /** Whether the field already had text before this change event. */
        val beforeTextNonEmpty: Boolean,
    ) : RawCapture()

    /** Focus moved; flushes pending text coalescers, never persisted itself. */
    data class FocusChange(
        override val uptimeMs: Long,
        override val wallClockMs: Long,
        override val screenName: String?,
        override val confidence: Confidence,
        val element: ElementInfo,
    ) : RawCapture()

    data class Scroll(
        override val uptimeMs: Long,
        override val wallClockMs: Long,
        override val screenName: String?,
        override val confidence: Confidence,
        val element: ElementInfo,
        val nodeKey: String,
        /** Present on API 28+; null below. */
        val deltaX: Int?,
        val deltaY: Int?,
        /** Fallback below API 28. */
        val fromIndex: Int?,
        val toIndex: Int?,
    ) : RawCapture()

    data class ScreenOpen(
        override val uptimeMs: Long,
        override val wallClockMs: Long,
        /** The NEW screen label. */
        override val screenName: String?,
        override val confidence: Confidence,
        val label: String,
    ) : RawCapture()

    /** Dialog shown — NOT a screen boundary; screenName stays the old label. */
    data class DialogOpen(
        override val uptimeMs: Long,
        override val wallClockMs: Long,
        override val screenName: String?,
        override val confidence: Confidence,
        val title: String,
    ) : RawCapture()

    data class SystemDialogClick(
        override val uptimeMs: Long,
        override val wallClockMs: Long,
        override val screenName: String?,
        override val confidence: Confidence,
        val packageName: String,
        /** Full rendered message, e.g. System: tapped "Allow" on system dialog. */
        val message: String,
    ) : RawCapture()

    data class AppMarker(
        override val uptimeMs: Long,
        override val wallClockMs: Long,
        override val screenName: String?,
        override val confidence: Confidence,
        val message: String,
    ) : RawCapture()
}
