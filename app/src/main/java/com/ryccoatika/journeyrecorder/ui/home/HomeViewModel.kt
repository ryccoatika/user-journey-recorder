package com.ryccoatika.journeyrecorder.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ryccoatika.journeyrecorder.data.JourneyRepository
import com.ryccoatika.journeyrecorder.data.db.JourneyDao
import com.ryccoatika.journeyrecorder.data.db.JourneyEntity
import com.ryccoatika.journeyrecorder.di.Graph
import com.ryccoatika.journeyrecorder.recorder.RecorderStateHolder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class JourneyListItem(
    val journey: JourneyEntity,
    val stepCount: Int,
)

sealed interface RecordingBannerState {
    data object Hidden : RecordingBannerState
    data class Visible(val targetPackage: String, val stepCount: Int) : RecordingBannerState
}

class HomeViewModel(
    private val repo: JourneyRepository = Graph.repository,
    dao: JourneyDao = Graph.journeyDao,
    state: RecorderStateHolder = Graph.recorderState,
) : ViewModel() {

    val items: StateFlow<List<JourneyListItem>> =
        combine(dao.observeJourneys(), dao.observeEventCounts()) { journeys, counts ->
            val countById = counts.associate { it.journeyId to it.cnt }
            journeys.map { journey ->
                JourneyListItem(journey = journey, stepCount = countById[journey.id] ?: 0)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val banner: StateFlow<RecordingBannerState> =
        state.state.flatMapLatest { recorderState ->
            when (recorderState) {
                is RecorderStateHolder.RecorderState.Idle ->
                    flowOf(RecordingBannerState.Hidden)
                is RecorderStateHolder.RecorderState.Recording ->
                    dao.observeEventCount(recorderState.journeyId).map { count ->
                        RecordingBannerState.Visible(
                            targetPackage = recorderState.targetPackage,
                            stepCount = count,
                        )
                    }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecordingBannerState.Hidden)

    fun stopRecording() {
        viewModelScope.launch { repo.finishRecording() }
    }

    fun rename(journeyId: Long, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { repo.rename(journeyId, trimmed) }
    }

    fun delete(journeyId: Long) {
        viewModelScope.launch { repo.delete(journeyId) }
    }
}
