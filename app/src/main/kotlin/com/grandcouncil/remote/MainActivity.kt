package com.grandcouncil.remote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.lifecycleScope
import com.grandcouncil.remote.ui.AppPreferences
import com.grandcouncil.remote.ui.MainScreen
import com.grandcouncil.remote.ui.theme.GrandCouncilTheme
import com.grandcouncil.remote.ui.theme.ThemePreset
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // 主题偏好（默认暖阳；dark/light 跟随系统）
            val context = LocalContext.current
            val prefs = remember { AppPreferences(context) }
            var themePreset by remember { mutableStateOf(ThemePreset.WARM) }
            androidx.compose.runtime.LaunchedEffect(Unit) {
                prefs.theme.collect { themePreset = it }
            }
            GrandCouncilTheme(
                preset = themePreset,
                darkTheme = isSystemInDarkTheme(),
            ) {
                MainScreen()
            }
        }
    }
}
