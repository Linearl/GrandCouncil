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
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.grandcouncil.remote.connection.ConnectionProfile
import com.grandcouncil.remote.connection.ConnectionStore
import com.grandcouncil.remote.model.HeldBy
import com.grandcouncil.remote.repository.SessionRepository
import com.grandcouncil.remote.ui.AppPreferences
import com.grandcouncil.remote.ui.theme.DensityPreset
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 会话抽屉栏（rikkahub 式）：设备筛选 + 状态筛选 + 会话列表（日期分组）。
 * 底部固定「配置」「向导」入口。默认由汉堡菜单折叠，展开后占左侧。
 */
@Composable
fun SessionDrawerContent(
    onSelectSession: (AggregatedSession) -> Unit,
    onOpenConfig: () -> Unit,
    onOpenWizard: () -> Unit,
    viewModel: SessionListViewModel = viewModel(
        factory = SessionListViewModelFactory(LocalContext.current.applicationContext),
    ),
) {
    val state by viewModel.uiState.collectAsState()
    var deviceMenuOpen by remember { mutableStateOf(false) }

    // 每次抽屉展开/组合时刷新
    LaunchedEffect(Unit) { viewModel.refresh() }

    Column(Modifier.fillMaxSize()) {
        // 顶部：设备选择器
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
        // 状态筛选
        FilterRow(
            current = state.filter,
            onSelect = { viewModel.setFilter(it) },
        )

        // 会话列表（占满剩余空间）
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.profiles.isEmpty() -> Column(
                    Modifier.fillMaxSize().padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("⚔", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "还没有连接，先去「向导」添加",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }

                state.loading && state.filteredSessions.isEmpty() ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }

                state.error != null && state.filteredSessions.isEmpty() ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(state.error!!, color = MaterialTheme.colorScheme.error)
                            TextButton(onClick = { viewModel.refresh() }) { Text("重试") }
                        }
                    }

                state.filteredSessions.isEmpty() ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            if (state.aggregated.isEmpty()) "暂无会话" else "该筛选条件下没有会话",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                else -> SessionGroupedList(
                    sessions = state.filteredSessions,
                    density = state.density,
                    multiDevice = state.profiles.size > 1,
                    onOpen = onSelectSession,
                )
            }
        }

        // 底部：配置 / 向导 入口
        HorizontalDivider()
        Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onOpenConfig, modifier = Modifier.weight(1f)) {
                Text("⚙ 配置")
            }
            TextButton(onClick = onOpenWizard, modifier = Modifier.weight(1f)) {
                Text("🧭 向导")
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
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
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

@Composable
private fun FilterRow(current: SessionFilter, onSelect: (SessionFilter) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
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
    val vPad = if (density == DensityPreset.COMPACT) 2.dp else 4.dp

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
    val hPad = if (density == DensityPreset.COMPACT) 8.dp else 12.dp
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().padding(horizontal = hPad),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    session.title,
                    style = if (density == DensityPreset.COMPACT) MaterialTheme.typography.bodyMedium
                    else MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    buildString {
                        if (showDevice) append("${item.profile.name} · ")
                        append("${session.turns} 轮")
                        if (session.isCurrent) append(" · 当前")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (session.heldBy == HeldBy.OTHER) {
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = "只读",
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
