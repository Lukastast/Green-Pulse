package com.example.green_pulse_android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightEarthySageGrove = lightColorScheme(
    primary = Color(0xFF9CAF88),
    secondary = Color(0xFF758467),
    tertiary = Color(0xFF7F4F24),
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFfdfdfd)
)

private val DarkMidnightGrove = darkColorScheme(
    primary = Color(0xFF4D5E3E),
    secondary = Color(0xFF3A4A2A),
    tertiary = Color(0xFF5A3A1E),
    background = Color(0xFF0F140D),
    surface = Color(0xFF1F241C)
)

@Composable
fun GreenPulseAndroidTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) {
        DarkMidnightGrove
    } else {
        LightEarthySageGrove
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}