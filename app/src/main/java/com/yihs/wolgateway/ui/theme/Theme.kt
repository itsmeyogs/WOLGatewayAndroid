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
    secondary = slate700,
    tertiary = cyan300,
    background = navy900,
    surface = navy900,
    surfaceContainer = navy800,
    surfaceContainerHigh = Color(0xFF1D354D),
    onPrimary = navy900,
    onBackground = surfaceLight,
    onSurface = surfaceLight,
)

private val lightColorScheme = lightColorScheme(
    primary = cyan700,
    secondary = slate700,
    tertiary = cyan700,
    background = surfaceLight,
    surface = surfaceLight,
    surfaceContainer = surfaceRaisedLight,
    surfaceContainerHigh = Color(0xFFEAF0F3),
    onPrimary = surfaceLight,
    onBackground = ink900,
    onSurface = ink900,
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
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
)
