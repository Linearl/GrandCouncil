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
import com.grandcouncil.remote.notify.AppNotifications
import com.grandcouncil.remote.notify.NotificationMonitor
import com.grandcouncil.remote.ui.AppPreferences
import com.grandcouncil.remote.ui.MainScreen
import com.grandcouncil.remote.ui.theme.GrandCouncilTheme
import com.grandcouncil.remote.ui.theme.ThemePreset

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // A3 通知渠道 + Android 13+ 运行时权限（首次启动请求）
        AppNotifications.createChannels(this)
        AppNotifications.requestPermissionIfNeeded(this)
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

    // A3 前台不弹：进入后台启动通知监听，回前台停止
    override fun onStart() {
        super.onStart()
        NotificationMonitor.stop()
    }

    override fun onStop() {
        super.onStop()
        NotificationMonitor.start(applicationContext)
    }
}
