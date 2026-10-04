package com.ryccoatika.journeyrecorder.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.ryccoatika.journeyrecorder.data.JourneyRepository
import com.ryccoatika.journeyrecorder.data.db.JourneyDao
import com.ryccoatika.journeyrecorder.data.db.JourneyEntity
import com.ryccoatika.journeyrecorder.data.db.JourneyEventEntity
import com.ryccoatika.journeyrecorder.di.Graph
import com.ryccoatika.journeyrecorder.ui.DetailRoute
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val repo: JourneyRepository = Graph.repository,
    private val dao: JourneyDao = Graph.journeyDao,
) : ViewModel() {
    private val route: DetailRoute = savedStateHandle.toRoute()
    val journeyId: Long = route.journeyId

    val journey: StateFlow<JourneyEntity?> = dao
        .observeJourney(journeyId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val events: StateFlow<List<JourneyEventEntity>> = dao
        .observeEvents(journeyId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun redact(eventId: Long) {
        Graph.analytics.redactionUsed()
        viewModelScope.launch { dao.redactEvent(eventId) }
    }

    fun rename(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { repo.rename(journeyId, trimmed) }
    }

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            repo.delete(journeyId)
            onDeleted()
        }
    }
}
