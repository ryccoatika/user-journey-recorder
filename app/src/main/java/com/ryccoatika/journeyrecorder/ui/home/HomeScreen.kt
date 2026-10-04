package com.ryccoatika.journeyrecorder.ui.home

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BubbleChart
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AppRegistration
import androidx.compose.material.icons.outlined.BubbleChart
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ryccoatika.journeyrecorder.R
import com.ryccoatika.journeyrecorder.data.db.JourneyStatus
import com.ryccoatika.journeyrecorder.ui.common.InfoChip
import com.ryccoatika.journeyrecorder.ui.common.PulsingRecordDot
import com.ryccoatika.journeyrecorder.ui.common.SearchField
import com.ryccoatika.journeyrecorder.ui.common.SelectedAvatar
import com.ryccoatika.journeyrecorder.ui.common.TargetAppIcon
import com.ryccoatika.journeyrecorder.ui.common.formatDuration
import com.ryccoatika.journeyrecorder.util.PermissionChecks
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNewRecording: () -> Unit,
    onOpenJourney: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = viewModel(),
) {
    val context = LocalContext.current
    // Hoisted so the Toast lambda doesn't call context.getString (stale across
    // config changes; flagged by lint's LocalContextGetResourceValueCall).
    val enableBubbleMsg = stringResource(R.string.home_bubble_enable_a11y)
    val items by viewModel.items.collectAsState()
    val banner by viewModel.banner.collectAsState()
    val bubbleEnabled by viewModel.bubbleEnabled.collectAsState()
    val selectedIds by viewModel.selectedIds.collectAsState()
    val vmQuery by viewModel.query.collectAsState()
    val selectionMode = selectedIds.isNotEmpty()

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    var queryInput by remember { mutableStateOf(vmQuery) }
    var searchFieldFocused by remember { mutableStateOf(false) }
    var focusSearchOnExpand by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }

    LaunchedEffect(vmQuery) { if (vmQuery != queryInput) queryInput = vmQuery }
    LaunchedEffect(queryInput) {
        delay(300)
        if (queryInput != vmQuery) viewModel.setQuery(queryInput)
    }

    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val scrollSignals = rememberHomeScrollSignals(
        listState = listState,
        queryEmpty = queryInput.isEmpty(),
        searchFieldFocused = searchFieldFocused,
        onUserScroll = { focusManager.clearFocus() },
    )

    var renameTarget by remember { mutableStateOf<JourneyListItem?>(null) }
    var deleteTarget by remember { mutableStateOf<JourneyListItem?>(null) }
    var showDeleteSelected by remember { mutableStateOf(false) }

    BackHandler(enabled = selectionMode) { viewModel.clearSelection() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (selectionMode) {
                SelectionTopBar(
                    selectedCount = selectedIds.size,
                    onClearSelection = viewModel::clearSelection,
                )
            } else {
                TopAppBar(
                    title = { Text(stringResource(R.string.home_title), fontWeight = FontWeight.SemiBold) },
                    actions = {
                        AnimatedVisibility(
                            visible = !scrollSignals.searchFieldExpanded,
                            enter = scaleIn() + fadeIn(),
                            exit = scaleOut() + fadeOut(),
                        ) {
                            IconButton(onClick = {
                                focusSearchOnExpand = true
                                scrollSignals.searchFieldExpanded = true
                            }) {
                                Icon(
                                    Icons.Filled.Search,
                                    contentDescription = stringResource(R.string.home_search),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                val turningOn = !bubbleEnabled
                                viewModel.toggleBubble()
                                if (turningOn &&
                                    !PermissionChecks.isAccessibilityServiceEnabled(context)
                                ) {
                                    Toast
                                        .makeText(
                                            context,
                                            enableBubbleMsg,
                                            Toast.LENGTH_LONG,
                                        ).show()
                                    context.startActivity(
                                        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
                                    )
                                }
                            },
                        ) {
                            Icon(
                                if (bubbleEnabled) Icons.Filled.BubbleChart else Icons.Outlined.BubbleChart,
                                contentDescription = if (bubbleEnabled) {
                                    stringResource(R.string.home_bubble_hide)
                                } else {
                                    stringResource(R.string.home_bubble_show)
                                },
                                tint = if (bubbleEnabled) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(
                                Icons.Filled.Settings,
                                contentDescription = stringResource(R.string.home_settings),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
                )
            }
        },
        bottomBar = {
            if (selectionMode) {
                SelectionBottomBar(onDelete = { showDeleteSelected = true })
            }
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = scrollSignals.fabVisible && !selectionMode,
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut(),
            ) {
                ExtendedFloatingActionButton(
                    onClick = onNewRecording,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.home_new_recording)) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .nestedScroll(scrollSignals.nestedScrollConnection),
        ) {
            AnimatedVisibility(
                visible = banner is RecordingBannerState.Visible,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                (banner as? RecordingBannerState.Visible)?.let { visible ->
                    RecordingBanner(banner = visible, onStop = viewModel::stopRecording)
                }
            }

            val hasJourneys = items.isNotEmpty() || queryInput.isNotBlank()
            if (!hasJourneys) {
                EmptyState(modifier = Modifier.weight(1f))
            } else {
                AnimatedVisibility(
                    visible = scrollSignals.searchFieldExpanded,
                    enter = expandVertically(tween(200, easing = FastOutSlowInEasing), Alignment.Top) +
                        fadeIn(tween(200)),
                    exit = shrinkVertically(tween(200, easing = FastOutSlowInEasing), Alignment.Top) +
                        fadeOut(tween(200)),
                ) {
                    SearchField(
                        query = queryInput,
                        placeholder = stringResource(R.string.home_search_placeholder),
                        onQueryChange = { queryInput = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .focusRequester(searchFocusRequester)
                            .onFocusChanged { searchFieldFocused = it.isFocused },
                        trailing = if (queryInput.isNotEmpty()) {
                            {
                                IconButton(onClick = {
                                    queryInput = ""
                                    viewModel.setQuery("")
                                }) {
                                    Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.home_clear_search))
                                }
                            }
                        } else {
                            null
                        },
                    )
                    LaunchedEffect(Unit) {
                        if (focusSearchOnExpand) {
                            searchFocusRequester.requestFocus()
                            focusSearchOnExpand = false
                        }
                    }
                }

                if (items.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            stringResource(R.string.home_no_match, queryInput.trim()),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    val orderedIds = remember(items) { items.map { it.journey.id } }
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .dragToSelect(
                                listState = listState,
                                orderedIds = orderedIds,
                                onSelectAnchor = { id ->
                                    val before = viewModel.selectedIds.value
                                    viewModel.setSelection(
                                        if (id in before) before - id else before + id,
                                    )
                                    before
                                },
                                setSelection = viewModel::setSelection,
                                scrollBy = { delta -> scope.launch { listState.scrollBy(delta) } },
                            ),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 8.dp,
                            bottom = 96.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(items, key = { it.journey.id }) { item ->
                            JourneyRow(
                                item = item,
                                selected = item.journey.id in selectedIds,
                                selectionMode = selectionMode,
                                onClick = {
                                    if (selectionMode) {
                                        viewModel.toggleSelect(item.journey.id)
                                    } else {
                                        onOpenJourney(item.journey.id)
                                    }
                                },
                                onRename = { renameTarget = item },
                                onDelete = { deleteTarget = item },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
            }
        }
    }

    renameTarget?.let { target ->
        RenameDialog(
            currentName = target.journey.name,
            onConfirm = { newName ->
                viewModel.rename(target.journey.id, newName)
                renameTarget = null
            },
            onDismiss = { renameTarget = null },
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.home_delete_journey_title)) },
            text = {
                Text(stringResource(R.string.home_delete_journey_message, target.journey.name))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(target.journey.id)
                        deleteTarget = null
                    },
                ) { Text(stringResource(R.string.home_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.home_cancel)) }
            },
        )
    }

    if (showDeleteSelected) {
        val n = selectedIds.size
        AlertDialog(
            onDismissRequest = { showDeleteSelected = false },
            title = {
                Text(
                    if (n == 1) {
                        stringResource(R.string.home_delete_journey_title)
                    } else {
                        stringResource(R.string.home_delete_selected_title, n)
                    },
                )
            },
            text = {
                Text(stringResource(R.string.home_delete_selected_message))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteSelected()
                        showDeleteSelected = false
                    },
                ) { Text(stringResource(R.string.home_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteSelected = false }) { Text(stringResource(R.string.home_cancel)) }
            },
        )
    }
}

