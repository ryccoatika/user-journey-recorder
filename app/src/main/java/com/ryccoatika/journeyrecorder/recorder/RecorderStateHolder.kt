package com.ryccoatika.journeyrecorder.recorder

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Dumb state holder. Mutated ONLY by [com.ryccoatika.journeyrecorder.data.JourneyRepository];
 * everyone else (service, bubble, ViewModels) just observes.
 */
class RecorderStateHolder {
    sealed interface RecorderState {
        data object Idle : RecorderState

        data class Recording(
            val journeyId: Long,
            val targetPackage: String,
        ) : RecorderState
    }

    private val _state = MutableStateFlow<RecorderState>(RecorderState.Idle)
    val state: StateFlow<RecorderState> = _state.asStateFlow()

    private val _serviceConnected = MutableStateFlow(false)
    val serviceConnected: StateFlow<Boolean> = _serviceConnected.asStateFlow()

    /** Capture is temporarily suspended while true (journey stays open). */
    private val _paused = MutableStateFlow(false)
    val paused: StateFlow<Boolean> = _paused.asStateFlow()

    // Paused-time accounting so the elapsed timer freezes while paused.
    private var pausedAtMs = 0L
    private var accumulatedPausedMs = 0L

    internal fun setRecording(journeyId: Long, targetPackage: String) {
        _paused.value = false
        pausedAtMs = 0L
        accumulatedPausedMs = 0L
        _state.value = RecorderState.Recording(journeyId, targetPackage)
    }

    internal fun setIdle() {
        _paused.value = false
        pausedAtMs = 0L
        accumulatedPausedMs = 0L
        _state.value = RecorderState.Idle
    }

    internal fun setPaused(paused: Boolean) {
        if (paused == _paused.value) return
        val now = System.currentTimeMillis()
        if (paused) {
            pausedAtMs = now
        } else if (pausedAtMs != 0L) {
            accumulatedPausedMs += now - pausedAtMs
            pausedAtMs = 0L
        }
        _paused.value = paused
    }

    /**
     * Elapsed recording time excluding paused spans. Freezes while paused, so
     * the bubble/notification timer stops counting on pause.
     */
    fun recordedElapsedMs(startedAt: Long): Long {
        val now = System.currentTimeMillis()
        return (now - startedAt - totalPausedMs()).coerceAtLeast(0L)
    }

    /** Total paused time so far (including an in-progress pause). */
    fun totalPausedMs(): Long =
        accumulatedPausedMs + if (_paused.value) System.currentTimeMillis() - pausedAtMs else 0L

    fun setServiceConnected(connected: Boolean) {
        _serviceConnected.value = connected
    }

    val current: RecorderState get() = _state.value
    val isPaused: Boolean get() = _paused.value
}
