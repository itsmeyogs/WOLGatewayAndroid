package com.yihs.wolgateway.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

private val darkColorScheme = darkColorScheme(
    primary = cyan300,
    onPrimary = navy900,
    secondary = blue500,
    tertiary = orange500,
    background = navy900,
    surface = navy900,
    surfaceContainer = navy800,
    surfaceContainerHigh = navy700,
    surfaceContainerHighest = Color(0xFF17436F),
    onBackground = iceWhite,
    onSurface = iceWhite,
    onSurfaceVariant = slate300,
    outline = Color(0xFF25486D),
    error = rustStatus,
    onError = navy900,
)

private val lightColorScheme = lightColorScheme(
    primary = blue500,
    onPrimary = Color.White,
    secondary = slate700,
    tertiary = orange500,
    background = surfaceLight,
    surface = surfaceLight,
    surfaceContainer = surfaceRaisedLight,
    surfaceContainerHigh = surfaceElevatedLight,
    surfaceContainerHighest = Color(0xFFD8EAF9),
    onBackground = ink900,
    onSurface = ink900,
    onSurfaceVariant = slate700,
    outline = Color(0xFFC5D8EA),
    error = rustStatus,
    onError = Color.White,
)

@Composable
fun WOLGatewayTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) darkColorScheme else lightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = appShapes,
        typography = appTypography,
        content = content
    )
}

private val appShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
)
