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
        data class Recording(val journeyId: Long, val targetPackage: String) : RecorderState
    }

    private val _state = MutableStateFlow<RecorderState>(RecorderState.Idle)
    val state: StateFlow<RecorderState> = _state.asStateFlow()

    private val _serviceConnected = MutableStateFlow(false)
    val serviceConnected: StateFlow<Boolean> = _serviceConnected.asStateFlow()

    /** Capture is temporarily suspended while true (journey stays open). */
    private val _paused = MutableStateFlow(false)
    val paused: StateFlow<Boolean> = _paused.asStateFlow()

    internal fun setRecording(journeyId: Long, targetPackage: String) {
        _paused.value = false
        _state.value = RecorderState.Recording(journeyId, targetPackage)
    }

    internal fun setIdle() {
        _paused.value = false
        _state.value = RecorderState.Idle
    }

    internal fun setPaused(paused: Boolean) {
        _paused.value = paused
    }

    fun setServiceConnected(connected: Boolean) {
        _serviceConnected.value = connected
    }

    val current: RecorderState get() = _state.value
    val isPaused: Boolean get() = _paused.value
}