package com.ryccoatika.journeyrecorder.ui.common

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.ryccoatika.journeyrecorder.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Launcher icon of [packageName], loaded off the main thread. Falls back to a
 * generic tile when the app is no longer installed.
 */
@Composable
fun TargetAppIcon(
    packageName: String?,
    size: Dp = 44.dp,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val icon by produceState<ImageBitmap?>(initialValue = null, packageName) {
        value = packageName?.let { pkg ->
            withContext(Dispatchers.IO) {
                runCatching {
                    context.packageManager.getApplicationIcon(pkg)
                        .toBitmap(ICON_PX, ICON_PX)
                        .asImageBitmap()
                }.getOrNull()
            }
        }
    }
    val shape = RoundedCornerShape(size / 4)
    val loaded = icon
    if (loaded != null) {
        Image(
            bitmap = loaded,
            contentDescription = null,
            modifier = modifier
                .size(size)
                .clip(shape),
        )
    } else {
        Box(
            modifier = modifier
                .size(size)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Android,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(size / 2),
            )
        }
    }
}

/** Selected-state leading avatar: primary circle with a check (multi-select). */
@Composable
fun SelectedAvatar(size: Dp = 44.dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.Check,
            contentDescription = stringResource(R.string.common_selected),
            tint = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

/** Pill search field (TextField, surfaceVariant, transparent indicators). */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SearchField(
    query: String,
    placeholder: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    androidx.compose.material3.TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        placeholder = { Text(placeholder) },
        singleLine = true,
        shape = CircleShape,
        leadingIcon = {
            Icon(Icons.Filled.Search, contentDescription = null)
        },
        trailingIcon = trailing,
        colors = androidx.compose.material3.TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        ),
    )
}

/** Soft-pulsing red dot — the universal "recording" signal in the app. */
@Composable
fun PulsingRecordDot(
    modifier: Modifier = Modifier,
    size: Dp = 12.dp,
    color: Color = MaterialTheme.colorScheme.error,
) {
    val transition = rememberInfiniteTransition(label = "recordPulse")
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "recordPulseScale",
    )
    Box(
        modifier = modifier
            .size(size)
            .scale(scale)
            .background(color, CircleShape),
    )
}

/** Small icon+label chip used for metadata rows (steps, duration, version…). */
@Composable
fun InfoChip(
    text: String,
    icon: ImageVector? = null,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    content: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Surface(color = container, contentColor = content, shape = RoundedCornerShape(8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text(text = text, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

/** "v1.2.3" — avoids "vv0.133" when the app's versionName already starts with v. */
fun versionLabel(versionName: String): String =
    if (versionName.startsWith("v", ignoreCase = true)) versionName else "v$versionName"

/** "2m 14s" / "45s" duration formatting shared by Home and Detail. */
fun formatDuration(startedAt: Long, endedAt: Long?, pausedMs: Long = 0): String? {
    endedAt ?: return null
    val totalSec = ((endedAt - startedAt - pausedMs) / 1000).coerceAtLeast(0)
    val min = totalSec / 60
    val sec = totalSec % 60
    return if (min > 0) "${min}m ${sec}s" else "${sec}s"
}

private const val ICON_PX = 128