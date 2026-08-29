package com.example.mobile_image_retrieval.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF246BFD),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8F0FF),
    background = Color(0xFFFBFCFE),
    surface = Color.White,
    surfaceVariant = Color(0xFFF1F3F6),
    onSurfaceVariant = Color(0xFF697386),
    outline = Color(0xFFD9DEE8),
)

@Composable
fun PhotoSearchTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LightColors, content = content)
}
