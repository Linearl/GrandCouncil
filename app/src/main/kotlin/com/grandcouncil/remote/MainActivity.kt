package com.grandcouncil.remote

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.grandcouncil.remote.notify.AppNotifications
import com.grandcouncil.remote.notify.NotificationMonitor
import com.grandcouncil.remote.security.BiometricLock
import com.grandcouncil.remote.ui.AppPreferences
import com.grandcouncil.remote.ui.MainScreen
import com.grandcouncil.remote.ui.theme.GrandCouncilTheme
import com.grandcouncil.remote.ui.theme.ThemePreset
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : FragmentActivity() {
    // A4 生物识别锁状态（Compose 侧 collectAsState）
    private val lockState = MutableStateFlow(false)
    @Volatile
    private var biometricEnabled = false

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
            var amoledDark by remember { mutableStateOf(false) }
            var dynamicColor by remember { mutableStateOf(false) }
            androidx.compose.runtime.LaunchedEffect(Unit) {
                prefs.theme.collect { themePreset = it }
                prefs.amoledDark.collect { amoledDark = it }
                prefs.dynamicColor.collect { dynamicColor = it }
            }
            // A4 生物识别锁：开关开启且设备支持时进入前验证
            val locked by lockState.collectAsState()
            androidx.compose.runtime.LaunchedEffect(Unit) {
                prefs.biometricLock.collect { enabled ->
                    biometricEnabled = enabled
                    if (enabled && BiometricLock.isAvailable(context) && lockState.value) {
                        lockState.value = true
                    }
                }
            }
            GrandCouncilTheme(
                preset = themePreset,
                darkTheme = isSystemInDarkTheme(),
                amoledDark = amoledDark,
                dynamicColor = dynamicColor,
            ) {
                if (locked) {
                    LockScreen(
                        onUnlock = {
                            BiometricLock.authenticate(
                                this@MainActivity,
                                "解锁 GrandCouncil",
                                onSuccess = { lockState.value = false },
                                onFailed = {},
                            )
                        },
                    )
                } else {
                    MainScreen()
                }
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
        // A4 进后台重新上锁（开关开启时）
        if (biometricEnabled && BiometricLock.isAvailable(this)) {
            lockState.value = true
        }
    }
}

/** A4 锁屏覆盖：全屏遮罩 + 解锁按钮 */
@Composable
private fun LockScreen(onUnlock: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🔒", style = MaterialTheme.typography.displayLarge)
            Text(
                "GrandCouncil 已锁定",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
            Button(
                onClick = onUnlock,
                modifier = Modifier.padding(top = 20.dp),
            ) {
                Text("解锁")
            }
        }
    }
}
