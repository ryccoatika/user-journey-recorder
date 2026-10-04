package com.ryccoatika.journeyrecorder.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ryccoatika.journeyrecorder.data.ThemeMode
import com.ryccoatika.journeyrecorder.di.Graph
import com.ryccoatika.journeyrecorder.export.JourneyNotifier
import com.ryccoatika.journeyrecorder.recorder.JourneyAccessibilityService
import com.ryccoatika.journeyrecorder.ui.theme.JourneyRecorderTheme

class MainActivity : ComponentActivity() {
    /** One-shot deep links from the bubble / notification. */
    private var pending by mutableStateOf(DeepLink())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pending = intent.toDeepLink()
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
                AppNav(
                    deepLink = pending,
                    onDeepLinkHandled = { pending = DeepLink() },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pending = intent.toDeepLink()
    }

    private fun Intent.toDeepLink() = DeepLink(
        journeyId = getLongExtra(JourneyNotifier.EXTRA_OPEN_JOURNEY_ID, -1L).takeIf { it > 0 },
        openSetup = getBooleanExtra(JourneyAccessibilityService.EXTRA_OPEN_SETUP, false),
        openSettings = getBooleanExtra(JourneyAccessibilityService.EXTRA_OPEN_SETTINGS, false),
        openHome = getBooleanExtra(JourneyAccessibilityService.EXTRA_OPEN_HOME, false),
    )
}

data class DeepLink(
    val journeyId: Long? = null,
    val openSetup: Boolean = false,
    val openSettings: Boolean = false,
    val openHome: Boolean = false,
)
