package com.ryccoatika.journeyrecorder.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ryccoatika.journeyrecorder.di.Graph
import com.ryccoatika.journeyrecorder.ui.detail.DetailScreen
import com.ryccoatika.journeyrecorder.ui.guide.GuideScreen
import com.ryccoatika.journeyrecorder.ui.home.HomeScreen
import com.ryccoatika.journeyrecorder.ui.onboarding.OnboardingScreen
import com.ryccoatika.journeyrecorder.ui.settings.ContactDeveloperScreen
import com.ryccoatika.journeyrecorder.ui.settings.SettingsScreen
import com.ryccoatika.journeyrecorder.ui.setup.SetupScreen
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
object HomeRoute

@Serializable
object SetupRoute

@Serializable
data class DetailRoute(val journeyId: Long)

@Serializable
object SettingsRoute

@Serializable
object GuideRoute

@Serializable
object ContactDeveloperRoute

@Serializable
object OnboardingRoute

// Material 3 emphasized easing pair — natural settle on enter, quick launch on exit.
private val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
private val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

private const val ENTER_MS = 350
private const val EXIT_MS = 250

@Composable
fun AppNav(
    deepLink: DeepLink = DeepLink(),
    onDeepLinkHandled: () -> Unit = {},
) {
    // First-launch onboarding gate. null = pref not read yet (keep the current
    // frame; DataStore resolves within a frame or two).
    val onboardingSeen by Graph.appPrefs
        .observeOnboardingSeen()
        .collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()

    when (onboardingSeen) {
        null -> Unit
        false -> OnboardingScreen(
            onFinish = { scope.launch { Graph.appPrefs.setOnboardingSeen() } },
        )
        true -> AppNavHost(deepLink = deepLink, onDeepLinkHandled = onDeepLinkHandled)
    }
}

@Composable
private fun AppNavHost(
    deepLink: DeepLink,
    onDeepLinkHandled: () -> Unit,
) {
    val navController = rememberNavController()

    // Deep links from the bubble / notification.
    androidx.compose.runtime.LaunchedEffect(deepLink) {
        when {
            deepLink.journeyId != null -> navController.navigate(DetailRoute(deepLink.journeyId))
            deepLink.openSetup -> navController.navigate(SetupRoute)
            deepLink.openSettings -> navController.navigate(SettingsRoute)
            deepLink.openHome ->
                navController.popBackStack(HomeRoute, inclusive = false)
            else -> return@LaunchedEffect
        }
        onDeepLinkHandled()
    }
    // Shared-axis X: forward pushes in from the right while the old screen
    // parallax-slides left; back reverses the motion. Fades keep the overlap
    // from ever looking like a hard cut.
    NavHost(
        navController = navController,
        startDestination = HomeRoute,
        enterTransition = {
            slideInHorizontally(
                animationSpec = tween(ENTER_MS, easing = EmphasizedDecelerate),
            ) { fullWidth -> fullWidth / 3 } +
                fadeIn(animationSpec = tween(ENTER_MS / 2))
        },
        exitTransition = {
            slideOutHorizontally(
                animationSpec = tween(EXIT_MS, easing = EmphasizedAccelerate),
            ) { fullWidth -> -fullWidth / 4 } +
                fadeOut(animationSpec = tween(EXIT_MS))
        },
        popEnterTransition = {
            slideInHorizontally(
                animationSpec = tween(ENTER_MS, easing = EmphasizedDecelerate),
            ) { fullWidth -> -fullWidth / 4 } +
                fadeIn(animationSpec = tween(ENTER_MS / 2))
        },
        popExitTransition = {
            slideOutHorizontally(
                animationSpec = tween(EXIT_MS, easing = EmphasizedAccelerate),
            ) { fullWidth -> fullWidth / 3 } +
                fadeOut(animationSpec = tween(EXIT_MS))
        },
    ) {
        composable<HomeRoute> {
            HomeScreen(
                onNewRecording = { navController.navigate(SetupRoute) },
                onOpenJourney = { journeyId -> navController.navigate(DetailRoute(journeyId)) },
                onOpenSettings = { navController.navigate(SettingsRoute) },
            )
        }
        composable<SetupRoute> {
            SetupScreen(
                onBack = { navController.popBackStack() },
                onRecordingStarted = { navController.popBackStack(HomeRoute, inclusive = false) },
            )
        }
        composable<DetailRoute> {
            DetailScreen(
                onBack = { navController.popBackStack() },
                onOpenGuide = { navController.navigate(GuideRoute) },
            )
        }
        composable<SettingsRoute> {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenGuide = { navController.navigate(GuideRoute) },
                onOpenContactDeveloper = { navController.navigate(ContactDeveloperRoute) },
                onOpenOnboarding = { navController.navigate(OnboardingRoute) },
            )
        }
        composable<GuideRoute> {
            GuideScreen(onBack = { navController.popBackStack() })
        }
        composable<ContactDeveloperRoute> {
            ContactDeveloperScreen(onBack = { navController.popBackStack() })
        }
        composable<OnboardingRoute> {
            OnboardingScreen(onFinish = { navController.popBackStack() })
        }
    }
}