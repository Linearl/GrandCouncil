package com.grandcouncil.remote.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.grandcouncil.remote.ui.config.ConfigScreen
import com.grandcouncil.remote.ui.demo.DemoChatScreen
import com.grandcouncil.remote.connection.ConnectionProfile
import com.grandcouncil.remote.connection.ConnectionStore
import com.grandcouncil.remote.model.RemoteSession
import com.grandcouncil.remote.repository.SessionRepository
import com.grandcouncil.remote.ui.sessions.AggregatedSession
import com.grandcouncil.remote.ui.sessions.SessionDetailScreen
import com.grandcouncil.remote.ui.sessions.SessionDrawerContent
import com.grandcouncil.remote.ui.wizard.WizardScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

/** 主区页面 */
enum class MainSection { CHAT, CONFIG, WIZARD, DEMO }

/**
 * 主界面（rikkahub 式布局）：左上汉堡 → 抽屉会话栏（默认折叠）；
 * 会话栏底部为「配置」「向导」入口；主区常驻聊天（选中会话后直接展示）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var section by remember { mutableStateOf(MainSection.CHAT) }
    var selectedSession by remember { mutableStateOf<AggregatedSession?>(null) }
    // 草稿聊天（新建会话后进入空白聊天，session=null）
    var draftProfile by remember { mutableStateOf<ConnectionProfile?>(null) }
    val appContext = LocalContext.current.applicationContext
    val prefs = remember { AppPreferences(appContext) }

    // C5 启动恢复：上次浏览位置（连接+会话）→ 自动进入；此后记录浏览位置
    LaunchedEffect(Unit) {
        val lastProfileId = prefs.lastProfileId.firstOrNull()
        val lastSessionId = prefs.lastSessionId.firstOrNull()
        if (lastProfileId != null && lastSessionId != null && selectedSession == null) {
            val profiles: List<ConnectionProfile> =
                runCatching { ConnectionStore(appContext).profiles.first() }.getOrDefault(emptyList())
            val profile = profiles.firstOrNull { it.id == lastProfileId } ?: return@LaunchedEffect
            val sessions: List<RemoteSession> =
                SessionRepository().listSessions(profile).getOrNull() ?: return@LaunchedEffect
            val target = sessions.firstOrNull { it.id == lastSessionId } ?: return@LaunchedEffect
            selectedSession = AggregatedSession(target, profile)
            section = MainSection.CHAT
        }
    }

    // 浏览位置持久化：选中会话 / 新建草稿 / 退出会话
    fun rememberPosition(profile: ConnectionProfile?, sessionId: String?) {
        scope.launch { prefs.setLastPosition(profile?.id, sessionId) }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        // 禁用抽屉拖动手势：会话列表需要左滑露出删除按钮（避免手势冲突）；scrim 点击仍可关闭
        gesturesEnabled = false,
        drawerContent = {
            ModalDrawerSheet {
                SessionDrawerContent(
                    onSelectSession = {
                        selectedSession = it
                        draftProfile = null
                        section = MainSection.CHAT
                        rememberPosition(it.profile, it.session.id)
                        scope.launch { drawerState.close() }
                    },
                    onOpenConfig = {
                        section = MainSection.CONFIG
                        scope.launch { drawerState.close() }
                    },
                    onOpenWizard = {
                        section = MainSection.WIZARD
                        scope.launch { drawerState.close() }
                    },
                    onNewSessionCreated = { profile ->
                        selectedSession = null
                        draftProfile = profile
                        section = MainSection.CHAT
                        rememberPosition(profile, null)
                        scope.launch { drawerState.close() }
                    },
                )
            }
        },
    ) {
        // A3 通知深链：open_session extra → 打开会话栏（列表页）供用户选择
        val activity = LocalContext.current as? android.app.Activity
        LaunchedEffect(Unit) {
            if (activity?.intent?.hasExtra("open_session") == true) {
                drawerState.open()
                activity.intent.removeExtra("open_session")
            }
        }
        Scaffold(
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Filled.Menu, contentDescription = "菜单")
                        }
                    },
                    title = {
                        Text(
                            when (section) {
                                MainSection.CHAT -> selectedSession?.session?.title ?: "会话"
                                MainSection.CONFIG -> "配置"
                                MainSection.WIZARD -> "向导"
                                MainSection.DEMO -> "演示"
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                )
            },
        ) { padding ->
            when (section) {
                MainSection.CHAT -> {
                    val selected = selectedSession
                    val draft = draftProfile
                    when {
                        selected != null -> SessionDetailScreen(
                            profile = selected.profile,
                            session = selected.session,
                            modifier = Modifier.padding(padding),
                        )

                        draft != null -> SessionDetailScreen(
                            profile = draft,
                            session = null,
                            modifier = Modifier.padding(padding),
                        )

                        else -> ChatEmptyHint(
                            modifier = Modifier.padding(padding),
                            onAddConnection = {
                                section = MainSection.WIZARD
                                scope.launch { drawerState.close() }
                            },
                            onTryDemo = { section = MainSection.DEMO },
                        )
                    }
                }

                MainSection.CONFIG -> ConfigScreen()
                MainSection.WIZARD -> WizardScreen()
                MainSection.DEMO -> DemoChatScreen(onBack = { section = MainSection.CHAT })
            }
        }
    }
}

/** 未选会话时的主区提示（C4 空态三入口：添加连接 / 使用指南 / Try a Demo） */
@Composable
private fun ChatEmptyHint(
    modifier: Modifier = Modifier,
    onAddConnection: () -> Unit = {},
    onTryDemo: () -> Unit = {},
) {
    var showGuide by remember { mutableStateOf(false) }
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("⚔", style = MaterialTheme.typography.displayMedium)
            Text(
                "从左侧会话栏选择一个会话开始",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                "首次使用：打开会话栏 → 底部「向导」添加连接",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAddConnection) { Text("➕ 添加连接") }
                OutlinedButton(onClick = { showGuide = true }) { Text("📖 使用指南") }
                TextButton(onClick = onTryDemo) { Text("🎬 Try a Demo") }
            }
        }
    }
    if (showGuide) {
        AlertDialog(
            onDismissRequest = { showGuide = false },
            title = { Text("使用指南") },
            text = {
                Text(
                    "1. 电脑上运行：reasonix serve --addr 0.0.0.0:8787 --auth token\n" +
                        "2. 手机「向导」添加连接（局域网/穿透均可）\n" +
                        "3. 测试通过后保存，会话列表自动出现\n" +
                        "4. 长按会话可左滑删除；审批会推送通知\n" +
                        "5. 没把握？点「Try a Demo」先看效果",
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            confirmButton = {
                TextButton(onClick = { showGuide = false }) { Text("知道了") }
            },
        )
    }
}
