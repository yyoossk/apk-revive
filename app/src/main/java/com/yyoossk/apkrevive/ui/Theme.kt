package com.yyoossk.apkrevive.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary = Color(0xFF2E6B4F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDEBD9),
    onPrimaryContainer = Color(0xFF0B2B1C),
    secondary = Color(0xFF52634F),
    secondaryContainer = Color(0xFFD5E8CF),
    tertiary = Color(0xFF8A5A00),
    tertiaryContainer = Color(0xFFFFDDB0),
    error = Color(0xFFB3261E),
    errorContainer = Color(0xFFF9DEDC),
    background = Color(0xFFF7F9F4),
    surface = Color(0xFFF7F9F4),
    surfaceVariant = Color(0xFFE0E6DD),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF9DD5B7),
    onPrimary = Color(0xFF00381F),
    primaryContainer = Color(0xFF145238),
    onPrimaryContainer = Color(0xFFCDEBD9),
    secondary = Color(0xFFB9CCB4),
    secondaryContainer = Color(0xFF3B4B39),
    tertiary = Color(0xFFFFB950),
    tertiaryContainer = Color(0xFF653F00),
    error = Color(0xFFF2B8B5),
    errorContainer = Color(0xFF8C1D18),
    background = Color(0xFF111411),
    surface = Color(0xFF111411),
    surfaceVariant = Color(0xFF414941),
)

@Composable
fun ReviveTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
