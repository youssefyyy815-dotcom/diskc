package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val DeskLinkDarkColorScheme = darkColorScheme(
    primary = PhosphorCyan,
    onPrimary = ConsoleBackground,
    primaryContainer = PhosphorCyanContainer,
    onPrimaryContainer = PhosphorCyan,
    secondary = DiagnosticEmerald,
    onSecondary = ConsoleBackground,
    secondaryContainer = DiagnosticEmeraldContainer,
    onSecondaryContainer = DiagnosticEmerald,
    tertiary = CautionAmber,
    onTertiary = ConsoleBackground,
    tertiaryContainer = CautionAmberContainer,
    onTertiaryContainer = CautionAmber,
    error = AlertCrimson,
    onError = ConsoleTextPrimary,
    errorContainer = AlertCrimsonContainer,
    onErrorContainer = AlertCrimson,
    background = ConsoleBackground,
    onBackground = ConsoleTextPrimary,
    surface = ConsoleSurface,
    onSurface = ConsoleTextPrimary,
    surfaceVariant = ConsoleSurfaceElevated,
    onSurfaceVariant = ConsoleTextSecondary,
    outline = ConsoleBorder,
    outlineVariant = ConsoleBorderBright,
)

private val ConsoleShapes = Shapes(
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(14.dp),
)

@Composable
fun DeskLinkTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = DeskLinkDarkColorScheme,
        typography = ConsoleTypography,
        shapes = ConsoleShapes,
        content = content,
    )
}
