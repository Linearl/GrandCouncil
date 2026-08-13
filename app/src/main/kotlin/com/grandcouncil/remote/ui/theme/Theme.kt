package com.grandcouncil.remote.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// GrandCouncil 品牌色：军机处意象——深绀蓝 + 朱红点缀
private val BrandBlue = Color(0xFF1A3A5C)
private val BrandBlueLight = Color(0xFF2E5C8A)
private val BrandRed = Color(0xFFB03A2E)

private val LightColors = lightColorScheme(
    primary = BrandBlueLight,
    secondary = BrandRed,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8FB3D9),
    secondary = Color(0xFFE08A7C),
)

@Composable
fun GrandCouncilTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
