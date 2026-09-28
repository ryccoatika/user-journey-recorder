package com.ryccoatika.journeyrecorder.recorder

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.ryccoatika.journeyrecorder.data.db.Confidence
import com.ryccoatika.journeyrecorder.data.db.JourneyStatus
import com.ryccoatika.journeyrecorder.di.Graph
import com.ryccoatika.journeyrecorder.recorder.bubble.BubbleController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Thin entry point: gate events, hand them to [EventInterpreter], ship the
 * resulting [RawCapture] to [StepPipeline]. Owns the [BubbleController] and a
 * Main.immediate scope (WindowManager calls must stay on the main thread).
 */
class JourneyAccessibilityService : AccessibilityService() {

    private var scope: CoroutineScope? = null
    private var bubble: BubbleController? = null
    private lateinit var screenTracker: ScreenTracker
    private lateinit var interpreter: EventInterpreter
    private var leftTargetApp = false
    private var tornDown = false

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

        Graph.recorderState.setServiceConnected(true)

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        this.scope = scope

        bubble = BubbleController(
            service = this,
            stateHolder = Graph.recorderState,
            dao = Graph.journeyDao,
            onStopRequested = { scope.launch { Graph.repository.finishRecording() } },
        )

        scope.launch { Graph.repository.recoverOrphans() }

        scope.launch {
            Graph.recorderState.state.collect { state ->
                when (state) {
                    is RecorderStateHolder.RecorderState.Recording -> onRecordingStarted(state)
                    RecorderStateHolder.RecorderState.Idle -> onRecordingStopped()
                }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val state = Graph.recorderState.current
            as? RecorderStateHolder.RecorderState.Recording ?: return
        val pkg = event.packageName?.toString() ?: return
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
                // Keyboard / status bar windows are not "leaving the app".
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

    // ------------------------------------------------------------------ private

    private fun onRecordingStarted(state: RecorderStateHolder.RecorderState.Recording) {
        leftTargetApp = false
        screenTracker.startRecording()
        // NOTE: serviceInfo.packageNames narrowing is deliberately NOT applied:
        // it would stop the framework from ever delivering foreign-package
        // WINDOW_STATE_CHANGED events, making the "Left/Returned to target app"
        // markers unreachable. The in-code gate above is the filter.
        bubble?.show()
    }

    private fun onRecordingStopped() {
        bubble?.hide()
    }

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
        scope?.cancel()
        scope = null
    }

    private companion object {
        const val OWN_PACKAGE = "com.ryccoatika.journeyrecorder"

        val SYSTEM_ALLOWLIST = listOf(
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
            "com.android.intentresolver",
            "android",
        )
    }
}
