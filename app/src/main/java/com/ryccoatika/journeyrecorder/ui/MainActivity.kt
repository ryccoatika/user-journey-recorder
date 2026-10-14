package com.ryccoatika.journeyrecorder.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
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
            // The default enableEdgeToEdge() reads the SYSTEM night mode, so a
            // forced in-app theme would get unreadable bar icons. Re-apply with
            // the resolved theme whenever it changes.
            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(
                        Color.TRANSPARENT,
                        Color.TRANSPARENT,
                    ) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(
                        LIGHT_NAV_SCRIM,
                        DARK_NAV_SCRIM,
                    ) { darkTheme },
                )
                onDispose {}
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

// Same scrims androidx.activity uses for 3-button navigation by default.
private val LIGHT_NAV_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
private val DARK_NAV_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)

data class DeepLink(
    val journeyId: Long? = null,
    val openSetup: Boolean = false,
    val openSettings: Boolean = false,
    val openHome: Boolean = false,
)
