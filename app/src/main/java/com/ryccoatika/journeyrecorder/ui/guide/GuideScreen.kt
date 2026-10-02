package com.ryccoatika.journeyrecorder.ui.guide

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ryccoatika.journeyrecorder.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuideScreen(onBack: () -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.guide_top_bar_title),
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.guide_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            SectionTitle(stringResource(R.string.guide_section_ids_title))
            BodyText(stringResource(R.string.guide_section_ids_body))
            Spacer(Modifier.height(16.dp))

            FrameworkCard(
                name = stringResource(R.string.guide_fw_classic_name),
                support = Support.FULL,
                summary = stringResource(R.string.guide_fw_classic_summary),
            )
            FrameworkCard(
                name = stringResource(R.string.guide_fw_compose_name),
                support = Support.PARTIAL,
                summary = stringResource(R.string.guide_fw_compose_summary),
                tip = stringResource(R.string.guide_fw_compose_tip),
            )
            FrameworkCard(
                name = stringResource(R.string.guide_fw_flutter_name),
                support = Support.PARTIAL,
                summary = stringResource(R.string.guide_fw_flutter_summary),
                tip = stringResource(R.string.guide_fw_flutter_tip),
            )
            FrameworkCard(
                name = stringResource(R.string.guide_fw_rn_name),
                support = Support.PARTIAL,
                summary = stringResource(R.string.guide_fw_rn_summary),
                tip = stringResource(R.string.guide_fw_rn_tip),
            )
            FrameworkCard(
                name = stringResource(R.string.guide_fw_webview_name),
                support = Support.PARTIAL,
                summary = stringResource(R.string.guide_fw_webview_summary),
            )
            FrameworkCard(
                name = stringResource(R.string.guide_fw_games_name),
                support = Support.NONE,
                summary = stringResource(R.string.guide_fw_games_summary),
            )

            SectionTitle(stringResource(R.string.guide_section_tips_title))
            Bullet(stringResource(R.string.guide_tip_deliberate))
            Bullet(stringResource(R.string.guide_tip_gestures))
            Bullet(stringResource(R.string.guide_tip_review))
            Bullet(stringResource(R.string.guide_tip_masking))
            Bullet(stringResource(R.string.guide_tip_banking))
            Bullet(stringResource(R.string.guide_tip_recovered))
            Spacer(Modifier.height(24.dp))
        }
    }
}

private enum class Support { FULL, PARTIAL, NONE }

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun BodyText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SupportBadge(support: Support) {
    val (label, container, content) = when (support) {
        Support.FULL -> Triple(
            stringResource(R.string.guide_badge_full),
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Support.PARTIAL -> Triple(
            stringResource(R.string.guide_badge_partial),
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
        )
        Support.NONE -> Triple(
            stringResource(R.string.guide_badge_none),
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
        )
    }
    BadgeChip(label, container, content)
}

@Composable
private fun BadgeChip(label: String, container: Color, content: Color) {
    Surface(color = container, contentColor = content, shape = RoundedCornerShape(8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun FrameworkCard(
    name: String,
    support: Support,
    summary: String,
    tip: String? = null,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                SupportBadge(support)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (tip != null) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = tip,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(10.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Bullet(text: String) {
    Row(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = "•",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}