package com.ryccoatika.journeyrecorder.recorder

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.ryccoatika.journeyrecorder.data.db.Confidence
import com.ryccoatika.journeyrecorder.data.db.JourneyStatus
import com.ryccoatika.journeyrecorder.di.Graph
import com.ryccoatika.journeyrecorder.export.ExportManager
import com.ryccoatika.journeyrecorder.export.ExportResult
import com.ryccoatika.journeyrecorder.export.JourneyNotifier
import com.ryccoatika.journeyrecorder.recorder.bubble.BubbleController
import com.ryccoatika.journeyrecorder.recorder.bubble.ResultCardController
import com.ryccoatika.journeyrecorder.ui.MainActivity
import com.ryccoatika.journeyrecorder.util.DeviceInfoProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Capture engine + XRecorder-style always-on launcher host. Gates events into
 * the [StepPipeline], and owns the [BubbleController], the result card, and the
 * persistent controls notification. WindowManager work stays on a
 * Main.immediate scope.
 */
class JourneyAccessibilityService : AccessibilityService() {

    private var scope: CoroutineScope? = null
    private var bubble: BubbleController? = null
    private var resultCard: ResultCardController? = null
    private lateinit var screenTracker: ScreenTracker
    private lateinit var interpreter: EventInterpreter
    private var leftTargetApp = false
    private var tornDown = false

    /** App on screen right now — used by "record the current app". */
    private var lastForegroundPackage: String? = null
    private var launcherPackages: Set<String> = emptySet()

