package com.ryccoatika.journeyrecorder.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ryccoatika.journeyrecorder.data.ThemeMode
import com.ryccoatika.journeyrecorder.di.Graph
import com.ryccoatika.journeyrecorder.ui.theme.JourneyRecorderTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeMode by Graph.appPrefs
                .observeThemeMode()
                .collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
            val darkTheme = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            JourneyRecorderTheme(darkTheme = darkTheme) {
                AppNav()
            }
        }
    }
}