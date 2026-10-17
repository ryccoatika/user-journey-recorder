package com.ryccoatika.journeyrecorder.ui.common

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.ryccoatika.journeyrecorder.R

/**
 * Google Play "prominent disclosure" for the AccessibilityService API. Must be
 * shown — and explicitly accepted — every time before we send the user to
 * Accessibility settings to enable the service. Play requires two explicit
 * buttons; back/outside-tap must never count as consent, so dismissal maps to
 * [onDecline] and outside taps are ignored.
 */
@Composable
fun AccessibilityDisclosureDialog(
    onAgree: () -> Unit,
    onDecline: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDecline,
        properties = DialogProperties(dismissOnClickOutside = false),
        icon = { Icon(Icons.Filled.Accessibility, contentDescription = null) },
        title = { Text(stringResource(R.string.common_a11y_disclosure_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    stringResource(R.string.common_a11y_disclosure_intro),
                    style = MaterialTheme.typography.bodyMedium,
                )
                DisclosureSection(
                    R.string.common_a11y_disclosure_collect_heading,
                    R.string.common_a11y_disclosure_collect_body,
                )
                DisclosureSection(
                    R.string.common_a11y_disclosure_use_heading,
                    R.string.common_a11y_disclosure_use_body,
                )
                DisclosureSection(
                    R.string.common_a11y_disclosure_share_heading,
                    R.string.common_a11y_disclosure_share_body,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.common_a11y_disclosure_footer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(onClick = onAgree) {
                Text(stringResource(R.string.common_a11y_disclosure_agree))
            }
        },
        dismissButton = {
            TextButton(onClick = onDecline) {
                Text(stringResource(R.string.common_a11y_disclosure_decline))
            }
        },
    )
}

@Composable
private fun DisclosureSection(heading: Int, body: Int) {
    Spacer(Modifier.height(12.dp))
    Text(
        stringResource(heading),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
    )
    Text(stringResource(body), style = MaterialTheme.typography.bodyMedium)
}

/** Only call after the user tapped Agree in [AccessibilityDisclosureDialog]. */
fun openAccessibilitySettings(context: Context) {
    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
}
