package com.grandcouncil.remote

import android.content.Intent
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
import androidx.compose.runtime.LaunchedEffect
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
import com.grandcouncil.remote.connection.ConnectionStore
import com.grandcouncil.remote.debug.DebugApiServer
import com.grandcouncil.remote.repository.SessionRepository
import com.grandcouncil.remote.notify.NotificationMonitor
import com.grandcouncil.remote.security.BiometricLock
import com.grandcouncil.remote.ui.AppPreferences
import com.grandcouncil.remote.ui.MainScreen
import com.grandcouncil.remote.ui.SharePayload
import com.grandcouncil.remote.ui.theme.GrandCouncilTheme
import com.grandcouncil.remote.ui.theme.ThemePreset
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : FragmentActivity() {
    // A4 生物识别锁状态（Compose 侧 collectAsState）
    private val lockState = MutableStateFlow(false)
    @Volatile
    private var biometricEnabled = false

    // 任务 702 A③：本会话（进程存活期）是否已通过验证——
    // 守住「开关确认开 → 置锁」不与「已解锁」互相打架；onStop 时复位强制重验
    @Volatile
    private var sessionUnlocked = false

    // 任务 702 B⑤：系统分享待预填文本（ACTION_SEND text/plain → 新建会话草稿）
    private val pendingShareText = MutableStateFlow<String?>(null)

    private var debugApiServer: DebugApiServer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        consumeShareIntent(intent)
        // Debug-only embedded API: host-side tooling drives the real
        // repositories over adb forward instead of UI automation.
        // E1（随 tl1534 族修）：BACK 退出后旧 server 线程仍占 8770，Activity
        // 重建时二次 start 抛 EADDRINUSE 曾致启动 FATAL——debug API 是辅助面，
        // 端口被占时跳过即可（旧实例仍在服务），不得拖垮主程序。
        if ((applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            debugApiServer = DebugApiServer(ConnectionStore(applicationContext), SessionRepository()).also {
                // 信息级（审查建议）：吞 start 失败时留痕，排障不靠端口推断
                runCatching { it.start() }
                    .onFailure { t -> android.util.Log.w("GrandCouncil", "DebugApiServer start skipped", t) }
            }
        }
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
            // 任务 702 B⑤：分享文本状态桥（onNewIntent 更新 → MainScreen 消费后清空）
            val shareText by pendingShareText.collectAsState()
            // A4 生物识别锁：开关开启且设备支持时进入前验证
            val locked by lockState.collectAsState()
            androidx.compose.runtime.LaunchedEffect(Unit) {
                prefs.biometricLock.collect { enabled ->
                    biometricEnabled = enabled
                    // 任务 702 A③ 修复：原条件 `lockState.value` 在冷启动时恒为 false，
                    // 导致「开关开着 → 冷启动不锁」。改为 shouldLock 统一判定。
                    if (BiometricLock.shouldLock(enabled, BiometricLock.isAvailable(context), sessionUnlocked)) {
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
                        // 任务 702 A③：锁屏出现即自动弹一次系统验证（指纹/面容/密码三选一，
                        // 由 BiometricPrompt 的组合 authenticator 决定），失败/取消可手动再弹
                        onUnlock = {
                            BiometricLock.authenticate(
                                this@MainActivity,
                                "解锁 GrandCouncil",
                                onSuccess = {
                                    sessionUnlocked = true
                                    lockState.value = false
                                },
                                onFailed = {},
                            )
                        },
                    )
                } else {
                    MainScreen(
                        initialShareText = shareText,
                        onShareConsumed = { pendingShareText.value = null },
                    )
                }
            }
        }
    }

    // A3 前台不弹：进入后台启动通知监听，回前台停止
    override fun onStart() {
        super.onStart()
        NotificationMonitor.stop()
    }

    // 任务 702 B⑤：复用实例路径（单顶/在栈顶时 SEND 重走 onNewIntent）
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        consumeShareIntent(intent)
    }

    /** 提取 ACTION_SEND text/plain 的分享文本（经 SharePayload 规整；非分享 intent 无副作用） */
    private fun consumeShareIntent(intent: Intent?) {
        if (intent?.action == android.content.Intent.ACTION_SEND &&
            intent.type == "text/plain"
        ) {
            val text = intent.getStringExtra(android.content.Intent.EXTRA_TEXT)
            SharePayload.normalize(text)?.let { pendingShareText.value = it }
        }
    }

    override fun onStop() {
        super.onStop()
        NotificationMonitor.start(applicationContext)
        // A4 进后台重新上锁（开关开启时）；702 A③：并复位会话解锁标志——回前台必须重验
        if (biometricEnabled && BiometricLock.isAvailable(this)) {
            lockState.value = true
            sessionUnlocked = false
        }
    }
}

/** A4 锁屏覆盖：全屏遮罩 + 自动弹一次验证 + 手动解锁按钮兜底 */
@Composable
private fun LockScreen(onUnlock: () -> Unit) {
    // 任务 702 A③：自动弹只发一次（验证取消/失败后停在锁屏，由按钮手动再弹）。
    // delay 缓冲等 Activity resumed——BiometricPrompt 在非 resumed 态弹会直接报错。
    var autoPrompted by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!autoPrompted) {
            autoPrompted = true
            kotlinx.coroutines.delay(400)
            onUnlock()
        }
    }
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