    override fun onServiceConnected() {
        super.onServiceConnected()
        tornDown = false
        screenTracker = ScreenTracker(
            rootProvider = { rootInActiveWindow },
            windowsProvider = { windows ?: emptyList() },
            screenHeightProvider = { resources.displayMetrics.heightPixels },
        )
        screenTracker.onZeroElementIds = { onZeroElementIds() }
        interpreter = EventInterpreter(screenTracker)
        launcherPackages = resolveLauncherPackages()

        Graph.recorderState.setServiceConnected(true)

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        this.scope = scope

        resultCard = ResultCardController(this)
        bubble = BubbleController(
            service = this,
            stateHolder = Graph.recorderState,
            dao = Graph.journeyDao,
            onRecord = { startRecordingForeground() },
            onStop = { scope.launch { finishAndPresentResult() } },
            onDiscard = { discardCurrent() },
            onTogglePause = { Graph.repository.togglePause() },
            onHome = { goHome() },
            onSettings = { openInApp(EXTRA_OPEN_SETTINGS) },
            onExit = { scope.launch { Graph.appPrefs.setBubbleEnabled(false) } },
        )

        scope.launch { Graph.repository.recoverOrphans() }

        // Recording lifecycle: arm the screen tracker when a recording starts.
        scope.launch {
            Graph.recorderState.state.collect { state ->
                if (state is RecorderStateHolder.RecorderState.Recording) {
                    leftTargetApp = false
                    screenTracker.startRecording()
                }
            }
        }

        // Bubble visibility follows the enabled pref and re-renders on state change.
        scope.launch {
            combine(
                Graph.appPrefs.observeBubbleEnabled(),
                Graph.recorderState.state,
            ) { enabled, _ -> enabled }
                .collect { enabled -> if (enabled) bubble?.enable() else bubble?.disable() }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val pkg = event.packageName?.toString() ?: return

        // Track the foreground app at all times (even when idle) so the launcher
        // can record "the current app". Ignore our own overlay and transient
        // system windows.
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            pkg != OWN_PACKAGE && pkg != packageName &&
            !isTransientSystemPackage(pkg) && pkg !in SYSTEM_ALLOWLIST
        ) {
            lastForegroundPackage = pkg
        }

        val state = Graph.recorderState.current
            as? RecorderStateHolder.RecorderState.Recording ?: return
        // Capture suspended — keep the journey open but record nothing.
        if (Graph.recorderState.isPaused) return
        // Own package is ALWAYS excluded — bubble taps are never recorded.
        if (pkg == OWN_PACKAGE || pkg == packageName) return

        val pipeline = Graph.stepPipeline
        when {
            pkg == state.targetPackage -> {
                if (leftTargetApp &&
                    event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                ) {
                    leftTargetApp = false
                    pipeline.submit(appMarker("Returned to target app"))
                }
                if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
                    screenTracker.maybeResampleOnTap(
                        SystemClock.uptimeMillis(),
                        System.currentTimeMillis(),
                    )?.let(pipeline::submit)
                }
                interpreter.extract(event)?.let(pipeline::submit)
            }

            pkg in SYSTEM_ALLOWLIST ->
                interpreter.extractSystemDialog(event)?.let(pipeline::submit)

            else -> {
                if (!leftTargetApp &&
                    event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
                    !isTransientSystemPackage(pkg)
                ) {
                    leftTargetApp = true
                    pipeline.submit(appMarker("Left target app ($pkg)"))
                }
            }
        }
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        teardown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        teardown()
        super.onDestroy()
    }

    // --------------------------------------------------------------- launcher ops

    private fun startRecordingForeground() {
        if (Graph.recorderState.current is RecorderStateHolder.RecorderState.Recording) return
        // Live probe first (most reliable), then the tracked value.
        val probed = runCatching { rootInActiveWindow?.packageName?.toString() }.getOrNull()
            ?.takeIf { it != packageName && it != OWN_PACKAGE && !isTransientSystemPackage(it) }
        val pkg = probed ?: lastForegroundPackage
        if (pkg == null || pkg in launcherPackages || pkg == packageName) {
            // No recordable app on screen — send the user to the picker.
            openInApp(EXTRA_OPEN_SETUP)
            return
        }
        scope?.launch {
            val pm = packageManager
            val label = runCatching {
                pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
            }.getOrNull()
            val version = runCatching {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(pkg, 0).versionName
            }.getOrNull()
            runCatching {
                Graph.repository.startRecording(
                    targetPackage = pkg,
                    targetAppLabel = label,
                    appVersionName = version,
                    deviceInfo = DeviceInfoProvider.deviceInfo(),
                    androidVersion = DeviceInfoProvider.androidVersion(),
                )
            }
        }
    }

    private fun discardCurrent() {
        scope?.launch {
            Graph.repository.discardRecording()
            Toast.makeText(this@JourneyAccessibilityService, "Recording discarded", Toast.LENGTH_SHORT)
                .show()
        }
    }

    /** Open the app's own home (journey list), not the Android launcher. */
    private fun goHome() = openInApp(EXTRA_OPEN_HOME)

    private fun openInApp(extra: String) {
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(extra, true)
            },
        )
    }

    // --------------------------------------------------------------- result flow

    /** Stop -> save -> surface the result (floating card + notification). */
    private suspend fun finishAndPresentResult() {
        val recording = Graph.recorderState.current
            as? RecorderStateHolder.RecorderState.Recording ?: return
        Graph.repository.finishRecording()

        val journey = Graph.journeyDao.getJourney(recording.journeyId) ?: return
        val events = Graph.journeyDao.getEvents(recording.journeyId)
        val duration = ((journey.endedAt ?: journey.startedAt) - journey.startedAt) / 1000
        val subtitle = "${events.size} steps · ${duration}s · " +
            (journey.targetAppLabel ?: journey.targetPackage)

        resultCard?.show(
            title = journey.name,
            subtitle = subtitle,
            actions = ResultCardController.Actions(
                onOpen = { openJourney(journey.id) },
                onShare = {
                    JourneyNotifier.buildShareChooser(this, journey, events)?.let(::startActivity)
                },
                onSave = {
                    scope?.launch {
                        val result = withContext(Dispatchers.IO) {
                            ExportManager(this@JourneyAccessibilityService)
                                .saveToDownloads(journey, events)
                        }
                        val message = when (result) {
                            is ExportResult.Saved -> "Saved to ${result.displayPath}"
                            is ExportResult.Failed -> result.message
                        }
                        Toast.makeText(this@JourneyAccessibilityService, message, Toast.LENGTH_SHORT)
                            .show()
                    }
                },
                onDelete = {
                    scope?.launch {
                        Graph.repository.delete(journey.id)
                        JourneyNotifier.cancel(this@JourneyAccessibilityService, journey.id)
                        Toast.makeText(
                            this@JourneyAccessibilityService,
                            "Journey deleted",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                },
            ),
        )

        JourneyNotifier.showResult(this, journey, events)
    }

    private fun openJourney(journeyId: Long) {
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(JourneyNotifier.EXTRA_OPEN_JOURNEY_ID, journeyId)
            },
        )
    }

    // ------------------------------------------------------------------ helpers

    private fun resolveLauncherPackages(): Set<String> = runCatching {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        packageManager.queryIntentActivities(intent, 0)
            .mapNotNull { it.activityInfo?.packageName }
            .toSet()
    }.getOrDefault(emptySet())

    private fun isTransientSystemPackage(pkg: String): Boolean =
        pkg == "com.android.systemui" ||
            pkg.contains("inputmethod", ignoreCase = true) ||
            pkg.contains("keyboard", ignoreCase = true)

    private fun onZeroElementIds() {
        val state = Graph.recorderState.current
            as? RecorderStateHolder.RecorderState.Recording ?: return
        Toast.makeText(
            this,
            "This app exposes no element IDs - steps will use text and bounds locators",
            Toast.LENGTH_LONG,
        ).show()
        scope?.launch { Graph.journeyDao.setNoElementIds(state.journeyId, true) }
    }

    private fun appMarker(message: String) = RawCapture.AppMarker(
        uptimeMs = SystemClock.uptimeMillis(),
        wallClockMs = System.currentTimeMillis(),
        screenName = screenTracker.currentScreen,
        confidence = Confidence.NORMAL,
        message = message,
    )

    private fun teardown() {
        if (tornDown) return
        tornDown = true
        Graph.recorderState.setServiceConnected(false)
        if (Graph.recorderState.current is RecorderStateHolder.RecorderState.Recording) {
            // Service is dying mid-recording: close the journey as RECOVERED on
            // a scope that cannot be cancelled from under us.
            CoroutineScope(Dispatchers.Default).launch(NonCancellable) {
                Graph.repository.finishRecording(JourneyStatus.RECOVERED)
            }
        }
        bubble?.dispose()
        bubble = null
        resultCard?.dispose()
        resultCard = null
        scope?.cancel()
        scope = null
    }

    companion object {
        const val EXTRA_OPEN_SETUP = "open_setup"
        const val EXTRA_OPEN_SETTINGS = "open_settings"
        const val EXTRA_OPEN_HOME = "open_home"

        private const val OWN_PACKAGE = "com.ryccoatika.journeyrecorder"

        private val SYSTEM_ALLOWLIST = listOf(
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
            "com.android.intentresolver",
            "android",
        )
    }
}