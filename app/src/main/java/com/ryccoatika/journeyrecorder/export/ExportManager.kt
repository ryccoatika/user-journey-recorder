package com.ryccoatika.journeyrecorder.export

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import com.ryccoatika.journeyrecorder.R
import com.ryccoatika.journeyrecorder.analytics.AppAnalytics
import com.ryccoatika.journeyrecorder.di.Graph
import com.ryccoatika.journeyrecorder.data.db.JourneyEntity
import com.ryccoatika.journeyrecorder.data.db.JourneyEventEntity
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface ExportResult {
    data class Saved(val displayPath: String) : ExportResult
    data class Failed(val message: String) : ExportResult
}

class ExportManager(private val context: Context) {

    /**
     * Renders the journey to markdown, writes it into the FileProvider-served
     * cache directory and launches a share chooser. EXTRA_TEXT carries the full
     * markdown for text-only share targets.
     */
    fun share(journey: JourneyEntity, events: List<JourneyEventEntity>): ExportResult = try {
        shareInternal(journey, events)
        Graph.analytics.journeyExported(AppAnalytics.METHOD_SHARE)
        ExportResult.Saved(context.getString(R.string.export_share_sheet_label))
    } catch (e: Exception) {
        ExportResult.Failed(
            context.getString(
                R.string.export_share_failed,
                e.message ?: context.getString(R.string.export_unknown_error),
            ),
        )
    }

    private fun shareInternal(journey: JourneyEntity, events: List<JourneyEventEntity>) {
        val markdown = MarkdownGenerator.generate(journey, events)
        val exportsDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(exportsDir, buildFileName(journey))
        file.writeText(markdown)

        val uri = FileProvider.getUriForFile(
            context,
            context.packageName + ".fileprovider",
            file,
        )
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/markdown"
            putExtra(Intent.EXTRA_STREAM, uri)
            // EXTRA_TEXT only for small journeys — big markdown in the binder
            // transaction throws TransactionTooLargeException (~1 MB limit).
            // Large journeys travel via the stream URI alone.
            if (markdown.length <= MAX_EXTRA_TEXT_CHARS) {
                putExtra(Intent.EXTRA_TEXT, markdown)
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(
            sendIntent,
            context.getString(R.string.export_share_chooser_title),
        ).apply {
            // May be called from a non-activity context (e.g. the a11y service).
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    /**
     * Saves the rendered markdown into the public Downloads collection via
     * MediaStore. API 29+ only; older devices are told to use Share instead.
     */
    suspend fun saveToDownloads(
        journey: JourneyEntity,
        events: List<JourneyEventEntity>,
    ): ExportResult = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return@withContext ExportResult.Failed(
                context.getString(R.string.export_needs_android_10),
            )
        }
        val markdown = MarkdownGenerator.generate(journey, events)
        val fileName = buildFileName(journey)
        try {
            insertIntoDownloads(fileName, markdown).also { result ->
                if (result is ExportResult.Saved) {
                    Graph.analytics.journeyExported(AppAnalytics.METHOD_DOWNLOAD)
                }
            }
        } catch (e: Exception) {
            ExportResult.Failed(
                context.getString(
                    R.string.export_save_failed,
                    e.message ?: context.getString(R.string.export_unknown_error),
                ),
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun insertIntoDownloads(fileName: String, markdown: String): ExportResult {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "text/markdown")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values)
            ?: return ExportResult.Failed(context.getString(R.string.export_create_failed))
        try {
            resolver.openOutputStream(uri)?.use { stream ->
                stream.write(markdown.toByteArray(Charsets.UTF_8))
            } ?: throw IOException(context.getString(R.string.export_open_failed))
            val publish = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            resolver.update(uri, publish, null, null)
        } catch (e: Exception) {
            // Do not leave a pending orphan row behind.
            resolver.delete(uri, null, null)
            throw e
        }
        return ExportResult.Saved("Downloads/$fileName")
    }

    /**
     * "{name sanitized to [A-Za-z0-9-_ ]}-{yyyyMMdd-HHmm of startedAt}.md"
     */
    private fun buildFileName(journey: JourneyEntity): String {
        val sanitized = journey.name
            .replace(Regex("[^A-Za-z0-9 _-]"), "_")
            .trim()
            .take(60) // keep the full name+stamp under filesystem name limits
            .trim()
            .ifEmpty { "journey" }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date(journey.startedAt))
        return "$sanitized-$stamp.md"
    }

    private companion object {
        const val MAX_EXTRA_TEXT_CHARS = 100_000
    }
}
