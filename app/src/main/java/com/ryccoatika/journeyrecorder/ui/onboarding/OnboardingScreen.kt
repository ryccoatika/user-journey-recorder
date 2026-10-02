package com.ryccoatika.journeyrecorder.ui.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.outlined.AppRegistration
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.absoluteValue
import kotlinx.coroutines.launch

private enum class Accent { PRIMARY, TERTIARY }

private data class OnboardingPage(
    val icon: ImageVector,
    val title: String,
    val body: String,
    val accent: Accent = Accent.PRIMARY,
    val steps: List<Triple<ImageVector, String, String>> = emptyList(),
    val privacy: Boolean = false,
)

private val pages = listOf(
    OnboardingPage(
        icon = Icons.Filled.PlayCircleOutline,
        title = "Record journeys\nin any app",
        body = "Capture every tap, text input and screen change while you walk " +
            "through a flow in another app — then export the steps as markdown " +
            "for an AI agent to turn into test automation.",
    ),
    OnboardingPage(
        icon = Icons.Outlined.TouchApp,
        title = "Three simple\nsteps",
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
        title = "Your data never\nleaves this device",
        body = "Nothing is ever sent to a server. Recordings stay on-device until " +
            "you choose to export them. Passwords and sensitive fields are masked " +
            "automatically and never stored.",
        accent = Accent.TERTIARY,
        privacy = true,
    ),
)

@Composable
private fun Accent.color(): Color = when (this) {
    Accent.PRIMARY -> MaterialTheme.colorScheme.primary
    Accent.TERTIARY -> MaterialTheme.colorScheme.tertiary
}

@Composable
private fun Accent.container(): Color = when (this) {
    Accent.PRIMARY -> MaterialTheme.colorScheme.primaryContainer
    Accent.TERTIARY -> MaterialTheme.colorScheme.tertiaryContainer
}

@Composable
private fun Accent.onColor(): Color = when (this) {
    Accent.PRIMARY -> MaterialTheme.colorScheme.onPrimary
    Accent.TERTIARY -> MaterialTheme.colorScheme.onTertiary
}

@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val lastPage = pagerState.currentPage == pages.lastIndex

    val accent = pages[pagerState.currentPage].accent.color()
    val accentContainer = pages[pagerState.currentPage].accent.container()
    val animatedGlow by animateColorAsState(
        accentContainer.copy(alpha = 0.55f),
        animationSpec = tween(500),
        label = "glow",
    )
    val background = MaterialTheme.colorScheme.background

    Scaffold(containerColor = background) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                // Soft top-anchored radial wash in the current page's accent.
                .drawBehind {
                    drawRect(
                        Brush.radialGradient(
                            colors = listOf(animatedGlow, background.copy(alpha = 0f)),
                            center = Offset(size.width / 2f, size.height * 0.30f),
                            radius = size.width * 0.9f,
                        ),
                    )
                },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
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
                    // Parallax + fade driven by the swipe offset.
                    val offset = (pagerState.currentPage - page) +
                        pagerState.currentPageOffsetFraction
                    OnboardingPageContent(page = pages[page], pageOffset = offset)
                }

                PageIndicator(
                    count = pages.size,
                    current = pagerState.currentPage,
                    accent = accent,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(vertical = 12.dp),
                )

                Button(
                    onClick = {
                        if (lastPage) {
                            onFinish()
                        } else {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accent,
                        contentColor = pages[pagerState.currentPage].accent.onColor(),
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                        .height(56.dp),
                ) {
                    Text(
                        if (lastPage) "Get started" else "Next",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun OnboardingPageContent(page: OnboardingPage, pageOffset: Float) {
    val fade = (1f - pageOffset.absoluteValue).coerceIn(0f, 1f)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        GlowingIcon(
            icon = page.icon,
            accent = page.accent.color(),
            accentContainer = page.accent.container(),
            onAccent = page.accent.onColor(),
            modifier = Modifier.graphicsLayer {
                translationX = pageOffset * size.width * 0.6f
                alpha = fade
                scaleX = 0.85f + 0.15f * fade
                scaleY = 0.85f + 0.15f * fade
            },
        )
        Spacer(Modifier.height(36.dp))
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.graphicsLayer {
                translationX = pageOffset * size.width * 0.25f
                alpha = fade
            },
        ) {
            Text(
                text = page.title,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                lineHeight = MaterialTheme.typography.displaySmall.fontSize * 1.1f,
            )
            if (page.body.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
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
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    page.steps.forEachIndexed { i, (icon, title, subtitle) ->
                        StepRow(number = i + 1, icon = icon, title = title, subtitle = subtitle)
                    }
                }
            }
            if (page.privacy) {
                Spacer(Modifier.height(24.dp))
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Outlined.CloudOff,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
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
}

@Composable
private fun GlowingIcon(
    icon: ImageVector,
    accent: Color,
    accentContainer: Color,
    onAccent: Color,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "iconPulse")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            tween(1800, easing = FastOutSlowInEasing),
            RepeatMode.Reverse,
        ),
        label = "pulse",
    )
    Box(contentAlignment = Alignment.Center, modifier = modifier) {
        // Outer soft glow ring (pulsing).
        Box(
            modifier = Modifier
                .size(172.dp)
                .scale(pulse)
                .background(accentContainer.copy(alpha = 0.45f), CircleShape),
        )
        Box(
            modifier = Modifier
                .size(136.dp)
                .background(accentContainer.copy(alpha = 0.7f), CircleShape),
        )
        // Solid gradient core.
        Box(
            modifier = Modifier
                .size(104.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(
                            lerp(accent, Color.White, 0.18f),
                            accent,
                            lerp(accent, Color.Black, 0.12f),
                        ),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = onAccent,
                modifier = Modifier.size(52.dp),
            )
        }
    }
}

@Composable
private fun StepRow(number: Int, icon: ImageVector, title: String, subtitle: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Box(modifier = Modifier.size(46.dp), contentAlignment = Alignment.Center) {
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
                style = MaterialTheme.typography.titleMedium,
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
private fun PageIndicator(count: Int, current: Int, accent: Color, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(count) { i ->
            val selected = i == current
            val width by animateDpAsState(if (selected) 28.dp else 8.dp, label = "dotWidth")
            val color by animateColorAsState(
                if (selected) accent else MaterialTheme.colorScheme.surfaceContainerHighest,
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
