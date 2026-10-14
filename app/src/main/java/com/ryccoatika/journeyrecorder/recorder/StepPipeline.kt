package com.ryccoatika.journeyrecorder.recorder

import com.ryccoatika.journeyrecorder.data.RecordingPipeline
import com.ryccoatika.journeyrecorder.data.db.Confidence
import com.ryccoatika.journeyrecorder.data.db.EventType
import com.ryccoatika.journeyrecorder.data.db.JourneyDao
import com.ryccoatika.journeyrecorder.data.db.JourneyEventEntity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Single-consumer ordering/coalescing pipeline. Producers ([submit], [begin],
 * [flushAndEnd]) only enqueue messages; ALL mutable state (sequence counter,
 * coalescers, dedup, masking guard) is owned by one consumer coroutine, so no
 * locking is needed. Every finalized step is written through to Room
 * immediately.
 *
 * All coalescer timers are messages routed back through the same channel,
 * which keeps ordering deterministic and the class JVM-testable with a
 * virtual-time dispatcher.
 */
class StepPipeline(
    private val dao: JourneyDao,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : RecordingPipeline {
    private data class TextKey(
        val windowId: Int,
        val fieldKey: String,
    )

    private class PendingText(
        val key: TextKey,
        var element: ElementInfo,
        var screenName: String?,
        var latestText: String?, // null while masked — masked values never enter the map
        var masked: Boolean,
        val initialBeforeNonEmpty: Boolean,
        var wallClockMs: Long,
        var confidence: Confidence,
        var generation: Long = 0,
        var timer: Job? = null,
    )

    private class PendingScroll(
        val nodeKey: String,
        var element: ElementInfo,
        var screenName: String?,
        var sumDeltaX: Int = 0,
        var sumDeltaY: Int = 0,
        var hasDeltas: Boolean = false,
        var firstFromIndex: Int? = null,
        var lastToIndex: Int? = null,
        var wallClockMs: Long,
        var confidence: Confidence,
        var generation: Long = 0,
        var timer: Job? = null,
    )

    private sealed interface Msg {
        data class Begin(
            val journeyId: Long,
        ) : Msg

        data class Cap(
            val capture: RawCapture,
        ) : Msg

        data class TextTimer(
            val key: TextKey,
            val generation: Long,
        ) : Msg

        data class ScrollTimer(
            val nodeKey: String,
            val generation: Long,
        ) : Msg

        class Barrier(
            val done: CompletableDeferred<Unit>,
        ) : Msg
    }

    private val channel = Channel<Msg>(Channel.UNLIMITED)
    private val consumerScope = scope

    // ---- consumer-owned state (touched only inside the consumer coroutine) ----
    private var journeyId: Long? = null
    private var sequence = 0
    private var guard = SensitiveTextGuard()
    private val pendingTexts = LinkedHashMap<TextKey, PendingText>()
    private val pendingScrolls = LinkedHashMap<String, PendingScroll>()

    // Pipeline-global timer generation: a stale timer message can never match a
    // NEW pending instance created under the same key (per-instance counters
    // would both restart from 1).
    private var timerGeneration = 0L
    private var lastClickKey: String? = null
    private var lastClickUptimeMs = Long.MIN_VALUE / 2

    init {
        consumerScope.launch {
            for (msg in channel) {
                try {
                    handle(msg)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // One failed insert must never kill the consumer (that would
                    // hang every later flushAndEnd barrier). Drop the step.
                    if (msg is Msg.Barrier) msg.done.complete(Unit)
                }
            }
        }
    }

    override fun begin(journeyId: Long) {
        channel.trySend(Msg.Begin(journeyId))
    }

    /** Called synchronously from the a11y callback thread. Never blocks. */
    fun submit(capture: RawCapture) {
        channel.trySend(Msg.Cap(capture))
    }

    /**
     * Barrier: everything submitted before this call is drained, all pending
     * coalescers flushed and persisted, then the journey is closed.
     */
    override suspend fun flushAndEnd() {
        val done = CompletableDeferred<Unit>()
        channel.trySend(Msg.Barrier(done))
        done.await()
    }

    // ------------------------------------------------------------------ consumer

    private suspend fun handle(msg: Msg) {
        when (msg) {
            is Msg.Begin -> {
                flushAllPending() // safety: leftovers of a previous journey
                journeyId = msg.journeyId
                sequence = 0
                guard = SensitiveTextGuard()
                lastClickKey = null
                lastClickUptimeMs = Long.MIN_VALUE / 2
            }

            is Msg.Cap -> {
                if (journeyId != null) onCapture(msg.capture)
            }

            is Msg.TextTimer -> {
                pendingTexts[msg.key]
                    ?.takeIf { it.generation == msg.generation }
                    ?.let { flushText(it) }
            }

            is Msg.ScrollTimer -> {
                pendingScrolls[msg.nodeKey]
                    ?.takeIf { it.generation == msg.generation }
                    ?.let { flushScroll(it) }
            }

            is Msg.Barrier -> {
                flushAllPending()
                journeyId = null
                msg.done.complete(Unit)
            }
        }
    }

    private suspend fun onCapture(c: RawCapture) {
        if (c is RawCapture.Text) {
            onText(c)
            return
        }
        // Any non-TEXT capture flushes pending text BEFORE it is processed.
        flushAllTexts()
        if (c is RawCapture.Scroll) {
            onScroll(c)
            return
        }
        // Discrete steps also flush pending scrolls to keep sequence order sane.
        flushAllScrolls()
        when (c) {
            is RawCapture.FocusChange -> Unit

            // flush trigger only, never persisted

            is RawCapture.Click -> onClick(c)

            is RawCapture.Select -> onSelect(c)

            is RawCapture.ScreenOpen -> insertStep(
                eventType = EventType.SCREEN_OPEN,
                wallClockMs = c.wallClockMs,
                screenName = c.screenName,
                confidence = c.confidence,
            )

            is RawCapture.DialogOpen -> insertStep(
                eventType = EventType.SCREEN_OPEN,
                wallClockMs = c.wallClockMs,
                screenName = c.screenName, // unchanged — dialogs are not screen boundaries
                elementText = "Dialog opened: ${c.title}",
                confidence = c.confidence,
            )

            is RawCapture.SystemDialogClick -> insertStep(
                eventType = EventType.SYSTEM_DIALOG,
                wallClockMs = c.wallClockMs,
                screenName = c.screenName,
                elementText = c.message,
                className = c.packageName,
                confidence = c.confidence,
            )

            is RawCapture.AppMarker -> insertStep(
                eventType = EventType.APP_MARKER,
                wallClockMs = c.wallClockMs,
                screenName = c.screenName,
                elementText = c.message,
                confidence = c.confidence,
            )

            is RawCapture.Text, is RawCapture.Scroll -> Unit // handled above
        }
    }

    // ---------------------------------------------------------------- clicks

    private suspend fun onClick(c: RawCapture.Click) {
        val key = c.element.stableKey
        if (!c.longClick && key != null && key == lastClickKey &&
            c.uptimeMs - lastClickUptimeMs < CLICK_DEDUP_MS
        ) {
            return // identical CLICK pair within 100 ms — drop the echo
        }
        lastClickKey = key
        lastClickUptimeMs = c.uptimeMs
        insertStep(
            eventType = if (c.longClick) EventType.LONG_CLICK else EventType.CLICK,
            wallClockMs = c.wallClockMs,
            screenName = c.screenName,
            element = sanitizeTarget(c.element),
            confidence = c.confidence,
        )
    }

    /**
     * A tapped editable field's node text IS its current content (a card
     * number, an email, …) — never persist it on CLICK/SELECT rows. Label the
     * field by its hint instead.
     */
    private fun sanitizeTarget(element: ElementInfo): ElementInfo =
        if (element.isEditable || element.isPassword) {
            element.copy(text = element.hint)
        } else {
            element
        }

    private suspend fun onSelect(c: RawCapture.Select) {
        val key = c.element.stableKey
        if (key != null && key == lastClickKey &&
            c.uptimeMs - lastClickUptimeMs in 0 until SELECT_AFTER_CLICK_MS
        ) {
            return // SELECT echo of the CLICK we already recorded
        }
        insertStep(
            eventType = EventType.SELECT,
            wallClockMs = c.wallClockMs,
            screenName = c.screenName,
            element = sanitizeTarget(c.element),
            confidence = c.confidence,
        )
    }

    // ------------------------------------------------------------------ text

    private suspend fun onText(c: RawCapture.Text) {
        val key = TextKey(c.windowId, c.fieldKey ?: UNKNOWN_FIELD)

        // A different field key flushes every other pending text first.
        val others = pendingTexts.values.filter { it.key != key }
        for (other in others) flushText(other)

        val mask = guard.shouldMask(
            fieldKey = c.fieldKey,
            isPassword = c.element.isPassword,
            identityStrings = listOf(
                c.element.elementId,
                c.element.hint,
                c.element.contentDescription,
                c.element.ancestorAnchor,
            ),
        )

        val existing = pendingTexts[key]
        if (existing == null) {
            val pending = PendingText(
                key = key,
                element = c.element,
                screenName = c.screenName,
                latestText = if (mask) null else c.text,
                masked = mask,
                initialBeforeNonEmpty = c.beforeTextNonEmpty,
                wallClockMs = c.wallClockMs,
                confidence = c.confidence,
            )
            pendingTexts[key] = pending
            restartTextTimer(pending)
        } else {
            existing.element = c.element
            existing.screenName = c.screenName ?: existing.screenName
            existing.masked = existing.masked || mask
            existing.latestText = if (existing.masked) null else c.text
            existing.wallClockMs = c.wallClockMs
            if (c.confidence == Confidence.LOW) existing.confidence = Confidence.LOW
            restartTextTimer(existing)
        }
    }

    private fun restartTextTimer(pending: PendingText) {
        pending.generation = ++timerGeneration
        pending.timer?.cancel()
        val key = pending.key
        val generation = pending.generation
        pending.timer = consumerScope.launch {
            delay(TEXT_QUIET_MS)
            channel.trySend(Msg.TextTimer(key, generation))
        }
    }

    private suspend fun flushText(pending: PendingText) {
        pendingTexts.remove(pending.key)
        pending.timer?.cancel()
        val jid = journeyId ?: return

        var masked = pending.masked
        val finalText = pending.latestText
        if (!masked && !finalText.isNullOrEmpty() && guard.maskByValue(finalText)) {
            masked = true
            guard.markSensitive(pending.key.fieldKey)
        }

        val isClear = !masked && finalText.isNullOrEmpty() && pending.initialBeforeNonEmpty
        if (!masked && finalText.isNullOrEmpty() && !isClear) return // nothing typed

        insertStep(
            journeyIdOverride = jid,
            eventType = EventType.TEXT_INPUT,
            wallClockMs = pending.wallClockMs,
            screenName = pending.screenName,
            element = pending.element,
            // never element.text here — for editable nodes it IS the typed value
            elementText = pending.element.hint,
            typedText = when {
                masked -> null

                isClear -> ""

                // explicit "Clear text" step
                else -> finalText
            },
            masked = masked,
            confidence = pending.confidence,
        )
    }

    private suspend fun flushAllTexts() {
        while (pendingTexts.isNotEmpty()) {
            flushText(pendingTexts.values.first())
        }
    }

    // ---------------------------------------------------------------- scrolls

    private suspend fun onScroll(c: RawCapture.Scroll) {
        val existing = pendingScrolls[c.nodeKey]
        val pending = if (existing == null) {
            PendingScroll(
                nodeKey = c.nodeKey,
                element = c.element,
                screenName = c.screenName,
                wallClockMs = c.wallClockMs,
                confidence = c.confidence,
            ).also { pendingScrolls[c.nodeKey] = it }
        } else {
            existing
        }
        if (c.deltaX != null || c.deltaY != null) {
            pending.hasDeltas = true
            pending.sumDeltaX += c.deltaX ?: 0
            pending.sumDeltaY += c.deltaY ?: 0
        } else {
            if (pending.firstFromIndex == null) pending.firstFromIndex = c.fromIndex
            if (c.toIndex != null) pending.lastToIndex = c.toIndex
        }
        pending.element = c.element
        pending.screenName = c.screenName ?: pending.screenName
        pending.wallClockMs = c.wallClockMs
        if (c.confidence == Confidence.LOW) pending.confidence = Confidence.LOW
        restartScrollTimer(pending)
    }

    private fun restartScrollTimer(pending: PendingScroll) {
        pending.generation = ++timerGeneration
        pending.timer?.cancel()
        val nodeKey = pending.nodeKey
        val generation = pending.generation
        pending.timer = consumerScope.launch {
            delay(SCROLL_QUIET_MS)
            channel.trySend(Msg.ScrollTimer(nodeKey, generation))
        }
    }

    private suspend fun flushScroll(pending: PendingScroll) {
        pendingScrolls.remove(pending.nodeKey)
        pending.timer?.cancel()
        journeyId ?: return
        insertStep(
            eventType = EventType.SCROLL,
            wallClockMs = pending.wallClockMs,
            screenName = pending.screenName,
            element = pending.element,
            // for SCROLL rows elementText carries the direction/range hint,
            // which is what MarkdownGenerator renders after "**Scroll**"
            elementText = scrollDetail(pending),
            confidence = pending.confidence,
        )
    }

    private fun scrollDetail(pending: PendingScroll): String {
        if (pending.hasDeltas) {
            val parts = buildList {
                val dy = pending.sumDeltaY
                val dx = pending.sumDeltaX
                if (dy > 0) add("down ${dy}px")
                if (dy < 0) add("up ${-dy}px")
                if (dx > 0) add("right ${dx}px")
                if (dx < 0) add("left ${-dx}px")
            }
            if (parts.isNotEmpty()) return parts.joinToString(", ")
        }
        val from = pending.firstFromIndex
        val to = pending.lastToIndex
        if (from != null && to != null) return "items $from to $to"
        return "scrolled"
    }

    private suspend fun flushAllScrolls() {
        while (pendingScrolls.isNotEmpty()) {
            flushScroll(pendingScrolls.values.first())
        }
    }

    private suspend fun flushAllPending() {
        flushAllTexts()
        flushAllScrolls()
    }

    // ----------------------------------------------------------------- insert

    private suspend fun insertStep(
        eventType: EventType,
        wallClockMs: Long,
        screenName: String?,
        element: ElementInfo? = null,
        elementText: String? = element?.text,
        className: String? = element?.className,
        typedText: String? = null,
        masked: Boolean = false,
        confidence: Confidence = Confidence.NORMAL,
        journeyIdOverride: Long? = null,
    ) {
        val jid = journeyIdOverride ?: journeyId ?: return
        dao.insertEvent(
            JourneyEventEntity(
                journeyId = jid,
                sequence = sequence++,
                wallClockMs = wallClockMs,
                eventType = eventType,
                screenName = screenName,
                elementId = element?.elementId,
                elementText = elementText,
                contentDesc = element?.contentDescription,
                className = className,
                bounds = element?.bounds,
                typedText = typedText,
                masked = masked,
                confidence = confidence,
                collectionIndex = element?.collectionIndex,
            ),
        )
    }

    private companion object {
        const val TEXT_QUIET_MS = 900L
        const val SCROLL_QUIET_MS = 500L
        const val CLICK_DEDUP_MS = 100L
        const val SELECT_AFTER_CLICK_MS = 300L
        const val UNKNOWN_FIELD = "<unknown>"
    }
}
