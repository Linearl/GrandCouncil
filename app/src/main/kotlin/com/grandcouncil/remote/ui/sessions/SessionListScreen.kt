package com.grandcouncil.remote.ui.sessions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.grandcouncil.remote.R
import com.grandcouncil.remote.connection.ConnectionProfile
import com.grandcouncil.remote.connection.ConnectionStore
import com.grandcouncil.remote.model.HeldBy
import com.grandcouncil.remote.model.RemoteSession
import com.grandcouncil.remote.repository.SessionRepository
import com.grandcouncil.remote.ui.AppPreferences
import com.grandcouncil.remote.ui.theme.DensityPreset
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 会话页（V3）：连接选择 + 服务器状态行 + 筛选 chips（全部/本地聊天/运行中/待审批/失败）
 * + 日期分组（今天/昨天/更早，解析自会话名时间戳）+ 卡片密度（清爽默认/紧凑）。
 * 会话可点击进入只读详情；无连接时显示首启三入口。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionListScreen(
    onNavigateToConfig: () -> Unit = {},
    onNavigateToWizard: () -> Unit = {},
    viewModel: SessionListViewModel = viewModel(
        factory = SessionListViewModelFactory(LocalContext.current.applicationContext),
    ),
) {
    val state by viewModel.uiState.collectAsState()
    var profileMenuOpen by remember { mutableStateOf(false) }
    var selectedSession by remember { mutableStateOf<RemoteSession?>(null) }

    // 详情页：覆盖整个会话页
    val active = state.activeProfile
    val selected = selectedSession
    if (active != null && selected != null) {
        SessionDetailScreen(
            profile = active,
            session = selected,
            onBack = { selectedSession = null },
        )
        return
    }

    // 每次进入会话页自动刷新（连接已选定时）
    LaunchedEffect(Unit) { viewModel.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("会话")
                        if (state.activeProfile != null) {
                            Text(
                                state.activeProfile!!.name,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                actions = {
                    if (state.activeProfile != null) {
                        IconButton(onClick = { viewModel.refresh() }) {
                            Icon(
                                Icons.Filled.Refresh,
                                contentDescription = stringResource(R.string.sessions_refresh),
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.profiles.isEmpty() -> WelcomeEmptyState(
                modifier = Modifier.fillMaxSize().padding(padding),
                onAddConnection = onNavigateToConfig,
                onGuide = onNavigateToWizard,
            )

            else -> Column(Modifier.fillMaxSize().padding(padding)) {
                ProfileSelector(
                    profiles = state.profiles,
                    active = state.activeProfile,
                    expanded = profileMenuOpen,
                    onToggle = { profileMenuOpen = !profileMenuOpen },
                    onSelect = {
                        profileMenuOpen = false
                        viewModel.selectProfile(it)
                    },
                )
                // 服务器状态行（GET /status）
                state.serveInfo?.let { info ->
                    Text(
                        "服务器：${info.label} · ${if (info.running) "运行中" else "空闲"} · ${info.cwd}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                // 筛选 chips（按设备/本地聊天/状态）
                FilterRow(
                    current = state.filter,
                    onSelect = { viewModel.setFilter(it) },
                )
                if (state.loading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (state.error != null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(state.error!!, color = MaterialTheme.colorScheme.error)
                            TextButton(onClick = { viewModel.refresh() }) {
                                Text(stringResource(R.string.sessions_retry))
                            }
                        }
                    }
                } else {
                    val filtered = state.filteredSessions
                    if (filtered.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                if (state.sessions.isEmpty()) stringResource(R.string.sessions_empty)
                                else "该筛选条件下没有会话",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        SessionGroupedList(
                            sessions = filtered,
                            density = state.density,
                            onOpen = { selectedSession = it },
                        )
                    }
                }
            }
        }
    }
}

/** 首启空态三入口（V3）：添加连接 / 使用指南 / Try a Demo */
@Composable
private fun WelcomeEmptyState(
    modifier: Modifier,
    onAddConnection: () -> Unit,
    onGuide: () -> Unit,
) {
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("⚔", style = MaterialTheme.typography.displayLarge)
        Text(
            "欢迎使用 GrandCouncil",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            "在手机上远程指挥你的 AI agent\n随时查看进度、审批操作、继续对话",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(vertical = 10.dp),
        )
        Card(onClick = onAddConnection, modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 4.dp)) {
            ListItem(
                leadingContent = { Text("🔌", style = MaterialTheme.typography.titleMedium) },
                headlineContent = { Text("添加连接") },
                supportingContent = { Text("连接你电脑上的 Reasonix") },
            )
        }
        Card(onClick = { /* TODO(M2): Demo 模式 */ }, modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 4.dp)) {
            ListItem(
                leadingContent = { Text("🎬", style = MaterialTheme.typography.titleMedium) },
                headlineContent = { Text("Try a Demo") },
                supportingContent = { Text("30 秒体验完整流程，无需服务器（M2 开放）") },
            )
        }
        Card(onClick = onGuide, modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 4.dp)) {
            ListItem(
                leadingContent = { Text("📖", style = MaterialTheme.typography.titleMedium) },
                headlineContent = { Text("使用指南") },
                supportingContent = { Text("穿透方式选择与配置说明") },
            )
        }
    }
}

@Composable
private fun FilterRow(current: SessionFilter, onSelect: (SessionFilter) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(SessionFilter.entries) { filter ->
            FilterChip(
                selected = current == filter,
                onClick = { onSelect(filter) },
                label = { Text(filter.label) },
            )
        }
    }
}

/** 会话列表（日期分组：今天/昨天/更早，解析自会话名 YYYYMMDD 前缀） */
@Composable
private fun SessionGroupedList(
    sessions: List<RemoteSession>,
    density: DensityPreset,
    onOpen: (RemoteSession) -> Unit,
) {
    val grouped = remember(sessions) { groupByDay(sessions) }
    val vPad = if (density == DensityPreset.COMPACT) 2.dp else 6.dp

    LazyColumn(Modifier.fillMaxSize()) {
        grouped.forEach { (label, list) ->
            item(key = "day-$label") {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            items(list, key = { it.id }) { session ->
                SessionItem(session, density, Modifier.padding(vertical = vPad), onClick = { onOpen(session) })
            }
        }
    }
}

/** 按日期分组（今天/昨天/更早）——会话名格式：YYYYMMDD-HHMMSS.xxx-model */
private fun groupByDay(sessions: List<RemoteSession>): List<Pair<String, List<RemoteSession>>> {
    val today = LocalDate.now()
    val grouped = linkedMapOf<String, MutableList<RemoteSession>>()
    sessions.forEach { session ->
        val day = parseSessionDate(session.id)
        val label = when (day) {
            null -> "更早"
            today -> "今天"
            today.minusDays(1) -> "昨天"
            else -> "更早"
        }
        grouped.getOrPut(label) { mutableListOf() }.add(session)
    }
    return grouped.toList()
}

private fun parseSessionDate(sessionId: String): LocalDate? = runCatching {
    val prefix = sessionId.take(8)
    LocalDate.parse(prefix, DateTimeFormatter.BASIC_ISO_DATE)
}.getOrNull()

@Composable
private fun ProfileSelector(
    profiles: List<ConnectionProfile>,
    active: ConnectionProfile?,
    expanded: Boolean,
    onToggle: () -> Unit,
    onSelect: (ConnectionProfile) -> Unit,
) {
    Box(Modifier.fillMaxWidth()) {
        Card(
            onClick = onToggle,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            ListItem(
                headlineContent = { Text(active?.name ?: "选择连接") },
                supportingContent = {
                    Text("${active?.type?.label.orEmpty()} · ${active?.baseUrl.orEmpty()}")
                },
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = onToggle) {
            profiles.forEach { profile ->
                DropdownMenuItem(
                    text = { Text(profile.name) },
                    onClick = { onSelect(profile) },
                )
            }
        }
    }
}

@Composable
private fun SessionItem(
    session: RemoteSession,
    density: DensityPreset,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val hPad = if (density == DensityPreset.COMPACT) 12.dp else 16.dp
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().padding(horizontal = hPad),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    session.title,
                    style = if (density == DensityPreset.COMPACT) MaterialTheme.typography.bodyMedium
                    else MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    buildString {
                        append("${session.turns} 轮")
                        if (session.isCurrent) append(" · 当前")
                        if (session.agent == com.grandcouncil.remote.model.AgentType.REASONIX) append(" · Reasonix")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (session.heldBy == HeldBy.OTHER) {
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = stringResource(R.string.sessions_held_other),
                    tint = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }
}

/** ViewModel 工厂：注入 ConnectionStore/SessionRepository/AppPreferences */
class SessionListViewModelFactory(private val appContext: android.content.Context) :
    androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        return SessionListViewModel(
            ConnectionStore(appContext),
            SessionRepository(),
            AppPreferences(appContext),
        ) as T
    }
}
