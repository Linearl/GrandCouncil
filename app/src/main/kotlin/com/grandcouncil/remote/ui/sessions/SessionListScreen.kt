package com.grandcouncil.remote.ui.sessions

import androidx.compose.foundation.combinedClickable
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
    onNewSessionCreated: (ConnectionProfile) -> Unit = {},
    viewModel: SessionListViewModel = viewModel(
        factory = SessionListViewModelFactory(LocalContext.current.applicationContext),
    ),
) {
    val state by viewModel.uiState.collectAsState()
    var deviceMenuOpen by remember { mutableStateOf(false) }

    // 每次抽屉展开/组合时刷新
    LaunchedEffect(Unit) { viewModel.refresh() }

    Column(Modifier.fillMaxSize()) {
        // 顶部：设备选择器 + 新建会话
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
            onNewSession = { viewModel.newSession { profile -> onNewSessionCreated(profile) } },
        )
        // 搜索框（A2）
        androidx.compose.material3.OutlinedTextField(
            value = state.searchQuery,
            onValueChange = { viewModel.setSearchQuery(it) },
            placeholder = { Text("搜索会话…") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
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
                    favorites = state.favorites,
                    onOpen = onSelectSession,
                    onDelete = { viewModel.deleteSession(it) },
                    onToggleFavorite = { viewModel.toggleFavorite(it) },
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

/** 设备筛选选择器（全部设备 + 各连接；在线状态点；右侧新建按钮） */
@Composable
private fun DeviceSelector(
    profiles: List<ConnectionProfile>,
    selectedId: String?,
    onlineIds: Set<String>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onSelect: (String?) -> Unit,
    onNewSession: () -> Unit,
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
                    modifier = Modifier.padding(horizontal = 8.dp).weight(1f),
                )
                Text("▾", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        // 新建会话按钮（A1：顶栏标题区 New Chat 对齐）
        TextButton(
            onClick = onNewSession,
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 16.dp),
        ) {
            Text("＋ 新建", style = MaterialTheme.typography.labelMedium)
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

/** 会话列表（日期分组；多设备时显示设备标签；长按菜单：删除/收藏） */
@Composable
private fun SessionGroupedList(
    sessions: List<AggregatedSession>,
    density: DensityPreset,
    multiDevice: Boolean,
    favorites: Set<String>,
    onOpen: (AggregatedSession) -> Unit,
    onDelete: (AggregatedSession) -> Unit,
    onToggleFavorite: (AggregatedSession) -> Unit,
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
                    isFavorite = "${item.profile.id}:${item.session.id}" in favorites,
                    modifier = Modifier.padding(vertical = vPad),
                    onClick = { onOpen(item) },
                    onDelete = { onDelete(item) },
                    onToggleFavorite = { onToggleFavorite(item) },
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
    isFavorite: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    val session = item.session
    val hPad = if (density == DensityPreset.COMPACT) 8.dp else 12.dp
    var menuOpen by remember { mutableStateOf(false) }

    Box {
        Card(
            onClick = onClick,
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = hPad)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { menuOpen = true },
                ),
        ) {
            Row(
                Modifier.padding(10.dp),
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isFavorite) {
                        Text("★", color = MaterialTheme.colorScheme.tertiary)
                    }
                    if (session.heldBy == HeldBy.OTHER) {
                        Icon(
                            Icons.Filled.Lock,
                            contentDescription = "只读",
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                }
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(if (isFavorite) "取消收藏" else "收藏") },
                onClick = {
                    menuOpen = false
                    onToggleFavorite()
                },
            )
            DropdownMenuItem(
                text = { Text("删除", color = MaterialTheme.colorScheme.error) },
                onClick = {
                    menuOpen = false
                    onDelete()
                },
            )
        }
    }
}

/** ViewModel 工厂：注入 ConnectionStore/SessionRepository/SessionLabelsStore/AppPreferences */
class SessionListViewModelFactory(private val appContext: android.content.Context) :
    androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        return SessionListViewModel(
            ConnectionStore(appContext),
            SessionRepository(),
            SessionLabelsStore(appContext),
            AppPreferences(appContext),
        ) as T
    }
}
