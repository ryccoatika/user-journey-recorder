package com.ryccoatika.journeyrecorder.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ryccoatika.journeyrecorder.data.AppPrefs
import com.ryccoatika.journeyrecorder.data.ThemeMode
import com.ryccoatika.journeyrecorder.di.Graph
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val appPrefs: AppPrefs = Graph.appPrefs,
) : ViewModel() {
    val themeMode: StateFlow<ThemeMode> = appPrefs
        .observeThemeMode()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.SYSTEM)

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { appPrefs.setThemeMode(mode) }
    }
}
