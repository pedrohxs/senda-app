package com.senda.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val SendaDarkColorScheme = darkColorScheme(
    primary = AccentSage,
    onPrimary = DarkCanvas,
    primaryContainer = IconContainer,
    onPrimaryContainer = IconTint,
    secondary = IconTint,
    onSecondary = DarkCanvas,
    secondaryContainer = IconContainer,
    onSecondaryContainer = IconTint,
    tertiary = ChartWarm,
    onTertiary = DarkCanvas,
    background = DarkCanvas,
    onBackground = TextWarmWhite,
    surface = CardSurface,
    onSurface = TextWarmWhite,
    surfaceVariant = SurfaceElevated,
    onSurfaceVariant = TextMutedGrey,
    surfaceTint = AccentSage,
    outline = BorderMuted,
    outlineVariant = BorderMuted,
    scrim = Color.Black,
    inverseSurface = TextWarmWhite,
    inverseOnSurface = DarkCanvas,
    inversePrimary = IconContainer,
    surfaceDim = DarkCanvas,
    surfaceBright = CardSurface,
    surfaceContainerLowest = DarkCanvas,
    surfaceContainerLow = CardSurface,
    surfaceContainer = CardSurface,
    surfaceContainerHigh = SurfaceElevated,
    surfaceContainerHighest = IconContainer
)

private val SendaShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp)
)

@Composable
fun SendaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SendaDarkColorScheme,
        typography = SendaTypography,
        shapes = SendaShapes,
        content = content
    )
}
