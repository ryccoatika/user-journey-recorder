package com.ryccoatika.journeyrecorder.export

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import com.ryccoatika.journeyrecorder.R
import com.ryccoatika.journeyrecorder.data.db.JourneyEntity
import com.ryccoatika.journeyrecorder.data.db.JourneyEventEntity
import com.ryccoatika.journeyrecorder.di.Graph
import com.ryccoatika.journeyrecorder.recorder.JourneyAccessibilityService
import com.ryccoatika.journeyrecorder.recorder.RecorderStateHolder
import com.ryccoatika.journeyrecorder.ui.MainActivity
import java.io.File

/**
 * "Journey saved" notification with Share / Save / Delete actions. Share is an
 * activity PendingIntent straight into the chooser (the markdown file is
 * written up front) so the shade closes by itself; Save and Delete go through
 * [JourneyActionReceiver].
 */
object JourneyNotifier {

    const val EXTRA_OPEN_JOURNEY_ID = "open_journey_id"
    private const val CHANNEL_ID = "journey_results"

    private const val RECORDING_CHANNEL_ID = "recording_live"
    private const val RECORDING_NOTIFICATION_ID = 770001 // one active recording at a time

    // ----------------------------------------------------------- live recording

    /**
     * Ongoing "Live Update" notification shown throughout a recording so it can
     * be stopped from the shade even when the floating bubble is dismissed.
     * Safe to call repeatedly to refresh the step count / elapsed time.
     */
    fun showRecording(
        context: Context,
        journey: JourneyEntity,
        stepCount: Int,
        elapsedSec: Long,
        paused: Boolean,
    ) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        ensureRecordingChannel(context)

        val journeyId = journey.id
        val app = journey.targetAppLabel ?: journey.targetPackage
        val time = "%d:%02d".format(elapsedSec / 60, elapsedSec % 60)
        val title = if (paused) "Recording paused" else "Recording $app"
        val text = "$stepCount steps · $time"

        val tap = PendingIntent.getActivity(
            context,
            90_000,
            Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(JourneyAccessibilityService.EXTRA_OPEN_HOME, true)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        fun broadcast(action: String, req: Int): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                req,
                Intent(context, JourneyActionReceiver::class.java).apply {
                    this.action = action
                    putExtra(JourneyActionReceiver.EXTRA_JOURNEY_ID, journeyId)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val builder = NotificationCompat.Builder(context, RECORDING_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_record)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(tap)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setColor(0xFFD32F2F.toInt())
            .setColorized(true)
            .setProgress(0, 0, !paused) // indeterminate bar signals "live"
            .addAction(
                0,
                "Stop",
                broadcast(JourneyActionReceiver.ACTION_STOP_RECORDING, 91_001),
            )
            .addAction(
                0,
                if (paused) "Resume" else "Pause",
                broadcast(JourneyActionReceiver.ACTION_PAUSE_RECORDING, 91_002),
            )
            .addAction(
                0,
                "Discard",
                broadcast(JourneyActionReceiver.ACTION_DISCARD_RECORDING, 91_003),
            )

        // Android 16 Live Updates: promote to the status-bar chip / lock screen.
        runCatching { builder.setRequestPromotedOngoing(true) }

        try {
            manager.notify(RECORDING_NOTIFICATION_ID, builder.build())
        } catch (_: SecurityException) {
        }
    }

    fun cancelRecording(context: Context) {
        NotificationManagerCompat.from(context).cancel(RECORDING_NOTIFICATION_ID)
    }

    /**
     * Finish the active recording and swap the live notification for the
     * "Journey saved" result notification. Callable from the bubble and the
     * notification's Stop action.
     */
    suspend fun stopAndShowResult(context: Context) {
        val rec = Graph.recorderState.current
            as? RecorderStateHolder.RecorderState.Recording ?: return
        Graph.repository.finishRecording()
        cancelRecording(context)
        val journey = Graph.journeyDao.getJourney(rec.journeyId) ?: return
        val events = Graph.journeyDao.getEvents(rec.journeyId)
        showResult(context, journey, events)
    }

    private fun ensureRecordingChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                RECORDING_CHANNEL_ID,
                "Recording in progress",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Live recording status with stop / pause controls"
                setShowBadge(false)
            },
        )
    }

    fun showResult(context: Context, journey: JourneyEntity, events: List<JourneyEventEntity>) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        ensureChannel(context)

        val journeyId = journey.id
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        // Tap body -> open the journey in the app.
        val contentIntent = PendingIntent.getActivity(
            context,
            journeyId.toInt(),
            Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(EXTRA_OPEN_JOURNEY_ID, journeyId)
            },
            flags,
        )

        // Share: chooser directly from the notification, file written up front.
        val shareIntent = buildShareChooser(context, journey, events)?.let { chooser ->
            PendingIntent.getActivity(context, journeyId.toInt() + 100_000, chooser, flags)
        }

        fun broadcast(action: String, requestOffset: Int): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                journeyId.toInt() + requestOffset,
                Intent(context, JourneyActionReceiver::class.java).apply {
                    this.action = action
                    putExtra(JourneyActionReceiver.EXTRA_JOURNEY_ID, journeyId)
                },
                flags,
            )

        val subtitle = buildString {
            append(events.size)
            append(" steps · ")
            append(journey.targetAppLabel ?: journey.targetPackage)
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_record)
            .setContentTitle("Journey saved")
            .setContentText(subtitle)
            .setStyle(NotificationCompat.BigTextStyle().bigText("${journey.name}\n$subtitle"))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .addAction(0, "Save", broadcast(JourneyActionReceiver.ACTION_SAVE, 200_000))
            .addAction(0, "Delete", broadcast(JourneyActionReceiver.ACTION_DELETE, 300_000))
        if (shareIntent != null) {
            builder.addAction(0, "Share", shareIntent)
        }

        try {
            manager.notify(journeyId.toInt(), builder.build())
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS revoked mid-flight — the in-app result card
            // still covers the flow.
        }
    }

    fun update(context: Context, journeyId: Long, title: String, text: String) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        ensureChannel(context)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_record)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
        try {
            manager.notify(journeyId.toInt(), builder.build())
        } catch (_: SecurityException) {
        }
    }

    fun cancel(context: Context, journeyId: Long) {
        NotificationManagerCompat.from(context).cancel(journeyId.toInt())
    }

    /** ACTION_SEND chooser for the journey's markdown; null when the file write fails. */
    fun buildShareChooser(
        context: Context,
        journey: JourneyEntity,
        events: List<JourneyEventEntity>,
    ): Intent? = try {
        val markdown = MarkdownGenerator.generate(journey, events)
        val exportsDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(exportsDir, "journey-${journey.id}.md")
        file.writeText(markdown)
        val uri = FileProvider.getUriForFile(
            context,
            context.packageName + ".fileprovider",
            file,
        )
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/markdown"
            putExtra(Intent.EXTRA_STREAM, uri)
            if (markdown.length <= 100_000) putExtra(Intent.EXTRA_TEXT, markdown)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        Intent.createChooser(send, "Share journey").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    } catch (_: Exception) {
        null
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Journey results",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Shown when a recording finishes, with export shortcuts"
            },
        )
    }
}