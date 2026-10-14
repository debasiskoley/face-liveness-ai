package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val AppColorScheme =
  lightColorScheme(
    primary = AppButton,
    onPrimary = AppText,
    secondary = AppButton,
    onSecondary = AppText,
    background = AppBackground,
    onBackground = AppText,
    surface = AppSurface,
    onSurface = AppText,
    surfaceVariant = AppSurface,
    onSurfaceVariant = AppText,
    surfaceContainer = AppSurface,
    outline = AppBorder,
    error = AppError,
    onError = AppSurface
  )

@Composable
fun MyApplicationTheme(content: @Composable () -> Unit) {
  MaterialTheme(colorScheme = AppColorScheme, typography = Typography, content = content)
}
