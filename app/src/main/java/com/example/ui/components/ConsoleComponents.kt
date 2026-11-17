package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.ConnectionState
import com.example.ui.theme.AlertCrimson
import com.example.ui.theme.AlertCrimsonContainer
import com.example.ui.theme.CautionAmber
import com.example.ui.theme.CautionAmberContainer
import com.example.ui.theme.ConsoleBorder
import com.example.ui.theme.ConsoleSurface
import com.example.ui.theme.ConsoleSurfaceElevated
import com.example.ui.theme.ConsoleTextMuted
import com.example.ui.theme.ConsoleTextPrimary
import com.example.ui.theme.ConsoleTextSecondary
import com.example.ui.theme.DiagnosticEmerald
import com.example.ui.theme.DiagnosticEmeraldContainer
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.PhosphorCyan
import com.example.ui.theme.PhosphorCyanContainer

@Composable
fun ConsolePanel(
    modifier: Modifier = Modifier,
    borderColor: Color = ConsoleBorder,
    containerColor: Color = ConsoleSurface,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = containerColor,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, borderColor),
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
fun ConsoleSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = "//",
                style = MaterialTheme.typography.labelLarge,
                color = PhosphorCyan,
                fontFamily = JetBrainsMonoFontFamily,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = ConsoleTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (trailing != null) {
            trailing()
        }
    }
}

@Composable
fun DeskLinkScreenHeader(
    title: String,
    subtitle: String,
    connectionState: ConnectionState,
    latencyMs: Long?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = ConsoleTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(8.dp))
            ConnectionStatusBadge(
                connectionState = connectionState,
                latencyMs = latencyMs,
            )
        }
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = ConsoleTextMuted,
        )
    }
}

@Composable
fun ConnectionStatusBadge(
    connectionState: ConnectionState,
    latencyMs: Long?,
    modifier: Modifier = Modifier,
) {
    val (fg, bg, label) = when (connectionState) {
        ConnectionState.ONLINE -> Triple(
            DiagnosticEmerald,
            DiagnosticEmeraldContainer,
            if (latencyMs != null && latencyMs > 0) "ONLINE • ${latencyMs}ms" else "ONLINE",
        )
        ConnectionState.CONNECTING -> Triple(
            PhosphorCyan,
            PhosphorCyanContainer,
            "CONNECTING…",
        )
        ConnectionState.AUTH_REQUIRED -> Triple(
            CautionAmber,
            CautionAmberContainer,
            "PIN REQUIRED",
        )
        ConnectionState.ERROR -> Triple(
            AlertCrimson,
            AlertCrimsonContainer,
            "UNREACHABLE",
        )
        ConnectionState.DISCONNECTED -> Triple(
            ConsoleTextSecondary,
            ConsoleSurfaceElevated,
            "OFFLINE",
        )
    }

    val animatedFg by animateColorAsState(targetValue = fg, label = "statusFg")
    val pulseTransition = rememberInfiniteTransition(label = "onlinePulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (connectionState == ConnectionState.ONLINE) 0.35f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseAlpha",
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .testTag("connection_status_badge")
            .background(color = bg, shape = RoundedCornerShape(999.dp))
            .border(width = 1.dp, color = animatedFg.copy(alpha = 0.45f), shape = RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .alpha(pulseAlpha)
                .background(animatedFg, CircleShape),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = animatedFg,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
fun TelemetryRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = ConsoleTextPrimary,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = ConsoleTextSecondary,
            modifier = Modifier.padding(end = 12.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = valueColor,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun PowerActionTile(
    label: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    containerColor: Color,
    enabled: Boolean,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .heightIn(min = 84.dp)
            .testTag(testTag),
        shape = MaterialTheme.shapes.medium,
        color = if (enabled) containerColor else ConsoleSurfaceElevated.copy(alpha = 0.45f),
        border = BorderStroke(
            1.dp,
            if (enabled) accentColor.copy(alpha = 0.45f) else ConsoleBorder,
        ),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (enabled) accentColor else ConsoleTextMuted,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (enabled) ConsoleTextPrimary else ConsoleTextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (enabled) ConsoleTextSecondary else ConsoleTextMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
