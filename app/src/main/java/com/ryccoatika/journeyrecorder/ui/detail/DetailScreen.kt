package com.ryccoatika.journeyrecorder.ui.detail

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ryccoatika.journeyrecorder.R
import com.ryccoatika.journeyrecorder.data.db.EventType
import com.ryccoatika.journeyrecorder.data.db.JourneyEntity
import com.ryccoatika.journeyrecorder.data.db.JourneyEventEntity
import com.ryccoatika.journeyrecorder.data.db.JourneyStatus
import com.ryccoatika.journeyrecorder.export.ExportManager
import com.ryccoatika.journeyrecorder.export.ExportResult
import com.ryccoatika.journeyrecorder.ui.common.InfoChip
import com.ryccoatika.journeyrecorder.ui.common.TargetAppIcon
import com.ryccoatika.journeyrecorder.ui.common.formatDuration
import com.ryccoatika.journeyrecorder.ui.common.versionLabel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class ScreenGroup(
    val screenName: String?,
    val events: List<JourneyEventEntity>,
)

private fun groupByScreen(events: List<JourneyEventEntity>): List<ScreenGroup> {
    val groups = mutableListOf<ScreenGroup>()
    var currentName: String? = null
    var current = mutableListOf<JourneyEventEntity>()
    for (event in events) {
        if (current.isEmpty() || event.screenName == currentName) {
            if (current.isEmpty()) currentName = event.screenName
            current.add(event)
        } else {
            groups.add(ScreenGroup(currentName, current))
            currentName = event.screenName
            current = mutableListOf(event)
        }
    }
    if (current.isNotEmpty()) groups.add(ScreenGroup(currentName, current))
    return groups
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DetailScreen(
    onBack: () -> Unit,
    onOpenGuide: () -> Unit,
    viewModel: DetailViewModel = viewModel { DetailViewModel(createSavedStateHandle()) },
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val exportManager = remember(context) { ExportManager(context) }

    val journey by viewModel.journey.collectAsState()
    val events by viewModel.events.collectAsState()

    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = journey?.name ?: stringResource(R.string.detail_title_fallback),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.detail_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showRenameDialog = true }) {
                        Icon(
                            Icons.Filled.Edit,
                            contentDescription = stringResource(R.string.detail_rename),
                        )
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.detail_delete),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 3.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            val j = journey ?: return@OutlinedButton
                            val result = exportManager.share(j, events)
                            if (result is ExportResult.Failed) {
                                scope.launch { snackbarHostState.showSnackbar(result.message) }
                            }
                        },
                        enabled = journey != null,
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.detail_share)) }
                    val savedToFormat = stringResource(R.string.detail_saved_to)
                    Button(
                        onClick = {
                            val j = journey ?: return@Button
                            val snapshot = events
                            scope.launch {
                                val message =
                                    when (val result = exportManager.saveToDownloads(j, snapshot)) {
                                        is ExportResult.Saved -> {
                                            String.format(savedToFormat, result.displayPath)
                                        }

                                        is ExportResult.Failed -> {
                                            result.message
                                        }
                                    }
                                snackbarHostState.showSnackbar(message)
                            }
                        },
                        enabled = journey != null,
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.detail_save_markdown)) }
                }
            }
        },
    ) { innerPadding ->
        val groups = remember(events) { groupByScreen(events) }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            item(key = "header") {
                journey?.let {
                    HeaderCard(journey = it, stepCount = events.size, onOpenGuide = onOpenGuide)
                }
            }

            groups.forEachIndexed { groupIndex, group ->
                stickyHeader(key = "screen-$groupIndex") {
                    ScreenHeader(name = group.screenName)
                }
                items(group.events, key = { it.id }) { event ->
                    EventRow(event = event, onRedact = { viewModel.redact(event.id) })
                }
            }
        }
    }

    if (showRenameDialog) {
        RenameDialog(
            currentName = journey?.name ?: "",
            onConfirm = { newName ->
                viewModel.rename(newName)
                showRenameDialog = false
            },
            onDismiss = { showRenameDialog = false },
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.detail_delete_dialog_title)) },
            text = {
                Text(stringResource(R.string.detail_delete_dialog_message))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.delete(onDeleted = onBack)
                    },
                ) {
                    Text(
                        stringResource(R.string.detail_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.detail_cancel))
                }
            },
        )
    }
}

