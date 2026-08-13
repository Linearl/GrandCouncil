package com.grandcouncil.remote.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Card
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.grandcouncil.remote.ui.config.ConfigScreen
import com.grandcouncil.remote.ui.sessions.AggregatedSession
import com.grandcouncil.remote.ui.sessions.SessionDetailScreen
import com.grandcouncil.remote.ui.sessions.SessionDrawerContent
import com.grandcouncil.remote.ui.wizard.WizardScreen
import kotlinx.coroutines.launch

/** 主区页面 */
enum class MainSection { CHAT, CONFIG, WIZARD }

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

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                SessionDrawerContent(
                    onSelectSession = {
                        selectedSession = it
                        section = MainSection.CHAT
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
                )
            }
        },
    ) {
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
                    if (selected != null) {
                        SessionDetailScreen(
                            profile = selected.profile,
                            session = selected.session,
                            modifier = Modifier.padding(padding),
                        )
                    } else {
                        ChatEmptyHint(Modifier.padding(padding))
                    }
                }

                MainSection.CONFIG -> ConfigScreen()
                MainSection.WIZARD -> WizardScreen()
            }
        }
    }
}

/** 未选会话时的主区提示 */
@Composable
private fun ChatEmptyHint(modifier: Modifier = Modifier) {
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
        }
    }
}
