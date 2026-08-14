package com.grandcouncil.remote.ui.sessions

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.grandcouncil.remote.agent.AgentAdapterFactory
import com.grandcouncil.remote.agent.Feature
import com.grandcouncil.remote.api.dto.ApprovalEventDto
import com.grandcouncil.remote.connection.ConnectionProfile
import com.grandcouncil.remote.model.HeldBy
import com.grandcouncil.remote.model.RemoteMessage
import com.grandcouncil.remote.model.RemoteSession
import com.grandcouncil.remote.model.Role
import com.grandcouncil.remote.model.ToolCallStatus
import com.grandcouncil.remote.ui.components.MessageContent
import com.grandcouncil.remote.ui.export.SessionExporter
import com.grandcouncil.remote.repository.SessionRepository
import com.grandcouncil.remote.ui.theme.ReasonixColors

/**
 * 会话详情（P0-2 聊天页）：
 * 全屏（底部三栏隐藏），底部输入框（busy 时变 Stop）。
 * 消息流：用户/助手气泡、推理折叠、工具卡片（状态色）、审批卡片、状态条。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailScreen(
    profile: ConnectionProfile,
    session: RemoteSession?,
    modifier: Modifier = Modifier,
    viewModel: SessionDetailViewModel = viewModel(
        factory = SessionDetailViewModelFactory(
            profile = profile,
            session = session,
            appContext = LocalContext.current.applicationContext,
        ),
        key = "detail-${session?.id ?: "draft"}",
    ),
) {
    val state by viewModel.uiState.collectAsState()
    var input by remember { mutableStateOf("") }
    val context = LocalContext.current
    // T5 发送失败恢复输入框文本
    LaunchedEffect(state.restoreInput) {
        state.restoreInput?.let { restored ->
            input = restored
            viewModel.clearRestoreInput()
        }
    }
    val listState = rememberLazyListState()
    // 点击消息区空白收起软键盘
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    // 每次进入（会话切换/组合重建）强制重新加载，避免旧快照/残留流式状态
    LaunchedEffect(session?.id) { viewModel.load() }

    // 新消息/流式变化时自动滚到底（P0-4：仅当用户当前位于底部附近才跟随，
    // 用户上翻查看时不抢滚动；等价于 rikkahub 规格「生成中且用户位于底部 → 持续跟随」）
    LaunchedEffect(state.messages.size, state.streaming?.text?.length, state.streaming?.tools?.size) {
        kotlinx.coroutines.delay(120)
        val count = state.messages.size + if (state.streaming != null) 1 else 0
        if (count <= 0) return@LaunchedEffect
        val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
        if (lastVisible >= count - 2) listState.animateScrollToItem(count - 1)
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(session?.title ?: "新会话", maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                actions = {
                    if (session?.heldBy == HeldBy.OTHER) {
                        Text(
                            "🔒 只读",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 12.dp),
                        )
                    }
                    // 导出对话（md/json，SAF 写文件）；按 A1 能力字典 gate
                    val canExport = profile.let { p ->
                        runCatching { AgentAdapterFactory.create(p).supports(Feature.EXPORT) }.getOrDefault(false)
                    }
                    if (canExport && state.messages.isNotEmpty()) {
                        var exportMenuOpen by remember { mutableStateOf(false) }
                        // 分格式 launcher：正确 mime 避免 DocumentsUI 追加 .txt 后缀
                        fun writeExport(uri: android.net.Uri, content: String) {
                            runCatching {
                                context.contentResolver.openOutputStream(uri)?.use { out ->
                                    out.write(content.toByteArray(Charsets.UTF_8))
                                } ?: throw IllegalStateException("无法打开输出流")
                            }.onSuccess {
                                Toast.makeText(context, "已导出", Toast.LENGTH_SHORT).show()
                            }.onFailure { e ->
                                Toast.makeText(context, "导出失败：${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                        val mdLauncher = rememberLauncherForActivityResult(
                            ActivityResultContracts.CreateDocument("text/markdown"),
                        ) { uri -> uri?.let { writeExport(it, SessionExporter.toMarkdown(session, state.messages)) } }
                        val jsonLauncher = rememberLauncherForActivityResult(
                            ActivityResultContracts.CreateDocument("application/json"),
                        ) { uri -> uri?.let { writeExport(it, SessionExporter.toJson(session, state.messages)) } }
                        Box {
                            IconButton(onClick = { exportMenuOpen = true }) {
                                Icon(
                                    Icons.Filled.MoreVert,
                                    contentDescription = "更多",
                                )
                            }
                            DropdownMenu(
                                expanded = exportMenuOpen,
                                onDismissRequest = { exportMenuOpen = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("导出 Markdown") },
                                    onClick = {
                                        exportMenuOpen = false
                                        mdLauncher.launch("${session?.title?.take(24) ?: "会话"}.md")
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("导出 JSON") },
                                    onClick = {
                                        exportMenuOpen = false
                                        jsonLauncher.launch("${session?.title?.take(24) ?: "会话"}.json")
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
        bottomBar = {
            Column {
                if (state.running) {
                    Text(
                        state.statusText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
                // 审批模式切换移到输入栏（对齐 rikkahub：模式 chip 在输入区）
                ApprovalModeMenu(
                    current = state.approvalMode,
                    onChange = { viewModel.setApprovalMode(it) },
                )
                InputBar(
                    input = input,
                    running = state.running,
                    readOnly = session?.heldBy == HeldBy.OTHER,
                    onInput = { input = it },
                    onSend = {
                        viewModel.send(input)
                        input = ""
                    },
                    onStop = { viewModel.cancel() },
                )
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (session?.heldBy == HeldBy.OTHER) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "该会话被其他设备持有，只读查看；发送消息将接管会话",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
            when {
                state.loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                state.error != null && state.messages.isEmpty() && state.streaming == null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(state.error!!, color = MaterialTheme.colorScheme.error)
                            TextButton(onClick = { viewModel.load() }) { Text("重试") }
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            // 点击非输入区域收起软键盘（clearFocus + hide）
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = {
                                        focusManager.clearFocus()
                                        keyboardController?.hide()
                                    },
                                )
                            },
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 10.dp),
                    ) {
                    // 不用 key：历史消息 id 无稳定来源，key 冲突会静默跳过渲染
                    items(state.messages) { message ->
                        MessageItem(message)
                    }
                    state.streaming?.let { streaming ->
                        if (!streaming.isEmpty) {
                            item(key = "streaming") {
                                StreamingItem(streaming)
                            }
                        }
                    }
                    state.pendingApproval?.let { approval ->
                        item(key = "approval") {
                            ApprovalCard(approval, viewModel)
                        }
                    }
                }
                }
            }
        }
    }
}

/** 底部输入栏：普通态=输入框+发送；busy 态=Stop 键（输入清空时） */
@Composable
private fun InputBar(
    input: String,
    running: Boolean,
    readOnly: Boolean,
    onInput: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = input,
            onValueChange = onInput,
            placeholder = { Text(if (readOnly) "只读会话（发送将接管）" else "回复或输入指令…") },
            enabled = !readOnly,
            modifier = Modifier.weight(1f),
            maxLines = 3,
        )
        if (running && input.isBlank()) {
            IconButton(
                onClick = onStop,
                modifier = Modifier.background(MaterialTheme.colorScheme.error, CircleShape),
            ) {
                Text("■", color = MaterialTheme.colorScheme.onError)
            }
        } else {
            IconButton(
                onClick = onSend,
                enabled = input.isNotBlank() && !readOnly,
                modifier = Modifier.background(MaterialTheme.colorScheme.primary, CircleShape),
            ) {
                Text("↑", color = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
}

@Composable
private fun MessageItem(message: RemoteMessage) {
    when (message.role) {
        Role.NOTICE -> Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        ) {
            Text(
                message.content,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 8,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(10.dp),
            )
        }

        Role.TOOL -> ToolCard(
            id = message.toolCalls.firstOrNull()?.id ?: "",
            name = message.toolCalls.firstOrNull()?.name ?: "工具",
            args = message.toolCalls.firstOrNull()?.arguments ?: "",
            output = message.content,
            error = "",
            durationMs = 0,
            status = ToolCallStatus.DONE,
        )

        else -> {
            val isUser = message.role == Role.USER
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
            ) {
                Surface(
                    color = if (isUser) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.widthIn(max = 320.dp),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        if (!isUser && !message.reasoning.isNullOrBlank()) {
                            Text(
                                "🤔 " + message.reasoning.trim().lineSequence().first().take(80),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isUser) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            HorizontalDivider(Modifier.padding(vertical = 4.dp))
                        }
                        if (!isUser) {
                            MessageContent(
                                content = message.content.ifBlank { "…" },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        } else {
                            Text(
                                message.content.ifBlank { "…" },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimary,
                                maxLines = 15,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (message.toolCalls.isNotEmpty()) {
                            message.toolCalls.forEach { tool ->
                                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                                Text(
                                    "🔧 ${tool.name}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.tertiary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 流式中的 assistant 消息（推理折叠 + 文本 + 工具卡片） */
@Composable
private fun StreamingItem(streaming: StreamingMessage) {
    var reasoningExpanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (streaming.reasoning.isNotEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(10.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "💡 推理过程",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        TextButton(onClick = { reasoningExpanded = !reasoningExpanded }) {
                            Text(if (reasoningExpanded) "收起" else "展开")
                        }
                    }
                    if (reasoningExpanded) {
                        Text(
                            streaming.reasoning,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        streaming.tools.forEach { tool ->
            ToolCard(
                id = tool.id,
                name = tool.name,
                args = tool.args,
                output = tool.output,
                error = tool.error,
                durationMs = tool.durationMs,
                status = tool.status,
            )
        }
        if (streaming.text.isNotEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.widthIn(max = 340.dp),
            ) {
                MessageContent(
                    content = streaming.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(12.dp),
                    textMaxLines = -1,
                )
            }
        }
    }
}

/** 工具卡片（状态色：运行=琥珀 spinner / 完成=绿 / 错误=红） */
@Composable
private fun ToolCard(
    id: String,
    name: String,
    args: String,
    output: String,
    error: String,
    durationMs: Long,
    status: ToolCallStatus,
) {
    var expanded by remember { mutableStateOf(false) }
    val statusColor = when {
        error.isNotEmpty() -> ReasonixColors.err
        status == ToolCallStatus.RUNNING -> ReasonixColors.warn
        else -> ReasonixColors.success
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(10.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (status == ToolCallStatus.RUNNING) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(end = 6.dp),
                        strokeWidth = 2.dp,
                        color = statusColor,
                    )
                } else {
                    Text("✓", color = statusColor, modifier = Modifier.padding(end = 6.dp))
                }
                Text(
                    name,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f),
                )
                if (durationMs > 0) {
                    Text(
                        "${durationMs / 1000.0}s",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "收起" else "详情")
                }
            }
            if (expanded) {
                Text(
                    args,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
                if (output.isNotEmpty()) {
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    Text(
                        output.take(500),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 8,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (error.isNotEmpty()) {
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    Text(
                        "✗ $error",
                        style = MaterialTheme.typography.bodySmall,
                        color = ReasonixColors.err,
                    )
                }
            }
        }
    }
}

/** 敏感工具集合（serve RequiresFreshHumanApprovalTool：YOLO 模式也强制显式审批） */
private val SENSITIVE_TOOLS = setOf(
    "memory_remember", "memory_forget", "plan", "sandbox_escape", "managed_config_write",
)

/** 审批卡片（紧凑版；Ask 与 YOLO 敏感工具都会触发） */
@Composable
private fun ApprovalCard(approval: ApprovalEventDto, viewModel: SessionDetailViewModel) {
    val sensitive = approval.tool in SENSITIVE_TOOLS
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (sensitive) "⚠️" else "🛡",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(end = 6.dp),
                )
                Text(
                    buildString {
                        append(approval.tool)
                        if (approval.subject.isNotEmpty()) append(" — ${approval.subject}")
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (sensitive) ReasonixColors.err else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            if (sensitive) {
                Text(
                    "敏感操作（记忆/计划/沙箱），即使 YOLO 模式也需显式审批",
                    style = MaterialTheme.typography.labelSmall,
                    color = ReasonixColors.err,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CompactApprovalButton("✕ 拒绝", ReasonixColors.err) {
                    viewModel.approve(approval, allow = false)
                }
                CompactApprovalButton("★ 总是") {
                    viewModel.approve(approval, allow = true, persist = true)
                }
                CompactApprovalButton("✓ 允许", MaterialTheme.colorScheme.primary) {
                    viewModel.approve(approval, allow = true)
                }
            }
        }
    }
}

/** 紧凑审批按钮（小圆角、小高度，行内右对齐） */
@Composable
private fun CompactApprovalButton(
    label: String,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 0.dp),
        modifier = Modifier.padding(0.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}

/** 审批模式切换菜单（询问/自动/YOLO，对标 PC 端三档） */
@Composable
private fun ApprovalModeMenu(
    current: String?,
    onChange: (ApprovalMode) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val currentMode = ApprovalMode.entries.firstOrNull { it.wire == current } ?: ApprovalMode.ASK
    Box {
        TextButton(onClick = { expanded = true }) {
            Text(
                "⚡ ${currentMode.label}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ApprovalMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = {
                        Text(
                            buildString {
                                append(mode.label)
                                append(
                                    when (mode) {
                                        ApprovalMode.ASK -> " · 工具调用逐个征求允许"
                                        ApprovalMode.AUTO -> " · 自动批准工具"
                                        ApprovalMode.YOLO -> " · 全速放行不询问"
                                    },
                                )
                            },
                        )
                    },
                    onClick = {
                        expanded = false
                        onChange(mode)
                    },
                )
            }
        }
    }
}

/** ViewModel 工厂（携带 profile/session 参数） */
class SessionDetailViewModelFactory(
    private val profile: ConnectionProfile,
    private val session: RemoteSession?,
    private val appContext: android.content.Context,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        SessionDetailViewModel(profile, session, SessionRepository()) as T
}
