package com.ryccoatika.journeyrecorder.ui.home

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged

private val COLLAPSE_HYSTERESIS: Dp = 28.dp // ~3x touch slop

/**
 * Drives the FAB and the search field's expand/collapse from scroll. Accumulates
 * raw gesture deltas against a hysteresis threshold and flips two booleans —
 * deliberately NOT a TopAppBarScrollBehavior or offset-derived direction
 * (which jitters).
 */
@Stable
internal class HomeScrollSignals(
    private val hysteresisPx: Float,
    private val queryEmpty: () -> Boolean,
    private val searchFieldFocused: () -> Boolean,
    private val onUserScroll: () -> Unit,
) {
    var fabVisible by mutableStateOf(true)
    var searchFieldExpanded by mutableStateOf(true)

    private var accumulated = 0f

    fun resetAccumulator() {
        accumulated = 0f
    }

    val nestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            onScroll(available.y)
            return Offset.Zero
        }

        private fun onScroll(deltaY: Float) {
            if (deltaY == 0f) return
            if (searchFieldFocused()) onUserScroll()
            val sameDirection =
                (deltaY < 0f && accumulated <= 0f) || (deltaY > 0f && accumulated >= 0f)
            accumulated = if (sameDirection) accumulated + deltaY else deltaY
            when {
                accumulated <= -hysteresisPx -> {
                    fabVisible = false
                    if (queryEmpty() && !searchFieldFocused()) searchFieldExpanded = false
                    accumulated = 0f
                }
                accumulated >= hysteresisPx -> {
                    fabVisible = true
                    if (queryEmpty() && !searchFieldFocused()) searchFieldExpanded = true
                    accumulated = 0f
                }
            }
        }
    }
}

@Composable
internal fun rememberHomeScrollSignals(
    listState: LazyListState,
    queryEmpty: Boolean,
    searchFieldFocused: Boolean,
    onUserScroll: () -> Unit,
): HomeScrollSignals {
    val queryEmptyState = rememberUpdatedState(queryEmpty)
    val focusedState = rememberUpdatedState(searchFieldFocused)
    val onScrollState = rememberUpdatedState(onUserScroll)
    val hysteresisPx = with(LocalDensity.current) { COLLAPSE_HYSTERESIS.toPx() }
    val signals = remember {
        HomeScrollSignals(
            hysteresisPx = hysteresisPx,
            queryEmpty = { queryEmptyState.value },
            searchFieldFocused = { focusedState.value },
            onUserScroll = { onScrollState.value() },
        )
    }
    LaunchedEffect(listState) {
        androidx.compose.runtime.snapshotFlow {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
        }
            .distinctUntilChanged()
            .collect { atTop ->
                if (atTop) {
                    signals.fabVisible = true
                    if (queryEmptyState.value && !focusedState.value) {
                        signals.searchFieldExpanded = true
                    }
                    signals.resetAccumulator()
                }
            }
    }
    LaunchedEffect(queryEmpty, searchFieldFocused) {
        if (!queryEmpty || searchFieldFocused) signals.searchFieldExpanded = true
    }
    return signals
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SelectionTopBar(
    selectedCount: Int,
    onClearSelection: () -> Unit,
) {
    TopAppBar(
        title = {
            Text("$selectedCount selected", fontWeight = FontWeight.SemiBold)
        },
        navigationIcon = {
            IconButton(onClick = onClearSelection) {
                Icon(Icons.Filled.Close, contentDescription = "Clear selection")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    )
}

@Composable
internal fun SelectionBottomBar(onDelete: () -> Unit) {
    BottomAppBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
            Spacer(Modifier.width(4.dp))
            TextButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.width(8.dp))
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
