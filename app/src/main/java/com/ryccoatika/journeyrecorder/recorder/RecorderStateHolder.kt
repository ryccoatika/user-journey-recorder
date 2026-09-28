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

    internal fun setRecording(journeyId: Long, targetPackage: String) {
        _state.value = RecorderState.Recording(journeyId, targetPackage)
    }

    internal fun setIdle() {
        _state.value = RecorderState.Idle
    }

    fun setServiceConnected(connected: Boolean) {
        _serviceConnected.value = connected
    }

    val current: RecorderState get() = _state.value
}