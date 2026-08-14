package com.grandcouncil.remote.ui.sessions

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.grandcouncil.remote.connection.ConnectionProfile
import com.grandcouncil.remote.connection.ConnectionStore
import com.grandcouncil.remote.model.HeldBy
import com.grandcouncil.remote.repository.SessionRepository
import com.grandcouncil.remote.ui.AppPreferences
import com.grandcouncil.remote.ui.theme.DensityPreset
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

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
        // 搜索框（A2；本地即时显示 + VM 200ms 防抖过滤）
        var localQuery by remember { mutableStateOf(state.searchQuery) }
        androidx.compose.material3.OutlinedTextField(
            value = localQuery,
            onValueChange = {
                localQuery = it
                viewModel.setSearchQuery(it)
            },
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

                state.error != null ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(state.error!!, color = MaterialTheme.colorScheme.error)
                            TextButton(onClick = {
                                viewModel.clearError()
                                viewModel.refresh()
                            }) { Text("重试") }
                        }
                    }

                state.filteredSessions.isEmpty() ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            if (state.aggregated.isEmpty()) "暂无会话" else "该筛选条件下没有会话",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                else -> {
                    // 全部设备 → 按项目分组（组头=设备名）；单设备 → 按日期分组
                    if (state.deviceFilter == null && state.profiles.size > 1) {
                        ProjectGroupedList(
                            groups = state.groupedByProject,
                            density = state.density,
                            onlineIds = state.serveInfoByProfile.keys,
                            favorites = state.favorites,
                            onOpen = onSelectSession,
                            onDelete = { viewModel.deleteSession(it) },
                            onToggleFavorite = { viewModel.toggleFavorite(it) },
                        )
                    } else {
                        SessionGroupedList(
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

/** 按项目分组的会话列表（组头=设备名+在线状态；左滑删除） */
@Composable
private fun ProjectGroupedList(
    groups: List<Pair<ConnectionProfile, List<AggregatedSession>>>,
    density: DensityPreset,
    onlineIds: Set<String>,
    favorites: Set<String>,
    onOpen: (AggregatedSession) -> Unit,
    onDelete: (AggregatedSession) -> Unit,
    onToggleFavorite: (AggregatedSession) -> Unit,
) {
    val vPad = if (density == DensityPreset.COMPACT) 2.dp else 4.dp
    LazyColumn(Modifier.fillMaxSize()) {
        groups.forEach { (profile, sessions) ->
            item(key = "proj-${profile.id}") {
                Text(
                    buildString {
                        append(profile.name)
                        append(if (profile.id in onlineIds) "  ●在线" else "  ○离线")
                        append("（${sessions.size}）")
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (profile.id in onlineIds) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            items(sessions, key = { "${it.profile.id}-${it.session.id}" }) { item ->
                SessionItem(
                    item = item,
                    density = density,
                    showDevice = false,
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

/** 相对时间（今天 HH:mm / 昨天 / N 天前 / 更早日期）——从会话名解析 YYYYMMDD-HHMMSS */
private fun relativeTime(sessionId: String): String? {
    val dt = runCatching {
        java.time.LocalDateTime.parse(
            sessionId.take(15),
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"),
        )
    }.getOrNull() ?: return null
    val now = java.time.LocalDateTime.now()
    val today = LocalDate.now()
    return when {
        dt.toLocalDate() == today -> "今天 ${dt.format(DateTimeFormatter.ofPattern("HH:mm"))}"
        dt.toLocalDate() == today.minusDays(1) -> "昨天 ${dt.format(DateTimeFormatter.ofPattern("HH:mm"))}"
        else -> "${java.time.temporal.ChronoUnit.DAYS.between(dt.toLocalDate(), today)} 天前"
    }
}

/**
 * 长按激活式滑动操作（常规交互，多款 app 同款）：
 * 长按内容 1s（触觉反馈 + 视觉高亮）→ 同一手势继续左滑 → 露出底层操作按钮（收藏/删除）；
 * 点击红色删除按钮才删除；未激活时左滑为普通滚动、点击为打开会话。
 */
@Composable
private fun SwipeRevealItem(
    onClick: () -> Unit,
    onActivate: () -> Unit,
    onDeactivate: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable (close: () -> Unit) -> Unit,
    content: @Composable (activated: Boolean) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val offsetX = remember { Animatable(0f) }
    val revealPx = with(LocalDensity.current) { 132.dp.toPx() }
    var activated by remember { mutableStateOf(false) }
    var revealed by remember { mutableStateOf(false) }
    // 长按 400ms 激活（用户调整：1s 体感太长）
    val longPressMillis = 400L
    // 按钮点击后收起
    val close = {
        scope.launch { offsetX.animateTo(0f) }
        revealed = false
        activated = false
        onDeactivate()
    }

    Box(modifier.clipToBounds()) {
        // 底层：操作按钮（右对齐，横向排列；end padding 对齐卡片右缘，避免未滑动时露出缝隙）
        Box(
            Modifier
                .matchParentSize()
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.CenterEnd,
        ) {
            actions(close)
        }
        // 上层：内容（长按激活 + 左滑；translationX 渲染层变换，随 Animatable 实时更新）
        Box(
            Modifier
                .fillMaxWidth()
                .graphicsLayer { translationX = offsetX.value }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var lastX = down.position.x
                        // 阶段 1：1s 内等待 up（点击）/ 移动（滚动）；超时 = 长按激活
                        // （手指静止时无新事件，须用 withTimeoutOrNull 实现超时，与 detectTapGestures 同机制）
                        // 退出类型：0=点击 1=滚动/消费 2=长按激活
                        val exitKind = withTimeoutOrNull(longPressMillis) {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: return@withTimeoutOrNull 1
                                if (change.changedToUp()) return@withTimeoutOrNull 0
                                if (change.isConsumed) return@withTimeoutOrNull 1
                                if ((change.position - down.position).getDistance() > 12f) return@withTimeoutOrNull 1
                            }
                            1
                        }?.let { it } ?: 2
                        if (exitKind == 1) {
                            // 普通滚动：不消费，交给 LazyColumn
                            return@awaitEachGesture
                        }
                        if (exitKind == 0) {
                            // 点击
                            if (revealed) {
                                scope.launch { offsetX.animateTo(0f) }
                                revealed = false
                                activated = false
                                onDeactivate()
                            } else {
                                onClick()
                            }
                            return@awaitEachGesture
                        }
                        // 长按达成：激活 + 触觉反馈
                        activated = true
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onActivate()
                        // 阶段 2：同一手势继续左滑（不松手）
                        var offset = offsetX.value
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (change.changedToUp()) break
                            val dx = change.position.x - lastX
                            lastX = change.position.x
                            if (dx != 0f && change.position.x < down.position.x) {
                                change.consume()
                                offset = (offset + dx).coerceIn(-revealPx, 0f)
                                scope.launch { offsetX.snapTo(offset) }
                            }
                        }
                        // 松手判定：有滑动（>48px）即吸附露出，否则收起并取消激活
                        if (offset <= -48f) {
                            scope.launch { offsetX.animateTo(-revealPx) }
                            revealed = true
                        } else {
                            scope.launch { offsetX.animateTo(0f) }
                            revealed = false
                            activated = false
                            onDeactivate()
                        }
                    }
                },
        ) { content(activated) }
    }
}

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
    var activated by remember { mutableStateOf(false) }

    Box {
        SwipeRevealItem(
            onClick = onClick,
            onActivate = { activated = true },
            onDeactivate = { activated = false },
            modifier = modifier.fillMaxWidth(),
            actions = { close ->
                Row(
                    Modifier.padding(end = hPad).fillMaxHeight(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 收藏按钮（次要色）
                    IconButton(
                        onClick = {
                            close()
                            onToggleFavorite()
                        },
                        modifier = Modifier.fillMaxHeight().width(48.dp),
                    ) {
                        Icon(
                            Icons.Filled.Star,
                            contentDescription = if (isFavorite) "取消收藏" else "收藏会话",
                            tint = if (isFavorite) MaterialTheme.colorScheme.tertiary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    // 删除按钮（红色）——点击才删除
                    IconButton(
                        onClick = {
                            close()
                            onDelete()
                        },
                        modifier = Modifier.fillMaxHeight().width(56.dp).background(MaterialTheme.colorScheme.error),
                    ) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "删除会话",
                            tint = MaterialTheme.colorScheme.onError,
                        )
                    }
                }
            },
        ) { act ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = hPad),
                colors = CardDefaults.cardColors(
                    containerColor = if (act) {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    },
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
                                relativeTime(session.id)?.let { append("$it · ") }
                                append("${session.turns} 轮")
                                if (session.isCurrent) append(" · 当前")
                                if (act) append(" · 左滑查看操作")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (act) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
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
