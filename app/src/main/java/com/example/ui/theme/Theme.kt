package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AmoledDarkColorScheme = darkColorScheme(
    primary = AmoledPrimary,
    onPrimary = AmoledBlack,
    primaryContainer = AmoledPrimaryContainer,
    onPrimaryContainer = AmoledOnPrimaryContainer,
    secondary = AmoledSecondary,
    onSecondary = AmoledBlack,
    secondaryContainer = AmoledSecondaryContainer,
    onSecondaryContainer = AmoledOnSecondaryContainer,
    tertiary = Color(0xFFA78BFA),
    onTertiary = AmoledBlack,
    background = AmoledBlack,
    onBackground = AmoledTextPrimary,
    surface = AmoledBlack,
    onSurface = AmoledTextPrimary,
    surfaceVariant = AmoledCard,
    onSurfaceVariant = AmoledTextSecondary,
    surfaceContainer = AmoledCard,
    surfaceContainerLow = AmoledBlack,
    surfaceContainerHigh = AmoledCardHover,
    surfaceContainerHighest = Color(0xFF1E1E1E),
    error = AmoledError,
    onError = AmoledBlack,
    errorContainer = AmoledErrorContainer,
    onErrorContainer = AmoledOnErrorContainer,
    outline = AmoledBorder,
    outlineVariant = AmoledBorderLight
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = AmoledDarkColorScheme,
        typography = Typography,
        content = content
    )
}