@Composable
private fun RecordingBanner(
    banner: RecordingBannerState.Visible,
    onStop: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PulsingRecordDot()
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.home_recording_in_progress),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(
                        R.string.home_recording_banner_detail,
                        banner.targetPackage,
                        banner.stepCount,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(12.dp))
            FilledTonalButton(onClick = onStop) { Text(stringResource(R.string.home_stop)) }
        }
    }
}

@Composable
private fun JourneyRow(
    item: JourneyListItem,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val journey = item.journey
    val isRecording = journey.endedAt == null

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 14.dp, bottom = 14.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selected) {
                SelectedAvatar()
            } else {
                TargetAppIcon(packageName = journey.targetPackage)
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = journey.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${journey.targetAppLabel ?: journey.targetPackage} · " +
                        relativeDate(context, journey.startedAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    InfoChip(
                        text = stringResource(R.string.home_step_count, item.stepCount),
                        icon = Icons.Outlined.TouchApp,
                    )
                    formatDuration(journey.startedAt, journey.endedAt, journey.pausedMs)?.let {
                        InfoChip(text = it, icon = Icons.Filled.Schedule)
                    }
                    if (isRecording) {
                        InfoChip(
                            text = stringResource(R.string.home_recording_chip),
                            container = MaterialTheme.colorScheme.errorContainer,
                            content = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                    if (journey.status == JourneyStatus.RECOVERED) {
                        InfoChip(
                            text = stringResource(R.string.home_recovered_chip),
                            container = MaterialTheme.colorScheme.tertiaryContainer,
                            content = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                }
            }
            if (!selectionMode) {
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = stringResource(R.string.home_more_options),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.home_rename)) },
                            onClick = {
                                menuOpen = false
                                onRename()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.home_delete)) },
                            onClick = {
                                menuOpen = false
                                onDelete()
                            },
                        )
                    }
                }
            } else {
                Spacer(Modifier.width(12.dp))
            }
        }
    }
}

