package com.ryccoatika.journeyrecorder.data

import com.ryccoatika.journeyrecorder.analytics.AppAnalytics
import com.ryccoatika.journeyrecorder.data.db.JourneyDao
import com.ryccoatika.journeyrecorder.data.db.JourneyEntity
import com.ryccoatika.journeyrecorder.data.db.JourneyStatus
import com.ryccoatika.journeyrecorder.recorder.RecorderStateHolder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Implemented by the capture StepPipeline. Lets the repository drain pending
 * coalescers before closing a journey without depending on capture internals.
 */
interface RecordingPipeline {
    fun begin(journeyId: Long)
    suspend fun flushAndEnd()
}

/**
 * Sole writer of [RecorderStateHolder]. UI and bubble always go through here.
 */
class JourneyRepository(
    private val dao: JourneyDao,
    private val stateHolder: RecorderStateHolder,
    private val pipeline: RecordingPipeline,
    private val analytics: AppAnalytics? = null,
) {
    suspend fun startRecording(
        targetPackage: String,
        targetAppLabel: String?,
        appVersionName: String?,
        deviceInfo: String,
        androidVersion: String,
    ): Long {
        check(stateHolder.current is RecorderStateHolder.RecorderState.Idle) {
            "A recording is already in progress"
        }
        val now = System.currentTimeMillis()
        val name = buildString {
            append(targetAppLabel ?: targetPackage)
            append(" ")
            append(SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(now)))
        }
        val journeyId = dao.insertJourney(
            JourneyEntity(
                name = name,
                targetPackage = targetPackage,
                targetAppLabel = targetAppLabel,
                appVersionName = appVersionName,
                deviceInfo = deviceInfo,
                androidVersion = androidVersion,
                startedAt = now,
            ),
        )
        pipeline.begin(journeyId)
        stateHolder.setRecording(journeyId, targetPackage)
        analytics?.recordingStarted()
        return journeyId
    }

    /** Toggle capture suspension for the active recording. No-op when idle. */
    fun togglePause() {
        if (stateHolder.current !is RecorderStateHolder.RecorderState.Recording) return
        stateHolder.setPaused(!stateHolder.isPaused)
    }

    suspend fun finishRecording(status: JourneyStatus = JourneyStatus.COMPLETED) {
        val state = stateHolder.current as? RecorderStateHolder.RecorderState.Recording ?: return
        pipeline.flushAndEnd()
        dao.finish(state.journeyId, System.currentTimeMillis(), status, stateHolder.totalPausedMs())
        analytics?.recordingStopped(status.name.lowercase(Locale.US), dao.countEvents(state.journeyId))
        stateHolder.setIdle()
    }

    /**
     * Abort the active recording and delete everything captured so far.
     * The pipeline is drained first so no late coalesced insert can recreate
     * rows against the deleted journey.
     */
    suspend fun discardRecording() {
        val state = stateHolder.current as? RecorderStateHolder.RecorderState.Recording ?: return
        pipeline.flushAndEnd()
        dao.deleteJourney(state.journeyId)
        analytics?.recordingDiscarded()
        stateHolder.setIdle()
    }

    /** Close journeys left open by a process/service death. Recorded steps stay intact. */
    suspend fun recoverOrphans() {
        val recordingId =
            (stateHolder.current as? RecorderStateHolder.RecorderState.Recording)?.journeyId
        for (journey in dao.getUnfinishedJourneys()) {
            if (journey.id == recordingId) continue
            val endedAt = dao.getLastEventTime(journey.id) ?: journey.startedAt
            dao.finish(journey.id, endedAt, JourneyStatus.RECOVERED, journey.pausedMs)
        }
    }

    suspend fun rename(id: Long, name: String) = dao.rename(id, name)

    suspend fun delete(id: Long) {
        // Deleting the journey that is being recorded would cascade its rows
        // away while the pipeline still inserts against it (FK violations).
        // Stop the recording cleanly first.
        val recording = stateHolder.current as? RecorderStateHolder.RecorderState.Recording
        if (recording?.journeyId == id) finishRecording()
        dao.deleteJourney(id)
    }
}