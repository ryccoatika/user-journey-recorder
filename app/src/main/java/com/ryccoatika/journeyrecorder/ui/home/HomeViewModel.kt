package com.ryccoatika.journeyrecorder.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ryccoatika.journeyrecorder.data.AppPrefs
import com.ryccoatika.journeyrecorder.data.JourneyRepository
import com.ryccoatika.journeyrecorder.data.db.JourneyDao
import com.ryccoatika.journeyrecorder.data.db.JourneyEntity
import com.ryccoatika.journeyrecorder.di.Graph
import com.ryccoatika.journeyrecorder.recorder.RecorderStateHolder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
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
    private val appPrefs: AppPrefs = Graph.appPrefs,
) : ViewModel() {

    val bubbleEnabled: StateFlow<Boolean> = appPrefs
        .observeBubbleEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun toggleBubble() {
        viewModelScope.launch { appPrefs.setBubbleEnabled(!bubbleEnabled.value) }
    }

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedIds: StateFlow<Set<Long>> = _selectedIds.asStateFlow()

    val items: StateFlow<List<JourneyListItem>> =
        combine(dao.observeJourneys(), dao.observeEventCounts(), _query) { journeys, counts, q ->
            val countById = counts.associate { it.journeyId to it.cnt }
            journeys
                .filter { it.matches(q) }
                .map { JourneyListItem(journey = it, stepCount = countById[it.id] ?: 0) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private fun JourneyEntity.matches(query: String): Boolean {
        if (query.isBlank()) return true
        val q = query.trim()
        return name.contains(q, ignoreCase = true) ||
            (targetAppLabel?.contains(q, ignoreCase = true) == true) ||
            targetPackage.contains(q, ignoreCase = true)
    }

    fun setQuery(q: String) {
        _query.value = q
    }

    // ---- multi-select ----

    fun toggleSelect(id: Long) {
        _selectedIds.update { if (id in it) it - id else it + id }
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
    }

    /** Replace the whole selection — used by long-press + drag range select. */
    fun setSelection(ids: Set<Long>) {
        _selectedIds.value = ids
    }

    fun deleteSelected() {
        val ids = _selectedIds.value.toList()
        clearSelection()
        viewModelScope.launch { ids.forEach { repo.delete(it) } }
    }

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
