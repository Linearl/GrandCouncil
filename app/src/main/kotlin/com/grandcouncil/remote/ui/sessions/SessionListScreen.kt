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
import com.grandcouncil.remote.repository.SessionRepository
import com.grandcouncil.remote.ui.AppPreferences
import com.grandcouncil.remote.ui.theme.DensityPreset
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 会话页（聚合视图）：全部连接（设备/项目）会话合并展示。
 * 设备筛选（全部 + 各连接）+ 状态筛选 chips + 日期分组 + 卡片密度。
 * 会话点击进入详情（携带所属连接）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionListScreen(
    onNavigateToConfig: () -> Unit = {},
    onNavigateToWizard: () -> Unit = {},
    onDetailOpen: () -> Unit = {},
    onDetailClose: () -> Unit = {},
    viewModel: SessionListViewModel = viewModel(
        factory = SessionListViewModelFactory(LocalContext.current.applicationContext),
    ),
) {
    val state by viewModel.uiState.collectAsState()
    var deviceMenuOpen by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<AggregatedSession?>(null) }

    // 详情页：覆盖整个会话页（全屏，隐藏底部三栏）
    val selectedItem = selected
    if (selectedItem != null) {
        LaunchedEffect(selectedItem) { onDetailOpen() }
        SessionDetailScreen(
            profile = selectedItem.profile,
            session = selectedItem.session,
            onBack = {
                selected = null
                onDetailClose()
            },
        )
        return
    }

    // 每次进入会话页自动刷新
    LaunchedEffect(Unit) { viewModel.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("会话")
                        val online = state.onlineCount
                        Text(
                            if (online > 0) "$online 台设备在线" else "暂无在线设备",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (online > 0) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = stringResource(R.string.sessions_refresh),
                        )
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
                DeviceSelector(
                    profiles = state.profiles,
                    selectedId = state.deviceFilter,
                    onlineIds = state.serveInfoByProfile.keys,
                    expanded = deviceMenuOpen,
                    onToggle = { deviceMenuOpen = !deviceMenuOpen },
                    onSelect = {
                        deviceMenuOpen = false
                        viewModel.setDeviceFilter(it)
                    },
                )
                // 状态筛选 chips
                FilterRow(
                    current = state.filter,
                    onSelect = { viewModel.setFilter(it) },
                )
                if (state.loading && state.filteredSessions.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (state.error != null && state.filteredSessions.isEmpty()) {
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
                                if (state.aggregated.isEmpty()) stringResource(R.string.sessions_empty)
                                else "该筛选条件下没有会话",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        SessionGroupedList(
                            sessions = filtered,
                            density = state.density,
                            multiDevice = state.profiles.size > 1,
                            onOpen = { selected = it },
                        )
                    }
                }
            }
        }
    }
}

/** 设备筛选选择器（全部设备 + 各连接；在线状态点） */
@Composable
private fun DeviceSelector(
    profiles: List<ConnectionProfile>,
    selectedId: String?,
    onlineIds: Set<String>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onSelect: (String?) -> Unit,
) {
    Box(Modifier.fillMaxWidth()) {
        Card(
            onClick = onToggle,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        ) {
            Row(
                Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val online = if (selectedId == null) onlineIds.size else if (selectedId in onlineIds) 1 else 0
                Text("●", color = if (online > 0) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    when {
                        selectedId == null -> "全部设备（${profiles.size}）"
                        else -> profiles.firstOrNull { it.id == selectedId }?.name ?: "选择设备"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                Text("▾", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = onToggle) {
            DropdownMenuItem(
                text = { Text("全部设备（${profiles.size}）") },
                onClick = { onSelect(null) },
            )
            profiles.forEach { profile ->
                DropdownMenuItem(
                    text = {
                        Text(
                            buildString {
                                append(profile.name)
                                if (profile.id in onlineIds) append("  ●在线")
                            },
                        )
                    },
                    onClick = { onSelect(profile.id) },
                )
            }
        }
    }
}

/** 首启空态三入口（V3） */
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
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("🔌", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(end = 10.dp))
                Column {
                    Text("添加连接", style = MaterialTheme.typography.titleSmall)
                    Text("连接你电脑上的 Reasonix", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Card(onClick = { /* TODO(M2): Demo 模式 */ }, modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 4.dp)) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("🎬", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(end = 10.dp))
                Column {
                    Text("Try a Demo", style = MaterialTheme.typography.titleSmall)
                    Text("30 秒体验完整流程，无需服务器（M2 开放）", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Card(onClick = onGuide, modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 4.dp)) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("📖", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(end = 10.dp))
                Column {
                    Text("使用指南", style = MaterialTheme.typography.titleSmall)
                    Text("穿透方式选择与配置说明", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
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

/** 会话列表（日期分组；多设备时显示设备标签） */
@Composable
private fun SessionGroupedList(
    sessions: List<AggregatedSession>,
    density: DensityPreset,
    multiDevice: Boolean,
    onOpen: (AggregatedSession) -> Unit,
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
            items(list, key = { "${it.profile.id}-${it.session.id}" }) { item ->
                SessionItem(
                    item = item,
                    density = density,
                    showDevice = multiDevice,
                    modifier = Modifier.padding(vertical = vPad),
                    onClick = { onOpen(item) },
                )
            }
        }
    }
}

/** 按日期分组（今天/昨天/更早）——会话名格式：YYYYMMDD-HHMMSS.xxx-model */
private fun groupByDay(sessions: List<AggregatedSession>): List<Pair<String, List<AggregatedSession>>> {
    val today = LocalDate.now()
    val grouped = linkedMapOf<String, MutableList<AggregatedSession>>()
    sessions.forEach { item ->
        val day = parseSessionDate(item.session.id)
        val label = when (day) {
            null -> "更早"
            today -> "今天"
            today.minusDays(1) -> "昨天"
            else -> "更早"
        }
        grouped.getOrPut(label) { mutableListOf() }.add(item)
    }
    return grouped.toList()
}

private fun parseSessionDate(sessionId: String): LocalDate? = runCatching {
    LocalDate.parse(sessionId.take(8), DateTimeFormatter.BASIC_ISO_DATE)
}.getOrNull()

@Composable
private fun SessionItem(
    item: AggregatedSession,
    density: DensityPreset,
    showDevice: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val session = item.session
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
                        if (showDevice) append("${item.profile.name} · ")
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
