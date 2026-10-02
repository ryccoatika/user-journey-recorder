package com.ryccoatika.journeyrecorder.analytics

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Thin wrapper over Firebase Analytics + Crashlytics.
 *
 * Privacy contract: recorded journey content (taps, typed text, screen text,
 * the identity of the app being recorded) is NEVER passed here. Events carry
 * only aggregate counts and lifecycle/feature signals. Every call is wrapped so
 * a telemetry failure can never crash the app — especially the accessibility
 * service, whose crash would kill an in-progress recording.
 */
class AppAnalytics(context: Context) {

    private val analytics = FirebaseAnalytics.getInstance(context)
    private val crashlytics = FirebaseCrashlytics.getInstance()

    fun logScreenView(screenName: String) = log(FirebaseAnalytics.Event.SCREEN_VIEW) {
        putString(FirebaseAnalytics.Param.SCREEN_NAME, screenName)
    }

    fun recordingStarted() {
        setKey(KEY_RECORDING_ACTIVE, true)
        log(EVENT_RECORDING_STARTED)
    }

    /** @param status "completed" or "recovered"; [stepCount] is an aggregate count only. */
    fun recordingStopped(status: String, stepCount: Int) {
        setKey(KEY_RECORDING_ACTIVE, false)
        log(EVENT_RECORDING_STOPPED) {
            putString(PARAM_STATUS, status)
            putInt(PARAM_STEP_COUNT, stepCount)
        }
    }

    fun recordingDiscarded() {
        setKey(KEY_RECORDING_ACTIVE, false)
        log(EVENT_RECORDING_DISCARDED)
    }

    /** @param method [METHOD_SHARE] or [METHOD_DOWNLOAD]. */
    fun journeyExported(method: String) = log(EVENT_JOURNEY_EXPORTED) {
        putString(FirebaseAnalytics.Param.METHOD, method)
    }

    fun onboardingCompleted() = log(EVENT_ONBOARDING_COMPLETED)

    fun redactionUsed() = log(EVENT_REDACTION_USED)

    private inline fun log(event: String, params: Bundle.() -> Unit = {}) {
        runCatching { analytics.logEvent(event, Bundle().apply(params)) }
    }

    private fun setKey(key: String, value: Boolean) {
        runCatching { crashlytics.setCustomKey(key, value) }
    }

    companion object {
        const val METHOD_SHARE = "share"
        const val METHOD_DOWNLOAD = "download"

        private const val EVENT_RECORDING_STARTED = "recording_started"
        private const val EVENT_RECORDING_STOPPED = "recording_stopped"
        private const val EVENT_RECORDING_DISCARDED = "recording_discarded"
        private const val EVENT_JOURNEY_EXPORTED = "journey_exported"
        private const val EVENT_ONBOARDING_COMPLETED = "onboarding_completed"
        private const val EVENT_REDACTION_USED = "redaction_used"

        private const val PARAM_STATUS = "status"
        private const val PARAM_STEP_COUNT = "step_count"

        // Non-PII diagnostic: was a recording in progress when a crash occurred.
        private const val KEY_RECORDING_ACTIVE = "recording_active"
    }
}