@Composable
private fun HeaderCard(journey: JourneyEntity, stepCount: Int, onOpenGuide: () -> Unit) {
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy HH:mm", Locale.US) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TargetAppIcon(packageName = journey.targetPackage, size = 48.dp)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        text = journey.targetAppLabel ?: journey.targetPackage,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = journey.targetPackage +
                            (journey.appVersionName?.let { " · ${versionLabel(it)}" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = dateFormat.format(Date(journey.startedAt)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                InfoChip(
                    text = stringResource(R.string.detail_step_count, stepCount),
                    icon = Icons.Outlined.TouchApp,
                )
                formatDuration(journey.startedAt, journey.endedAt, journey.pausedMs)?.let {
                    InfoChip(text = it, icon = Icons.Filled.Schedule)
                }
                InfoChip(text = journey.androidVersion, icon = Icons.Filled.PhoneAndroid)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = journey.deviceInfo,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (journey.status == JourneyStatus.RECOVERED) {
                Spacer(Modifier.height(12.dp))
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.Warning,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.detail_recovered_warning),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            if (journey.noElementIds) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    shape = MaterialTheme.shapes.medium,
                    onClick = onOpenGuide,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.Info,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.detail_no_element_ids),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScreenHeader(name: String?) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                shape = RoundedCornerShape(50),
            ) {
                Text(
                    text = name ?: stringResource(R.string.detail_unknown_screen),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun eventIcon(type: EventType): ImageVector = when (type) {
    EventType.SCREEN_OPEN -> Icons.AutoMirrored.Filled.OpenInNew
    EventType.CLICK -> Icons.Filled.TouchApp
    EventType.LONG_CLICK -> Icons.Filled.Fingerprint
    EventType.TEXT_INPUT -> Icons.Filled.Keyboard
    EventType.SCROLL -> Icons.Filled.SwapVert
    EventType.SELECT -> Icons.Filled.CheckCircle
    EventType.SYSTEM_DIALOG -> Icons.Filled.Info
    EventType.APP_MARKER -> Icons.Filled.Flag
}

@Composable
private fun eventTint(type: EventType): Color = when (type) {
    EventType.CLICK, EventType.LONG_CLICK, EventType.SELECT -> {
        MaterialTheme.colorScheme.primary
    }

    EventType.TEXT_INPUT -> {
        MaterialTheme.colorScheme.secondary
    }

    EventType.SCREEN_OPEN -> {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    EventType.SCROLL -> {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    EventType.SYSTEM_DIALOG -> {
        MaterialTheme.colorScheme.tertiary
    }

    EventType.APP_MARKER -> {
        MaterialTheme.colorScheme.outline
    }
}

private fun elementLabel(event: JourneyEventEntity): String? =
    event.elementText?.takeIf { it.isNotBlank() }
        ?: event.contentDesc?.takeIf { it.isNotBlank() }
        ?: event.elementId?.takeIf { it.isNotBlank() }
        ?: event.className?.takeIf { it.isNotBlank() }

private fun fieldLabel(event: JourneyEventEntity): String? =
    event.contentDesc?.takeIf { it.isNotBlank() }
        ?: event.elementId?.takeIf { it.isNotBlank() }
        ?: event.className?.takeIf { it.isNotBlank() }

@Composable
private fun primaryLine(event: JourneyEventEntity): String = when (event.eventType) {
    EventType.SCREEN_OPEN -> {
        // dialog rows reuse SCREEN_OPEN with "Dialog opened: …" in elementText
        event.elementText?.takeIf { it.isNotBlank() }
            ?: stringResource(
                R.string.detail_open_screen,
                event.screenName ?: stringResource(R.string.detail_unknown),
            )
    }

    EventType.CLICK -> {
        elementLabel(event)?.let { stringResource(R.string.detail_tap_labeled, it) }
            ?: stringResource(R.string.detail_tap)
    }

    EventType.LONG_CLICK -> {
        elementLabel(event)?.let { stringResource(R.string.detail_long_press_labeled, it) }
            ?: stringResource(R.string.detail_long_press)
    }

    EventType.TEXT_INPUT -> {
        val target = fieldLabel(event)?.let { stringResource(R.string.detail_text_target, it) } ?: ""
        when {
            event.masked -> {
                stringResource(R.string.detail_type_masked, target)
            }

            event.typedText != null -> {
                stringResource(R.string.detail_type_value, event.typedText, target)
            }

            else -> {
                stringResource(R.string.detail_clear_text, target)
            }
        }
    }

    EventType.SCROLL -> {
        elementLabel(event)?.let { stringResource(R.string.detail_scroll_labeled, it) }
            ?: stringResource(R.string.detail_scroll)
    }

    EventType.SELECT -> {
        elementLabel(event)?.let { stringResource(R.string.detail_select_labeled, it) }
            ?: stringResource(R.string.detail_select)
    }

    EventType.SYSTEM_DIALOG -> {
        elementLabel(event)?.let { stringResource(R.string.detail_system_dialog_labeled, it) }
            ?: stringResource(R.string.detail_system_dialog)
    }

    EventType.APP_MARKER -> {
        elementLabel(event) ?: stringResource(R.string.detail_app_marker)
    }
}

@Composable
private fun EventRow(
    event: JourneyEventEntity,
    onRedact: () -> Unit,
) {
    // Timeline line drawn behind the row instead of an IntrinsicSize.Min +
    // fillMaxHeight box: intrinsic measurement double-measures every visible
    // row, which dropped frames while the enter transition was running.
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .drawBehind {
                val x = 20.dp.toPx()
                drawLine(
                    color = lineColor,
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 2.dp.toPx(),
                )
            },
        verticalAlignment = Alignment.Top,
    ) {
        // Timeline gutter: numbered node sitting on the line.
        Box(
            modifier = Modifier.width(40.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.padding(top = 6.dp),
            ) {
                Box(
                    modifier = Modifier.size(28.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        // 1-based to match the exported markdown's step numbers
                        text = "${event.sequence + 1}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = eventIcon(event.eventType),
                    contentDescription = event.eventType.name,
                    tint = eventTint(event.eventType),
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = primaryLine(event),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
            }
            val secondary = listOfNotNull(
                event.elementId?.takeIf { it.isNotBlank() }?.let { "id: $it" },
                event.bounds?.takeIf { it.isNotBlank() }?.let { it },
            ).joinToString("  ·  ")
            if (secondary.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = secondary,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (event.masked) {
                Spacer(Modifier.height(4.dp))
                InfoChip(
                    text = stringResource(R.string.detail_masked_chip),
                    icon = Icons.Filled.VisibilityOff,
                    container = MaterialTheme.colorScheme.tertiaryContainer,
                    content = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }
        if (event.eventType == EventType.TEXT_INPUT && event.typedText != null) {
            IconButton(onClick = onRedact) {
                Icon(
                    Icons.Filled.VisibilityOff,
                    contentDescription = stringResource(R.string.detail_redact),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun RenameDialog(
    currentName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.detail_rename_dialog_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.detail_name_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank(),
            ) { Text(stringResource(R.string.detail_rename)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.detail_cancel)) }
        },
    )
}
