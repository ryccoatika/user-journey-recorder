package com.ryccoatika.journeyrecorder.recorder

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.ryccoatika.journeyrecorder.R
import com.ryccoatika.journeyrecorder.data.db.Confidence
import com.ryccoatika.journeyrecorder.data.db.JourneyStatus
import com.ryccoatika.journeyrecorder.di.Graph
import com.ryccoatika.journeyrecorder.export.JourneyNotifier
import com.ryccoatika.journeyrecorder.recorder.bubble.BubbleController
import com.ryccoatika.journeyrecorder.ui.MainActivity
import com.ryccoatika.journeyrecorder.util.DeviceInfoProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Capture engine + XRecorder-style always-on launcher host. Gates events into
 * the [StepPipeline], and owns the [BubbleController] and the live recording
 * notification. WindowManager work stays on a Main.immediate scope.
 */
class JourneyAccessibilityService : AccessibilityService() {

    private var scope: CoroutineScope? = null
    private var bubble: BubbleController? = null
    private var notifJob: Job? = null
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

        bubble = BubbleController(
            service = this,
            stateHolder = Graph.recorderState,
            dao = Graph.journeyDao,
            onRecord = { startRecordingForeground() },
            onStop = { scope.launch { JourneyNotifier.stopAndShowResult(this@JourneyAccessibilityService) } },
            onDiscard = { discardCurrent() },
            onTogglePause = { Graph.repository.togglePause() },
            onHome = { goHome() },
            onSettings = { openInApp(EXTRA_OPEN_SETTINGS) },
            onExit = { scope.launch { Graph.appPrefs.setBubbleEnabled(false) } },
        )

        scope.launch { Graph.repository.recoverOrphans() }

        // Recording lifecycle: arm the screen tracker + live notification on
        // start, tear the notification down on stop.
        scope.launch {
            Graph.recorderState.state.collect { state ->
                if (state is RecorderStateHolder.RecorderState.Recording) {
                    leftTargetApp = false
                    screenTracker.startRecording()
                    startRecordingNotification(state.journeyId)
                } else {
                    stopRecordingNotification()
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
                    pipeline.submit(appMarker(getString(R.string.svc_marker_returned_to_target)))
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
                    pipeline.submit(appMarker(getString(R.string.svc_marker_left_target, pkg)))
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
            Toast.makeText(
                this@JourneyAccessibilityService,
                getString(R.string.svc_recording_discarded),
                Toast.LENGTH_SHORT,
            ).show()
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

    // ------------------------------------------------------ live recording notif

    private fun startRecordingNotification(journeyId: Long) {
        notifJob?.cancel()
        notifJob = scope?.launch {
            val journey = Graph.journeyDao.getJourney(journeyId) ?: return@launch
            while (true) {
                val count = Graph.journeyDao.countEvents(journeyId)
                val elapsed = Graph.recorderState.recordedElapsedMs(journey.startedAt) / 1000
                JourneyNotifier.showRecording(
                    this@JourneyAccessibilityService,
                    journey,
                    count,
                    elapsed,
                    Graph.recorderState.isPaused,
                )
                delay(1000)
            }
        }
    }

    private fun stopRecordingNotification() {
        notifJob?.cancel()
        notifJob = null
        JourneyNotifier.cancelRecording(this)
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
            getString(R.string.svc_no_element_ids),
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
        notifJob?.cancel()
        notifJob = null
        JourneyNotifier.cancelRecording(this)
        bubble?.dispose()
        bubble = null
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