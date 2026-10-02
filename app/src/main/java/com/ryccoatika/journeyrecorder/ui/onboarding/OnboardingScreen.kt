package com.ryccoatika.journeyrecorder.ui.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.outlined.AppRegistration
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private data class OnboardingPage(
    val icon: ImageVector,
    val title: String,
    val body: String,
    val steps: List<Triple<ImageVector, String, String>> = emptyList(),
    val highlight: Boolean = false, // privacy page: accent framing
)

private val pages = listOf(
    OnboardingPage(
        icon = Icons.Filled.PlayCircleOutline,
        title = "Record journeys in any app",
        body = "Journey Recorder captures every tap, text input and screen change " +
            "while you walk through a flow in another app — then exports the steps " +
            "as markdown you can hand to an AI agent to generate test automation.",
    ),
    OnboardingPage(
        icon = Icons.Outlined.TouchApp,
        title = "Three simple steps",
        body = "",
        steps = listOf(
            Triple(
                Icons.Outlined.AppRegistration,
                "Enable & pick an app",
                "Turn on the accessibility service, choose the app to record",
            ),
            Triple(
                Icons.Outlined.TouchApp,
                "Walk through the flow",
                "A floating button records every step; tap it to pause or stop",
            ),
            Triple(
                Icons.Outlined.Description,
                "Review & export",
                "Redact anything sensitive, then share or save the markdown",
            ),
        ),
    ),
    OnboardingPage(
        icon = Icons.Outlined.Lock,
        title = "Your data never leaves this device",
        body = "Nothing is ever sent to a server. Recordings stay on-device until " +
            "you choose to export them. Passwords and sensitive fields are masked " +
            "automatically and never stored.",
        highlight = true,
    ),
)

@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val lastPage = pagerState.currentPage == pages.lastIndex

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // Skip (hidden on the last page).
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
                if (!lastPage) {
                    TextButton(
                        onClick = onFinish,
                        modifier = Modifier.align(Alignment.CenterEnd),
                    ) { Text("Skip") }
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) { page ->
                OnboardingPageContent(pages[page])
            }

            PageIndicator(
                count = pages.size,
                current = pagerState.currentPage,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(vertical = 8.dp),
            )

            Button(
                onClick = {
                    if (lastPage) {
                        onFinish()
                    } else {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .height(52.dp),
            ) {
                Text(
                    if (lastPage) "Get started" else "Next",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

@Composable
private fun OnboardingPageContent(page: OnboardingPage) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = if (page.highlight) {
                MaterialTheme.colorScheme.tertiaryContainer
            } else {
                MaterialTheme.colorScheme.primaryContainer
            },
        ) {
            Icon(
                page.icon,
                contentDescription = null,
                tint = if (page.highlight) {
                    MaterialTheme.colorScheme.onTertiaryContainer
                } else {
                    MaterialTheme.colorScheme.onPrimaryContainer
                },
                modifier = Modifier
                    .padding(28.dp)
                    .size(52.dp),
            )
        }
        Spacer(Modifier.height(28.dp))
        Text(
            text = page.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        if (page.body.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = page.body,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 420.dp),
            )
        }
        if (page.steps.isNotEmpty()) {
            Spacer(Modifier.height(28.dp))
            Column(
                modifier = Modifier.widthIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                page.steps.forEachIndexed { i, (icon, title, subtitle) ->
                    StepRow(number = i + 1, icon = icon, title = title, subtitle = subtitle)
                }
            }
        }
        if (page.highlight) {
            Spacer(Modifier.height(24.dp))
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.CloudOff, contentDescription = null)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "100% on-device · no account, no network",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun StepRow(number: Int, icon: ImageVector, title: String, subtitle: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(
                "$number. $title",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PageIndicator(count: Int, current: Int, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(count) { i ->
            val selected = i == current
            val width by animateDpAsState(if (selected) 24.dp else 8.dp, label = "dotWidth")
            val color by animateColorAsState(
                if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                },
                label = "dotColor",
            )
            Box(
                modifier = Modifier
                    .height(8.dp)
                    .width(width)
                    .background(color, CircleShape),
            )
        }
    }
}
