package com.tavi.v2ex.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Light = lightColorScheme(
    primary = Color(0xFF35618E), onPrimary = Color.White,
    primaryContainer = Color(0xFFD0E4FF), onPrimaryContainer = Color(0xFF001D35),
    secondaryContainer = Color(0xFFD7E3F7), onSecondaryContainer = Color(0xFF101C2B),
    background = Color(0xFFF8F9FF), surface = Color(0xFFF8F9FF),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF2F3F9),
    surfaceContainer = Color(0xFFECEEF4), surfaceContainerHigh = Color(0xFFE6E8EE),
    surfaceContainerHighest = Color(0xFFE1E2E8), onSurface = Color(0xFF191C20),
    onSurfaceVariant = Color(0xFF43474E), outlineVariant = Color(0xFFC3C7CF),
)
private val Dark = darkColorScheme(
    primary = Color(0xFFA0CAFD), onPrimary = Color(0xFF003258),
    primaryContainer = Color(0xFF194975), onPrimaryContainer = Color(0xFFD0E4FF),
    secondaryContainer = Color(0xFF3B4858), onSecondaryContainer = Color(0xFFD7E3F7),
    background = Color(0xFF111418), surface = Color(0xFF111418),
    surfaceContainerLowest = Color(0xFF0B0E13), surfaceContainerLow = Color(0xFF191C20),
    surfaceContainer = Color(0xFF1D2024), surfaceContainerHigh = Color(0xFF272A2F),
    surfaceContainerHighest = Color(0xFF32353A), onSurface = Color(0xFFE1E2E8),
    onSurfaceVariant = Color(0xFFC3C7CF), outlineVariant = Color(0xFF43474E),
)

@Composable
fun AppTheme(themeMode: Int = 0, dynamic: Boolean = true, content: @Composable () -> Unit) {
    val dark = when (themeMode) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }
    val ctx = LocalContext.current
    val scheme = when {
        dynamic && Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
