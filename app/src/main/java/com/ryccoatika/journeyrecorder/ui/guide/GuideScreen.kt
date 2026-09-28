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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuideScreen(onBack: () -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Recording guide", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            SectionTitle("Why are element IDs sometimes missing?")
            BodyText(
                "Journey Recorder reads the accessibility tree of the app you record. " +
                    "Whether an element has a stable ID depends entirely on how the " +
                    "target app was built. When IDs are missing, steps fall back to " +
                    "text, content descriptions and screen coordinates — the exported " +
                    "markdown marks those steps so your automation can use text-based " +
                    "selectors instead.",
            )
            Spacer(Modifier.height(16.dp))

            FrameworkCard(
                name = "Classic Android (XML Views)",
                support = Support.FULL,
                summary = "Full element IDs (e.g. com.app:id/login_button), text and " +
                    "descriptions. Best possible recording quality.",
            )
            FrameworkCard(
                name = "Jetpack Compose",
                support = Support.PARTIAL,
                summary = "No element IDs — Compose UIs are not built from Views, so " +
                    "there is nothing to report. Button text and content descriptions " +
                    "still come through and work well as locators.",
                tip = "For your own apps: set Modifier.semantics { testTagsAsResourceId " +
                    "= true } on the root composable and add Modifier.testTag(\"…\") to " +
                    "key elements — their tags then appear as element IDs when recording.",
            )
            FrameworkCard(
                name = "Flutter",
                support = Support.PARTIAL,
                summary = "No element IDs, ever — Flutter draws its own UI and only " +
                    "exposes what the app labels with Semantics. Labeled buttons and " +
                    "visible text are captured; unlabeled icons show as coordinates only.",
                tip = "For your own apps: wrap key widgets in Semantics(label: \"…\") — " +
                    "labels are captured as content descriptions.",
            )
            FrameworkCard(
                name = "React Native",
                support = Support.PARTIAL,
                summary = "No element IDs — testID is invisible to accessibility on " +
                    "Android. Visible text and accessibilityLabel values are captured " +
                    "and usually make good locators.",
                tip = "For your own apps: set accessibilityLabel on touchable elements — " +
                    "it is captured as a content description.",
            )
            FrameworkCard(
                name = "WebView / Ionic / Cordova",
                support = Support.PARTIAL,
                summary = "HTML id attributes usually appear as element IDs, plus link " +
                    "and button text. The tree can be briefly empty right after a page " +
                    "loads — pause a moment before tapping.",
            )
            FrameworkCard(
                name = "Games / Unity / canvas UIs",
                support = Support.NONE,
                summary = "No accessibility tree at all — taps inside these apps cannot " +
                    "be captured.",
            )

            SectionTitle("Tips for reliable recordings")
            Bullet("Tap deliberately, one action at a time — rapid taps can merge.")
            Bullet(
                "Swipes, pinch and drag gestures are not captured — only taps, typing " +
                    "and scrolls. Add a note manually to the markdown if a flow needs one.",
            )
            Bullet(
                "Review the journey before exporting: typed values are shown in the " +
                    "step list with a redact button — remove anything sensitive.",
            )
            Bullet(
                "Passwords and fields that look sensitive (PIN, OTP, CVV, card number) " +
                    "are masked automatically and never stored.",
            )
            Bullet(
                "Some banking apps block accessibility services entirely and cannot " +
                    "be recorded.",
            )
            Bullet(
                "If the recorder stops unexpectedly (battery managers can kill it), " +
                    "the journey is kept and marked \"recovered\".",
            )
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
            "full IDs",
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Support.PARTIAL -> Triple(
            "text locators",
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
        )
        Support.NONE -> Triple(
            "not recordable",
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