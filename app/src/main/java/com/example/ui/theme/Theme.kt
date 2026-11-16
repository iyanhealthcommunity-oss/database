package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = IhcPureWhite,
    onPrimary = IhcInkBlack,
    primaryContainer = IhcDarkSurfaceVariant,
    onPrimaryContainer = IhcPureWhite,
    secondary = IhcEmeraldLight,
    onSecondary = IhcInkBlack,
    secondaryContainer = IhcOnEmeraldContainer,
    onSecondaryContainer = IhcEmeraldContainer,
    background = IhcDarkBg,
    onBackground = IhcDarkText,
    surface = IhcDarkSurface,
    onSurface = IhcDarkText,
    surfaceVariant = IhcDarkSurfaceVariant,
    onSurfaceVariant = IhcDarkMuted,
    outline = ColorTokens.DarkBorder,
    error = IhcDangerRed,
    onError = IhcPureWhite
)

private val LightColorScheme = lightColorScheme(
    primary = IhcInkBlack,
    onPrimary = IhcPureWhite,
    primaryContainer = IhcBasicBg,
    onPrimaryContainer = IhcInkBlack,
    secondary = IhcEmerald,
    onSecondary = IhcPureWhite,
    secondaryContainer = IhcEmeraldContainer,
    onSecondaryContainer = IhcOnEmeraldContainer,
    background = IhcSoftPaper,
    onBackground = IhcInkBlack,
    surface = IhcCardSurface,
    onSurface = IhcInkBlack,
    surfaceVariant = IhcSoftPaper,
    onSurfaceVariant = IhcMutedText,
    outline = IhcBorderLight,
    error = IhcDangerRed,
    onError = IhcPureWhite
)

private object ColorTokens {
    val DarkBorder = androidx.compose.ui.graphics.Color(0xFF2E353D)
}

val IhcShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp)
)

@Composable
fun IhcTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = IhcShapes,
        content = content
    )
}
