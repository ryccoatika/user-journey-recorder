package com.ryccoatika.journeyrecorder.recorder

import android.os.Build
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import com.ryccoatika.journeyrecorder.data.db.Confidence

/**
 * AccessibilityEvent -> [RawCapture]. Runs synchronously on the a11y callback
 * thread. ALL AccessibilityNodeInfo access lives here (and in
 * [ElementIdentity]); every node traversal is wrapped so that any throw
 * degrades to event-payload extraction with [Confidence.LOW] — a click is
 * never dropped because a node went stale.
 */
class EventInterpreter(private val screenTracker: ScreenTracker) {

    fun extract(event: AccessibilityEvent): RawCapture? {
        val uptimeMs = SystemClock.uptimeMillis()
        val wallClockMs = System.currentTimeMillis()
        return when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ->
                screenTracker.onWindowStateChanged(event, uptimeMs, wallClockMs)

            AccessibilityEvent.TYPE_VIEW_CLICKED ->
                click(event, uptimeMs, wallClockMs, longClick = false)

            AccessibilityEvent.TYPE_VIEW_LONG_CLICKED ->
                click(event, uptimeMs, wallClockMs, longClick = true)

            AccessibilityEvent.TYPE_VIEW_SELECTED -> {
                val (element, confidence) = elementFromEvent(event)
                RawCapture.Select(uptimeMs, wallClockMs, screenTracker.currentScreen, confidence, element)
            }

            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> text(event, uptimeMs, wallClockMs)

            AccessibilityEvent.TYPE_VIEW_FOCUSED -> {
                val (element, confidence) = elementFromEvent(event)
                RawCapture.FocusChange(uptimeMs, wallClockMs, screenTracker.currentScreen, confidence, element)
            }

            AccessibilityEvent.TYPE_VIEW_SCROLLED -> scroll(event, uptimeMs, wallClockMs)

            else -> null
        }
    }

    /**
     * Simplified path for allowlisted system packages (permission dialogs,
     * intent resolver): clicks only, event payload only — no node traversal.
     */
    fun extractSystemDialog(event: AccessibilityEvent): RawCapture? {
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_CLICKED) return null
        val uptimeMs = SystemClock.uptimeMillis()
        val wallClockMs = System.currentTimeMillis()
        val label = event.text
            .asSequence()
            .filterNotNull()
            .map { it.toString().trim() }
            .firstOrNull { it.isNotEmpty() }
            ?: event.contentDescription?.toString()?.trim().orEmpty()
        return RawCapture.SystemDialogClick(
            uptimeMs = uptimeMs,
            wallClockMs = wallClockMs,
            screenName = screenTracker.currentScreen,
            confidence = Confidence.NORMAL,
            packageName = event.packageName?.toString().orEmpty(),
            message = "System: tapped \"$label\" on system dialog",
        )
    }

    // ------------------------------------------------------------------ private

    private fun click(
        event: AccessibilityEvent,
        uptimeMs: Long,
        wallClockMs: Long,
        longClick: Boolean,
    ): RawCapture {
        val (element, confidence) = elementFromEvent(event)
        return RawCapture.Click(
            uptimeMs = uptimeMs,
            wallClockMs = wallClockMs,
            screenName = screenTracker.currentScreen,
            confidence = confidence,
            element = element,
            longClick = longClick,
        )
    }

    private fun text(event: AccessibilityEvent, uptimeMs: Long, wallClockMs: Long): RawCapture {
        val (element, confidence) = elementFromEvent(event)
        val isPassword = element.isPassword || event.isPassword
        val fullText = element.text ?: joinedEventText(event)
        return RawCapture.Text(
            uptimeMs = uptimeMs,
            wallClockMs = wallClockMs,
            screenName = screenTracker.currentScreen,
            confidence = confidence,
            element = if (isPassword == element.isPassword) element else element.copy(isPassword = true),
            windowId = event.windowId,
            fieldKey = ElementIdentity.fieldKey(element),
            text = fullText,
            beforeTextNonEmpty = !event.beforeText.isNullOrEmpty(),
        )
    }

    private fun scroll(event: AccessibilityEvent, uptimeMs: Long, wallClockMs: Long): RawCapture {
        val (element, confidence) = elementFromEvent(event)
        val nodeKey = element.stableKey ?: "window:${event.windowId}"
        var deltaX: Int? = null
        var deltaY: Int? = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val dx = event.scrollDeltaX
            val dy = event.scrollDeltaY
            if (dx != 0 || dy != 0) {
                deltaX = dx
                deltaY = dy
            }
        }
        return RawCapture.Scroll(
            uptimeMs = uptimeMs,
            wallClockMs = wallClockMs,
            screenName = screenTracker.currentScreen,
            confidence = confidence,
            element = element,
            nodeKey = nodeKey,
            deltaX = deltaX,
            deltaY = deltaY,
            fromIndex = event.fromIndex.takeIf { it >= 0 },
            toIndex = event.toIndex.takeIf { it >= 0 },
        )
    }

    /**
     * Full node extraction; any throw (stale node, binder error, security
     * exception) degrades to the event payload with LOW confidence. Obtained
     * nodes are always recycled on API 24-32.
     */
    private fun elementFromEvent(event: AccessibilityEvent): Pair<ElementInfo, Confidence> {
        val recycler = NodeRecycler()
        try {
            val source = event.source ?: return payloadElement(event) to Confidence.LOW
            val compat = recycler.track(AccessibilityNodeInfoCompat.wrap(source))
                ?: return payloadElement(event) to Confidence.LOW
            return ElementIdentity.describe(compat, recycler) to Confidence.NORMAL
        } catch (_: Throwable) {
            return payloadElement(event) to Confidence.LOW
        } finally {
            recycler.recycleAll()
        }
    }

    /** Degraded extraction from the event payload alone — never throws. */
    private fun payloadElement(event: AccessibilityEvent): ElementInfo = ElementInfo(
        text = joinedEventText(event),
        contentDescription = event.contentDescription?.toString()?.takeUnless { it.isBlank() },
        className = event.className?.toString(),
        isPassword = event.isPassword,
    )

    private fun joinedEventText(event: AccessibilityEvent): String? = try {
        event.text
            .asSequence()
            .filterNotNull()
            .joinToString(" ") { it.toString() }
            .trim()
            .takeUnless { it.isEmpty() }
    } catch (_: Throwable) {
        null
    }
}
