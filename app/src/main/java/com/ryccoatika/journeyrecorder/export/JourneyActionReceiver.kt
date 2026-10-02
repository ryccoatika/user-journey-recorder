package com.ryccoatika.journeyrecorder.export

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.ryccoatika.journeyrecorder.di.Graph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Handles the Save / Delete actions on the "Journey saved" notification.
 * Graph is initialized in Application.onCreate, which always runs before any
 * receiver in this process.
 */
class JourneyActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val journeyId = intent.getLongExtra(EXTRA_JOURNEY_ID, -1L)
        if (journeyId <= 0) return
        val action = intent.action ?: return
        val appContext = context.applicationContext
        val pending = goAsync()

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                when (action) {
                    ACTION_SAVE -> {
                        val journey = Graph.journeyDao.getJourney(journeyId) ?: return@launch
                        val events = Graph.journeyDao.getEvents(journeyId)
                        when (val result = ExportManager(appContext).saveToDownloads(journey, events)) {
                            is ExportResult.Saved -> {
                                toast(appContext, "Saved to ${result.displayPath}")
                                JourneyNotifier.update(
                                    appContext,
                                    journeyId,
                                    "Journey saved to Downloads",
                                    result.displayPath,
                                )
                            }
                            is ExportResult.Failed -> toast(appContext, result.message)
                        }
                    }

                    ACTION_DELETE -> {
                        Graph.repository.delete(journeyId)
                        JourneyNotifier.cancel(appContext, journeyId)
                        toast(appContext, "Journey deleted")
                    }

                    ACTION_STOP_RECORDING -> JourneyNotifier.stopAndShowResult(appContext)

                    ACTION_PAUSE_RECORDING -> Graph.repository.togglePause()

                    ACTION_DISCARD_RECORDING -> {
                        Graph.repository.discardRecording()
                        JourneyNotifier.cancelRecording(appContext)
                        toast(appContext, "Recording discarded")
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    private fun toast(context: Context, message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        const val ACTION_SAVE = "com.ryccoatika.journeyrecorder.action.SAVE_JOURNEY"
        const val ACTION_DELETE = "com.ryccoatika.journeyrecorder.action.DELETE_JOURNEY"
        const val ACTION_STOP_RECORDING = "com.ryccoatika.journeyrecorder.action.STOP_RECORDING"
        const val ACTION_PAUSE_RECORDING = "com.ryccoatika.journeyrecorder.action.PAUSE_RECORDING"
        const val ACTION_DISCARD_RECORDING =
            "com.ryccoatika.journeyrecorder.action.DISCARD_RECORDING"
        const val EXTRA_JOURNEY_ID = "journey_id"
    }
}