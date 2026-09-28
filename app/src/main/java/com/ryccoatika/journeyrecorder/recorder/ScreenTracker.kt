package com.ryccoatika.journeyrecorder.recorder

import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import com.ryccoatika.journeyrecorder.data.db.Confidence

/**
 * Screen-label ladder:
 *  1. WINDOW_STATE_CHANGED with an activity-like className => label is the
 *     simple class name (screen boundary).
 *  2. Dialog classes => a "Dialog opened: title" step, NOT a screen boundary.
 *  3. Toast classes => dropped.
 *  4. Compose/unknown fallback: window title -> bounded root scan (depth<=12,
 *     <=150 nodes) for paneTitle / heading / largest text in the top 25% of
 *     the screen -> else retain the current label.
 *
 * Also owns lazy re-sampling on taps (>1.5 s since last sample) and the
 * zero-element-ids detection performed on the first root sample of a
 * recording.
 */
class ScreenTracker(
    private val rootProvider: () -> AccessibilityNodeInfo?,
    private val windowsProvider: () -> List<AccessibilityWindowInfo>,
    private val screenHeightProvider: () -> Int,
) {
    /** Invoked at most once per recording when the first root sample finds zero view ids. */
    var onZeroElementIds: (() -> Unit)? = null

    var currentScreen: String? = null
        private set

    private var lastSampleUptimeMs = 0L
    private var pendingInitialSample = false

    /**
     * True when [currentScreen] came from an Activity class name. Such labels
     * are authoritative: a tap re-sample may only replace them with an explicit
     * paneTitle (a real Compose navigation signal) — window titles and heading
     * heuristics would flip-flop against the activity name on every re-sample.
     */
    private var labelFromActivity = false

    /** Call when a recording starts. Resets label state and arms the initial id-count sample. */
    fun startRecording() {
        currentScreen = null
        lastSampleUptimeMs = 0L
        pendingInitialSample = true
        labelFromActivity = false
    }

    fun onWindowStateChanged(
        event: AccessibilityEvent,
        uptimeMs: Long,
        wallClockMs: Long,
    ): RawCapture? {
        val className = event.className?.toString().orEmpty()
        maybeRunInitialSample(uptimeMs)
        return when {
            isToastClass(className) -> null

            isDialogClass(className) -> RawCapture.DialogOpen(
                uptimeMs = uptimeMs,
                wallClockMs = wallClockMs,
                screenName = currentScreen, // not a screen boundary
                confidence = Confidence.NORMAL,
                title = dialogTitle(event),
            )

            isActivityLike(className) -> {
                lastSampleUptimeMs = uptimeMs
                val simple = className.substringAfterLast('.')
                labelFromActivity = true
                if (simple == currentScreen) {
                    null
                } else {
                    currentScreen = simple
                    RawCapture.ScreenOpen(
                        uptimeMs = uptimeMs,
                        wallClockMs = wallClockMs,
                        screenName = simple,
                        confidence = Confidence.NORMAL,
                        label = simple,
                    )
                }
            }

            else -> {
                lastSampleUptimeMs = uptimeMs
                val label = fallbackLabel()
                if (label != null && label != currentScreen) {
                    currentScreen = label
                    labelFromActivity = false
                    RawCapture.ScreenOpen(
                        uptimeMs = uptimeMs,
                        wallClockMs = wallClockMs,
                        screenName = label,
                        confidence = Confidence.NORMAL,
                        label = label,
                    )
                } else {
                    null // retain current label
                }
            }
        }
    }

    /**
     * Lazy re-sample on tap when the last sample is stale (>1.5 s). Returns a
     * SCREEN_OPEN capture when the visible label actually changed.
     */
    fun maybeResampleOnTap(uptimeMs: Long, wallClockMs: Long): RawCapture? {
        maybeRunInitialSample(uptimeMs)
        if (uptimeMs - lastSampleUptimeMs <= RESAMPLE_AFTER_MS) return null
        lastSampleUptimeMs = uptimeMs
        val label = if (labelFromActivity) scanRoot().paneTitle else fallbackLabel()
        if (label == null || label == currentScreen) return null
        currentScreen = label
        labelFromActivity = false
        return RawCapture.ScreenOpen(
            uptimeMs = uptimeMs,
            wallClockMs = wallClockMs,
            screenName = label,
            confidence = Confidence.NORMAL,
            label = label,
        )
    }

    // ------------------------------------------------------------------ internals

    private fun maybeRunInitialSample(uptimeMs: Long) {
        if (!pendingInitialSample) return
        pendingInitialSample = false
        lastSampleUptimeMs = uptimeMs
        val scan = scanRoot()
        if (scan.visited > 0 && scan.idCount == 0) {
            onZeroElementIds?.invoke()
        }
    }

    private fun isActivityLike(className: String): Boolean =
        className.contains('.') &&
            !className.startsWith("android.widget.") &&
            !className.startsWith("android.view.") &&
            !isDialogClass(className) &&
            !isToastClass(className)

    private fun isDialogClass(className: String): Boolean {
        val simple = className.substringAfterLast('.')
        return simple == "Dialog" ||
            simple.endsWith("Dialog") || // AlertDialog, AppCompatDialog, BottomSheetDialog, ...
            simple.contains("PopupWindow")
    }

    private fun isToastClass(className: String): Boolean =
        className.startsWith("android.widget.Toast")

    private fun dialogTitle(event: AccessibilityEvent): String {
        val fromEvent = event.text
            .asSequence()
            .filterNotNull()
            .map { it.toString().trim() }
            .firstOrNull { it.isNotEmpty() }
        if (fromEvent != null) return fromEvent.take(MAX_LABEL_CHARS)
        return scanRoot().label ?: "Dialog"
    }

    private fun fallbackLabel(): String? = windowTitle() ?: scanRoot().label

    private fun windowTitle(): String? = try {
        windowsProvider()
            .asSequence()
            .filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
            .filter { it.isActive || it.isFocused }
            .mapNotNull { it.title?.toString()?.trim() }
            .firstOrNull { it.isNotEmpty() }
            ?.take(MAX_LABEL_CHARS)
    } catch (_: Throwable) {
        null
    }

    private class ScanResult(
        val label: String?,
        val visited: Int,
        val idCount: Int,
        val paneTitle: String? = null,
    )

    /**
     * Bounded BFS over the active root: depth<=12, <=150 nodes. Collects a
     * label candidate (paneTitle -> heading -> largest text in the top 25% of
     * the screen) and counts nodes exposing a viewIdResourceName.
     */
    private fun scanRoot(): ScanResult {
        val recycler = NodeRecycler()
        try {
            val rootRaw = rootProvider() ?: return ScanResult(null, 0, 0)
            val root = recycler.track(AccessibilityNodeInfoCompat.wrap(rootRaw))
                ?: return ScanResult(null, 0, 0)
            var visited = 0
            var idCount = 0
            var paneTitle: String? = null
            var headingText: String? = null
            var topText: String? = null
            var topTextHeight = -1
            val topLimit = screenHeightProvider() / 4
            val rect = Rect()
            val queue = ArrayDeque<Pair<AccessibilityNodeInfoCompat, Int>>()
            queue.add(root to 0)
            while (queue.isNotEmpty() && visited < MAX_SCAN_NODES) {
                val (node, depth) = queue.removeFirst()
                visited++
                if (!node.viewIdResourceName.isNullOrBlank()) idCount++
                if (paneTitle == null) {
                    val pane = node.paneTitle?.toString()?.trim()
                    if (!pane.isNullOrEmpty()) paneTitle = pane
                }
                val text = node.text?.toString()?.trim()
                if (!text.isNullOrEmpty()) {
                    if (headingText == null && node.isHeading) headingText = text
                    node.getBoundsInScreen(rect)
                    if (rect.top in 0 until topLimit && rect.height() > topTextHeight) {
                        topTextHeight = rect.height()
                        topText = text
                    }
                }
                if (depth < MAX_SCAN_DEPTH) {
                    for (i in 0 until node.childCount) {
                        if (visited + queue.size >= MAX_SCAN_NODES) break
                        val child = recycler.track(node.getChild(i)) ?: continue
                        queue.add(child to depth + 1)
                    }
                }
            }
            val label = (paneTitle ?: headingText ?: topText)?.take(MAX_LABEL_CHARS)
            return ScanResult(label, visited, idCount, paneTitle?.take(MAX_LABEL_CHARS))
        } catch (_: Throwable) {
            return ScanResult(null, 0, 0)
        } finally {
            recycler.recycleAll()
        }
    }

    private companion object {
        const val RESAMPLE_AFTER_MS = 1500L
        const val MAX_SCAN_DEPTH = 12
        const val MAX_SCAN_NODES = 150
        const val MAX_LABEL_CHARS = 60
    }
}