/** "Today 14:32", "Yesterday 09:10", else "Mar 4, 14:32". */
private fun relativeDate(context: android.content.Context, epochMs: Long): String {
    val time = SimpleDateFormat("HH:mm", Locale.US).format(Date(epochMs))
    val day = Calendar.getInstance().apply { timeInMillis = epochMs }
    val now = Calendar.getInstance()

    fun Calendar.dayKey() = get(Calendar.YEAR) * 1000 + get(Calendar.DAY_OF_YEAR)
    return when (now.dayKey() - day.dayKey()) {
        0 -> context.getString(R.string.home_date_today, time)
        1 -> context.getString(R.string.home_date_yesterday, time)
        else -> SimpleDateFormat("MMM d, HH:mm", Locale.US).format(Date(epochMs))
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .padding(horizontal = 28.dp)
                .widthIn(max = 480.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Icon(
                    Icons.Filled.PlayCircleOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier
                        .padding(20.dp)
                        .size(40.dp),
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.home_empty_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.home_empty_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            OnboardingStep(
                number = "1",
                icon = Icons.Outlined.AppRegistration,
                title = stringResource(R.string.home_step1_title),
                subtitle = stringResource(R.string.home_step1_subtitle),
            )
            OnboardingStep(
                number = "2",
                icon = Icons.Outlined.TouchApp,
                title = stringResource(R.string.home_step2_title),
                subtitle = stringResource(R.string.home_step2_subtitle),
            )
            OnboardingStep(
                number = "3",
                icon = Icons.Outlined.Description,
                title = stringResource(R.string.home_step3_title),
                subtitle = stringResource(R.string.home_step3_subtitle),
            )
        }
    }
}

@Composable
private fun OnboardingStep(
    number: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = "$number. $title",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
        title = { Text(stringResource(R.string.home_rename_dialog_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.home_rename_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank(),
            ) { Text(stringResource(R.string.home_rename)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.home_cancel)) }
        },
    )
}
