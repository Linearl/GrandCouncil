package com.grandcouncil.remote.ui.sessions

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.grandcouncil.remote.agent.AgentAdapterFactory
import com.grandcouncil.remote.agent.Feature
import com.grandcouncil.remote.api.dto.ApprovalEventDto
import com.grandcouncil.remote.connection.ConnectionProfile
import com.grandcouncil.remote.ui.AppPreferences
import com.grandcouncil.remote.ui.theme.DensityPreset
import com.grandcouncil.remote.model.HeldBy
import com.grandcouncil.remote.model.RemoteMessage
import com.grandcouncil.remote.model.RemoteSession
import com.grandcouncil.remote.model.Role
import com.grandcouncil.remote.model.ToolCall
import com.grandcouncil.remote.model.ToolCallStatus
import com.grandcouncil.remote.ui.components.MessageContent
import com.grandcouncil.remote.ui.components.ProcessCard
import com.grandcouncil.remote.ui.components.ProcessStep
import com.grandcouncil.remote.ui.components.ReasoningProcessStep
import com.grandcouncil.remote.ui.components.ToolProcessStep
import com.grandcouncil.remote.ui.components.MessageTts
import com.grandcouncil.remote.ui.export.SessionExporter
import kotlinx.coroutines.launch
import com.grandcouncil.remote.repository.SessionRepository
import com.grandcouncil.remote.ui.theme.ReasonixColors
import kotlin.math.roundToInt

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
    onNewChat: () -> Unit = {},
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
    // 思考档位选择底部弹层（文档 §2.4：上拉菜单，滑杆+刻度行）
    var effortSheetOpen by remember { mutableStateOf(false) }
    // 用户输入预览模式（右上角 📋，对标 rikkahub Chat Options）
    var previewMode by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // 密度联动（问题二：紧凑模式下工具区更紧凑）
    val prefs = remember { AppPreferences(context) }
    var density by remember { mutableStateOf(DensityPreset.COMFORTABLE) }
    LaunchedEffect(Unit) { prefs.density.collect { density = it } }
    // T5 发送失败恢复输入框文本
    LaunchedEffect(state.restoreInput) {
        state.restoreInput?.let { restored ->
            input = restored
            viewModel.clearRestoreInput()
        }
    }
    val listState = rememberLazyListState()
    // 历史消息按 turn 聚合：同一轮多工具过程合并为 ProcessCard（避免逐条平铺）
    val grouped = remember(state.messages) { groupMessages(state.messages) }
    // 点击消息区空白收起软键盘
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    // 每次进入（会话切换/组合重建）强制重新加载，避免旧快照/残留流式状态
    LaunchedEffect(session?.id) { viewModel.load() }

    // 释放所有权反馈：点击「释放所有权」后弹 Toast 告知结果（成功/失败），消费后清空
    LaunchedEffect(state.releaseMessage) {
        state.releaseMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            viewModel.clearReleaseMessage()
        }
    }

    // 工作中滚动展示（聊天滚动跟随修复指导）：snapshotFlow 持续监听可见项——
    // 推理/工具/文本任何更新都触发判定；生成中 + 用户未滚动 + 底部附近 +
    // 距上次用户滚动 > 1.5s 才瞬时 scrollToItem；生成结束不强制拉回
    val autoScrollEnabled by prefs.autoScroll.collectAsState(initial = true)
    rememberAutoScrollFollower(
        listState = listState,
        loading = state.running,
        enabled = autoScrollEnabled,
    )
    // 自己发送消息后滚到底（用户主动行为，独立于跟随开关）
    LaunchedEffect(state.messages.size) {
        if (state.messages.lastOrNull()?.role == Role.USER) {
            val count = grouped.size + if (state.streaming != null) 1 else 0
            if (count > 0) listState.scrollToItem(count - 1)
        }
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
                        // 只读标识（接管入口在页面中心，避免顶部与中心重复）
                        Text(
                            "🔒 只读",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    } else if (session != null && !state.ownershipReleased) {
                        // GC（serve 侧）持有或 FREE：提供显式释放入口
                        TextButton(
                            onClick = { viewModel.releaseOwnership() },
                            enabled = !state.releasingOwnership,
                        ) {
                            Text(if (state.releasingOwnership) "释放中…" else "释放所有权")
                        }
                    }
                    // 右上角「新建对话」（对标 rikkahub New Message）：进入空白新会话草稿
                    if (session != null && !state.newSessionBusy) {
                        IconButton(
                            onClick = { viewModel.newSession { onNewChat() } },
                            enabled = !state.running,
                        ) {
                            Text("💬＋", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    // 右上角「查看用户输入」：切换用户输入预览（搜索/跳转，对标 rikkahub Chat Options）
                    val hasUserMessages = state.messages.any { it.role == Role.USER }
                    if (hasUserMessages) {
                        IconButton(onClick = { previewMode = !previewMode }) {
                            Icon(
                                if (previewMode) Icons.Filled.Close else Icons.Filled.List,
                                contentDescription = if (previewMode) "退出预览" else "查看用户输入",
                            )
                        }
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
                // T7 上下文用量条（used/window token，进度条 + 颜色随占比）
                val used = state.contextUsed
                val window = state.contextWindow
                if (used != null && window != null && window > 0) {
                    val pct = (used * 100 / window).coerceIn(0, 100)
                    val barColor = when {
                        pct > 85 -> MaterialTheme.colorScheme.error
                        pct > 60 -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.primary
                    }
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(horizontal = 16.dp, vertical = 2.dp),
                    ) {
                        Text(
                            "上下文 ${pct}%（${used / 1000f}k / ${window / 1000f}k tokens）",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        LinearProgressIndicator(
                            progress = { pct / 100f },
                            color = barColor,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 2.dp, bottom = 3.dp)
                                .height(3.dp),
                        )
                    }
                }
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
                // Suggestion 胶囊（本地规则；非生成中显示，点击直接发送）
                val suggestions = remember(state.messages, state.streaming) {
                    if (state.streaming != null) emptyList()
                    else localSuggestions(state.messages)
                }
                if (suggestions.isNotEmpty()) {
                    SuggestionRow(
                        suggestions = suggestions,
                        onPick = { s ->
                            viewModel.send(s)
                            input = ""
                        },
                    )
                }
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
                    webSearchEnabled = state.webSearchEnabled,
                    onToggleWebSearch = { viewModel.setWebSearch(!state.webSearchEnabled) },
                    effortLabel = effortLabel(state.effortLevel),
                    effortUnsupported = state.effortUnsupported,
                    onOpenEffort = { effortSheetOpen = true },
                    approvalMode = state.approvalMode,
                    onApprovalModeChange = { viewModel.setApprovalMode(it) },
                    voiceSlot = {
                        VoiceInputButton(
                            onResult = { text -> input = if (input.isBlank()) text else "$input $text" },
                            readOnly = session?.heldBy == HeldBy.OTHER,
                        )
                    },
                )
                if (effortSheetOpen) {
                    EffortSheet(
                        current = state.effortLevel,
                        onSelect = { level ->
                            viewModel.setEffort(level)
                            effortSheetOpen = false
                        },
                        onDismiss = { effortSheetOpen = false },
                    )
                }
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
                        "该会话正在桌面端使用，当前只读查看。点击页面中心「获取所有权」按钮可接管（不会自动获取）。",
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
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        // 用户输入预览模式（右上角 📋）：搜索 + 列表 + 跳转
                        if (previewMode) {
                            UserInputPreview(
                                messages = state.messages,
                                grouped = grouped,
                                onJumpTo = { groupIndex ->
                                    scope.launch { listState.scrollToItem(groupIndex) }
                                    previewMode = false
                                },
                                onClose = { previewMode = false },
                            )
                            return@Box
                        }
                        // 历史消息按 turn 聚合（grouped 在顶部计算，供滚动逻辑复用）
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
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
                            grouped.forEach { group ->
                                when (group) {
                                    is MessageGroup.Single -> item(key = "m-${group.message.id}") {
                                        MessageItem(
                                            group.message,
                                            // 生成中不显示操作行（避免按钮跳动）
                                            showActions = state.streaming == null,
                                        )
                                    }
                                    is MessageGroup.Process -> item(key = group.key) {
                                        ProcessHistoryCard(group)
                                    }
                                }
                            }
                        state.streaming?.let { streaming ->
                            if (!streaming.isEmpty) {
                                item(key = "streaming") {
                                    StreamingItem(streaming, density)
                                }
                            }
                        }
                        state.pendingApproval?.let { approval ->
                            item(key = "approval") {
                                ApprovalCard(approval, viewModel)
                            }
                        }
                    }
                        // T6 回到底部：离开底部（约 3 条）时右下角悬浮 ↓
                        val totalCount = grouped.size + if (state.streaming != null) 1 else 0
                        val awayFromBottom by remember {
                            derivedStateOf {
                                val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                                totalCount > 1 && last < totalCount - 3
                            }
                        }
                        if (awayFromBottom) {
                            androidx.compose.material3.FloatingActionButton(
                                onClick = {
                                    scope.launch { listState.animateScrollToItem((totalCount - 1).coerceAtLeast(0)) }
                                },
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(14.dp),
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ) {
                                Text("↓")
                            }
                        }
                        // 中心所有权按钮：桌面使用中→获取所有权；GC 已持有→释放所有权。
                        // 进会话默认只读（不自动获取）；发送消息才自动获取。释放后隐藏。
                        if (!state.ownershipReleased && session != null) {
                            val held = session.heldBy
                            val isHeldByMe = held == HeldBy.ME
                            val isOther = held == HeldBy.OTHER
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Card(
                                    onClick = {
                                        if (isHeldByMe) viewModel.releaseOwnership()
                                        else viewModel.takeover()
                                    },
                                    modifier = Modifier.padding(24.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    ),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                                ) {
                                    Column(
                                        Modifier.padding(horizontal = 24.dp, vertical = 18.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        Text(
                                            if (isOther) "🔒 只读查看（桌面端使用中）"
                                            else if (isHeldByMe) "✓ 已获得所有权"
                                            else "🔓 空闲会话",
                                            style = MaterialTheme.typography.titleSmall,
                                        )
                                        Text(
                                            if (isOther) "该会话正在桌面端使用，当前只读；点击按钮获取所有权"
                                            else if (isHeldByMe) "桌面端已释放；点击可重新释放给桌面端"
                                            else "该会话空闲；点击获取所有权即可使用",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(top = 4.dp),
                                        )
                                        Spacer(Modifier.height(12.dp))
                                        Button(
                                            onClick = {
                                                if (isHeldByMe) viewModel.releaseOwnership()
                                                else viewModel.takeover()
                                            },
                                            enabled = !state.takingOver && !state.releasingOwnership,
                                        ) {
                                            Text(
                                                when {
                                                    state.takingOver -> "获取中…"
                                                    state.releasingOwnership -> "释放中…"
                                                    isHeldByMe -> "释放所有权"
                                                    else -> "获取所有权"
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * T8 语音听写按钮：SpeechRecognizer 识别结果回调 onResult（听写→编辑→发送两级）。
 * 点击开始/结束听写；RECORD_AUDIO 未授权时先请求。
 */
@Composable
private fun VoiceInputButton(
    onResult: (String) -> Unit,
    readOnly: Boolean,
) {
    val context = LocalContext.current
    var listening by remember { mutableStateOf(false) }

    val recognizer = remember {
        android.speech.SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : android.speech.RecognitionListener {
                override fun onReadyForSpeech(params: android.os.Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { listening = false }
                override fun onError(error: Int) { listening = false }
                override fun onResults(results: android.os.Bundle?) {
                    listening = false
                    val text = results?.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                    if (!text.isNullOrBlank()) onResult(text)
                }
                override fun onPartialResults(partialResults: android.os.Bundle?) {}
                override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
            })
        }
    }

    fun start() {
        listening = true
        runCatching {
            recognizer.startListening(
                android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(
                        android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                    )
                    putExtra(android.speech.RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                },
            )
        }.onFailure { listening = false }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) start() }
    DisposableEffect(Unit) { onDispose { recognizer.destroy() } }

    IconButton(
        onClick = {
            if (readOnly) return@IconButton
            if (listening) {
                recognizer.cancel()
                listening = false
            } else if (
                androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                permissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
            } else {
                start()
            }
        },
        modifier = Modifier.background(
            if (listening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.surfaceVariant,
            CircleShape,
        ),
    ) {
        Text(
            if (listening) "⏹" else "🎤",
            color = if (listening) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** 底部输入栏：普通态=输入框+发送；busy 态=Stop 键（输入清空时）；语音听写按钮插槽。
 *  质感：半透明圆角容器 + 1dp 描边 + 阴影；键盘弹出时底部两角变直角贴合 IME。 */
@OptIn(ExperimentalLayoutApi::class)
/**
 * 底部输入区（两行，用户拍板）：
 * 工具行 = 🌐 联网 + 💭 思考 + ⚡ 审批模式（权限控制）+ 🎤 语音；
 * 输入行 = 输入框 + 发送/Stop 键独立占一行。
 */
@Composable
private fun InputBar(
    input: String,
    running: Boolean,
    readOnly: Boolean,
    onInput: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    webSearchEnabled: Boolean,
    onToggleWebSearch: () -> Unit,
    effortLabel: String,
    effortUnsupported: Boolean,
    onOpenEffort: () -> Unit,
    approvalMode: String?,
    onApprovalModeChange: (ApprovalMode) -> Unit,
    voiceSlot: @Composable () -> Unit = {},
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val imeVisible = WindowInsets.isImeVisible
    // IME 弹出时底部两角变直角（贴合键盘）；否则大圆角悬浮容器
    val containerShape = if (imeVisible) {
        RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    } else {
        RoundedCornerShape(20.dp)
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = containerShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        shadowElevation = 6.dp,
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            // ---- 工具行：联网 / 思考档位 / 审批模式 / 语音 ----
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // 联网开关（文档 §2.2）：只留图标，长按提示状态。
                // 配色参考 rikkahub：开启 primary 前景 + primaryContainer 底色，关闭 onSurface，
                // animateColorAsState 平滑渐变（不用呼吸动画，符合 rikkahub 惯例）。
                val webColor by animateColorAsState(
                    targetValue = if (webSearchEnabled) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
                    animationSpec = tween(200),
                    label = "webColor",
                )
                val webBg by animateColorAsState(
                    targetValue = if (webSearchEnabled) MaterialTheme.colorScheme.primaryContainer
                    else Color.Transparent,
                    animationSpec = tween(200),
                    label = "webBg",
                )
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .background(webBg, CircleShape)
                        .pointerInput(webSearchEnabled, readOnly) {
                            detectTapGestures(
                                onTap = { if (!readOnly) onToggleWebSearch() },
                                onLongPress = {
                                    Toast.makeText(
                                        context,
                                        if (webSearchEnabled) "联网已开启" else "联网已关闭（长按无效仅提示）",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                },
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "🌐",
                        style = MaterialTheme.typography.bodySmall,
                        color = webColor,
                        modifier = Modifier.alpha(if (webSearchEnabled) 1f else 0.55f),
                    )
                }
                // 思考档位（文档 §2.4）：只留图标，长按提示当前档位；服务不支持时置灰
                Box(
                    modifier = Modifier
                        .height(30.dp)
                        .widthIn(min = 30.dp)
                        .background(
                            when {
                                effortUnsupported -> MaterialTheme.colorScheme.surfaceVariant
                                effortLabel != "自动" -> MaterialTheme.colorScheme.tertiaryContainer
                                else -> Color.Transparent
                            },
                            CircleShape,
                        )
                        .pointerInput(effortLabel, effortUnsupported, readOnly) {
                            detectTapGestures(
                                onTap = { if (!readOnly && !effortUnsupported) onOpenEffort() },
                                onLongPress = {
                                    Toast.makeText(
                                        context,
                                        if (effortUnsupported) "此服务不支持思考档位" else "思考档位：$effortLabel（轻/高/超重）",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                },
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "💭",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.alpha(if (effortUnsupported) 0.4f else 1f),
                    )
                }
                // 审批模式（权限控制）：⚡ 只留图标，长按提示当前档位，点击弹菜单
                val currentMode = ApprovalMode.entries.firstOrNull { it.wire == approvalMode } ?: ApprovalMode.ASK
                var modeMenu by remember { mutableStateOf(false) }
                Box {
                    Box(
                        modifier = Modifier
                            .height(30.dp)
                            .widthIn(min = 30.dp)
                            .background(
                                if (currentMode == ApprovalMode.YOLO) {
                                    MaterialTheme.colorScheme.errorContainer
                                } else if (currentMode == ApprovalMode.AUTO) {
                                    MaterialTheme.colorScheme.secondaryContainer
                                } else {
                                    Color.Transparent
                                },
                                CircleShape,
                            )
                            .pointerInput(currentMode, readOnly) {
                                detectTapGestures(
                                    onTap = { if (!readOnly) modeMenu = true },
                                    onLongPress = {
                                        Toast.makeText(
                                            context,
                                            "审批模式：${currentMode.label}",
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    },
                                )
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "⚡",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.alpha(if (readOnly) 0.4f else 1f),
                        )
                    }
                    DropdownMenu(expanded = modeMenu, onDismissRequest = { modeMenu = false }) {
                        ApprovalMode.entries.forEach { mode ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        buildString {
                                            append(mode.label)
                                            append(
                                                when (mode) {
                                                    ApprovalMode.ASK -> " · 逐个征求允许"
                                                    ApprovalMode.AUTO -> " · 自动批准工具"
                                                    ApprovalMode.YOLO -> " · 全速放行不询问"
                                                },
                                            )
                                        },
                                    )
                                },
                                onClick = {
                                    onApprovalModeChange(mode)
                                    modeMenu = false
                                },
                            )
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                // 语音听写（工具行右侧）
                voiceSlot()
            }
            // ---- 输入行：输入框 + 发送/Stop ----
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = onInput,
                    placeholder = {
                        Text(
                            when {
                                readOnly -> "只读会话（发送将接管）"
                                webSearchEnabled -> "🌐 已开启联网 · 回复或输入指令…"
                                else -> "回复或输入指令…"
                            },
                        )
                    },
                    enabled = !readOnly,
                    modifier = Modifier.weight(1f),
                    maxLines = 3,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        disabledBorderColor = Color.Transparent,
                    ),
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
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onSend()
                        },
                        enabled = input.isNotBlank() && !readOnly,
                        modifier = Modifier.background(MaterialTheme.colorScheme.primary, CircleShape),
                    ) {
                        Text("↑", color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageItem(
    message: RemoteMessage,
    showActions: Boolean = false,
    context: Context = LocalContext.current,
    prefs: AppPreferences = remember { AppPreferences(context) },
) {
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
            // 头像分列：assistant 左（🤖 圆底）、user 右（🧑）；NOTICE/TOOL 无头像
            val avatar = @Composable {
                Box(
                    Modifier
                        .size(30.dp)
                        .background(
                            if (isUser) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            else MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f),
                            CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (isUser) "🧑" else "🤖",
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
            }
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
            ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
                verticalAlignment = Alignment.Bottom,
            ) {
                if (!isUser) {
                    avatar()
                    Spacer(Modifier.width(6.dp))
                }
                Surface(
                    color = if (isUser) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.widthIn(max = 320.dp),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        if (!isUser && !message.reasoning.isNullOrBlank()) {
                            // 推理摘要：默认首行 80 字，点击展开完整推理（复用 StreamingItem 交互）
                            var reasonExpanded by remember { mutableStateOf(false) }
                            Text(
                                buildString {
                                    append("🤔 ")
                                    if (reasonExpanded) {
                                        append(message.reasoning.trim())
                                    } else {
                                        append(message.reasoning.trim().lineSequence().first().take(80))
                                        if (message.reasoning.trim().length > 80) append("…")
                                    }
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isUser) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = if (reasonExpanded) Int.MAX_VALUE else 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .clickable { reasonExpanded = !reasonExpanded }
                                    .padding(vertical = 2.dp),
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
                            HorizontalDivider(Modifier.padding(vertical = 4.dp))
                            // 历史消息工具区：默认折叠为尾部 2 步 + 控制条（对标 ChainOfThought）
                            ProcessCard(
                                steps = message.toolCalls.map {
                                    ToolProcessStep(
                                        id = it.id,
                                        name = it.name,
                                        args = it.arguments,
                                        output = message.content,
                                        error = "",
                                        durationMs = 0,
                                        status = ToolCallStatus.DONE,
                                    )
                                },
                                stepContent = { step ->
                                    Text(
                                        "🔧 ${step.label}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.tertiary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                            )
                        }
                    }
                }
                if (isUser) {
                    Spacer(Modifier.width(6.dp))
                    avatar()
                }
            }
            // 消息操作行（assistant 消息下方：复制/朗读/收藏/更多；生成中不显示避免跳动）
            if (!isUser && showActions) {
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
                ) {
                    MessageActionRow(
                        message = message,
                        context = context,
                        prefs = prefs,
                    )
                }
            }
            }
        }
    }
}

/** 流式中的 assistant 消息（过程默认折叠 + 文本；推理/工具合并进 ProcessCard） */
@Composable
private fun StreamingItem(streaming: StreamingMessage, density: DensityPreset = DensityPreset.COMFORTABLE) {
    // 过程步骤：推理（一步）+ 工具（每工具一步），合并进 ProcessCard 默认折叠
    val processSteps = remember(streaming) {
        buildList {
            if (streaming.reasoning.isNotEmpty()) add(ReasoningProcessStep(streaming.reasoning))
            streaming.tools.forEach { t ->
                add(ToolProcessStep(t.id, t.name, t.args, t.output, t.error, t.durationMs, t.status))
            }
        }
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (processSteps.isNotEmpty()) {
            ProcessCard(
                steps = processSteps,
                stepContent = { step ->
                    when (step) {
                        is ReasoningProcessStep -> ReasoningStepRow(step)
                        is ToolProcessStep -> ToolStepRow(step, density)
                    }
                },
            )
        }
        if (streaming.text.isNotEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.widthIn(max = 340.dp),
            ) {
                // 流式光标（文档 §1.2）：生成中文本末尾 ▍ 闪烁（500ms）
                val cursorAlpha = rememberInfiniteTransition(label = "cursor").animateFloat(
                    initialValue = 1f,
                    targetValue = 0f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(500),
                        repeatMode = RepeatMode.Reverse,
                    ),
                    label = "cursorAlpha",
                )
                Row(Modifier.padding(12.dp)) {
                    MessageContent(
                        content = streaming.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Text(
                        "▍",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = cursorAlpha.value),
                    )
                }
            }
        } else if (streaming.isEmpty) {
            // 思考中占位（文档 §1.2）：推理/工具阶段尚无文本时，三点动画提示"正在思考"
            ThinkingDots()
        }
    }
}

/** 思考中三点动画（文档 §1.2 加载指示，等效 rikkahub DotLoading） */
@Composable
private fun ThinkingDots() {
    val transition = rememberInfiniteTransition(label = "thinking")
    val dot1 = transition.animateFloat(0.3f, 1f, infiniteRepeatable(tween(400), RepeatMode.Reverse), label = "d1")
    val dot2 = transition.animateFloat(0.3f, 1f, infiniteRepeatable(tween(400, delayMillis = 150), RepeatMode.Reverse), label = "d2")
    val dot3 = transition.animateFloat(0.3f, 1f, infiniteRepeatable(tween(400, delayMillis = 300), RepeatMode.Reverse), label = "d3")
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.widthIn(max = 340.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("思考中", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("●", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = dot1.value))
            Text("●", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = dot2.value))
            Text("●", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = dot3.value))
        }
    }
}

/** 过程单步：推理行（标题 + 点击展开/收起，限高内滚） */
@Composable
private fun ReasoningStepRow(step: ReasoningProcessStep) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "💡 推理过程",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        Text(
            if (expanded) "▴" else "▾",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    if (expanded) {
        Text(
            step.text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .heightIn(max = 240.dp)
                .verticalScroll(rememberScrollState())
                .padding(top = 2.dp, bottom = 4.dp),
        )
    }
}

/** 过程单步：工具行（状态点 + 名称 + 时长 + ▾ 详情；展开限高内滚） */
@Composable
private fun ToolStepRow(step: ToolProcessStep, density: DensityPreset = DensityPreset.COMFORTABLE) {
    var expanded by remember { mutableStateOf(false) }
    val compact = density == DensityPreset.COMPACT
    val statusColor = when {
        step.isError -> ReasonixColors.err
        step.isRunning -> ReasonixColors.warn
        else -> ReasonixColors.success
    }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (step.isRunning) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(end = 6.dp),
                    strokeWidth = 2.dp,
                    color = statusColor,
                )
            } else {
                Text("✓", color = statusColor, modifier = Modifier.padding(end = 6.dp))
            }
            Text(
                step.label,
                style = MaterialTheme.typography.labelSmall,
                color = if (step.isError) ReasonixColors.err else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (step.durationMs > 0 && !compact) {
                Text(
                    "${step.durationMs / 1000.0}s",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 4.dp),
                )
            }
            Text(
                if (expanded) "▴ 收起" else "▾ 详情",
                style = MaterialTheme.typography.labelSmall,
                color = if (step.isError) ReasonixColors.err else MaterialTheme.colorScheme.primary,
            )
        }
        if (expanded) {
            // 展开限高 200dp + 内部滚动，避免长输出撑屏
            Column(
                Modifier
                    .heightIn(max = 200.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 4.dp),
            ) {
                if (step.args.isNotBlank()) {
                    Text(
                        step.args,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (step.output.isNotEmpty()) {
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    Text(
                        step.output.take(500),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 8,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (step.isError) {
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    Text(
                        "✗ ${step.error}",
                        style = MaterialTheme.typography.bodySmall,
                        color = ReasonixColors.err,
                    )
                }
            }
        }
    }
}

/** 工具卡片（紧凑版：labelSmall 工具名 + 图标折叠 + 限高内滚；状态色：运行=琥珀/完成=绿/错误=红） */
@Composable
private fun ToolCard(
    id: String,
    name: String,
    args: String,
    output: String,
    error: String,
    durationMs: Long,
    status: ToolCallStatus,
    density: DensityPreset = DensityPreset.COMFORTABLE,
) {
    var expanded by remember { mutableStateOf(false) }
    val compact = density == DensityPreset.COMPACT
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
        Column(Modifier.padding(horizontal = 8.dp, vertical = if (compact) 2.dp else 4.dp)) {
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
                    style = MaterialTheme.typography.labelSmall,
                    color = if (error.isNotEmpty()) ReasonixColors.err else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (durationMs > 0 && !compact) {
                    Text(
                        "${durationMs / 1000.0}s",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 4.dp),
                    )
                }
                Text(
                    if (expanded) "▴ 收起" else "▾ 详情",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (error.isNotEmpty()) ReasonixColors.err else MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable { expanded = !expanded }
                        .padding(4.dp),
                )
            }
            if (expanded) {
                // 展开限高 200dp + 内部滚动，避免长输出撑屏
                Column(
                    Modifier
                        .heightIn(max = 200.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    if (args.isNotBlank()) {
                        Text(
                            args,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (output.isNotEmpty()) {
                        HorizontalDivider(Modifier.padding(vertical = 4.dp))
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
                        HorizontalDivider(Modifier.padding(vertical = 4.dp))
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
                CompactApprovalButton("✕ 拒绝", ReasonixColors.err, haptic = HapticFeedbackType.LongPress) {
                    viewModel.approve(approval, allow = false)
                }
                CompactApprovalButton("★ 总是", haptic = HapticFeedbackType.Confirm) {
                    viewModel.approve(approval, allow = true, persist = true)
                }
                CompactApprovalButton("✓ 允许", MaterialTheme.colorScheme.primary, haptic = HapticFeedbackType.Confirm) {
                    viewModel.approve(approval, allow = true)
                }
            }
        }
    }
}

/** 紧凑审批按钮（小圆角、小高度，行内右对齐；可带触觉反馈） */
@Composable
private fun CompactApprovalButton(
    label: String,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
    haptic: HapticFeedbackType? = null,
    onClick: () -> Unit,
) {
    val feedback = LocalHapticFeedback.current
    TextButton(
        onClick = {
            haptic?.let { feedback.performHapticFeedback(it) }
            onClick()
        },
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

/** ViewModel 工厂（携带 profile/session 参数） */
class SessionDetailViewModelFactory(
    private val profile: ConnectionProfile,
    private val session: RemoteSession?,
    private val appContext: android.content.Context,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        SessionDetailViewModel(
            profile,
            session,
            SessionRepository(),
            AppPreferences(appContext),
        ) as T
}

// ========== 历史消息按 turn 聚合（多工具过程折叠） ==========

/** 渲染分组：单条消息 or 一个工具过程段（多步工具调用 + 最终文本） */
private sealed interface MessageGroup {
    data class Single(val message: RemoteMessage) : MessageGroup

    data class Process(
        val steps: List<ProcessStep>,
        val finalText: String,
        val key: String,
    ) : MessageGroup
}

/**
 * 把历史消息按"工具过程段"聚合：
 * - assistant(含 toolCalls) + 后续 tool 结果 + assistant(最终文本) → Process 组
 * - 其余（user / 独立 assistant / notice）→ Single
 * serve 历史中同一轮的多工具调用是逐条 assistant 消息（每条 1 个 toolCall），
 * 必须跨消息聚合才能让 ProcessCard 把多步骤折叠成摘要。
 */
private fun groupMessages(messages: List<RemoteMessage>): List<MessageGroup> {
    val result = mutableListOf<MessageGroup>()
    var i = 0
    while (i < messages.size) {
        val m = messages[i]
        if (m.role == Role.ASSISTANT && m.toolCalls.isNotEmpty()) {
            val stepCalls = mutableListOf<ToolCall>()
            val reasoningTexts = mutableListOf<String>()
            val outputs = mutableMapOf<String, String>()
            var finalText = ""
            var j = i
            var sawTool = false
            while (j < messages.size) {
                val cur = messages[j]
                when {
                    cur.role == Role.ASSISTANT && cur.toolCalls.isNotEmpty() -> {
                        stepCalls += cur.toolCalls
                        if (!cur.reasoning.isNullOrBlank()) reasoningTexts += cur.reasoning
                        if (cur.content.isNotBlank()) finalText = cur.content
                        j++
                    }
                    cur.role == Role.TOOL -> {
                        sawTool = true
                        outputs[cur.id] = cur.content
                        j++
                    }
                    cur.role == Role.ASSISTANT && cur.toolCalls.isEmpty() -> {
                        // 阶段性文本（agent 边做边输出）不结束段——后面可能继续工具调用
                        if (cur.content.isNotBlank()) finalText = cur.content
                        j++
                    }
                    else -> break
                }
            }
            if (sawTool) {
                val steps: List<ProcessStep> = buildList {
                    reasoningTexts.forEach { add(ReasoningProcessStep(it)) }
                    stepCalls.forEach { tc ->
                        add(
                            ToolProcessStep(
                                id = tc.id,
                                name = tc.name,
                                args = tc.arguments,
                                output = outputs[tc.id] ?: "",
                                error = "",
                                durationMs = 0,
                                status = ToolCallStatus.DONE,
                            ),
                        )
                    }
                }
                result += MessageGroup.Process(
                    steps = steps,
                    finalText = finalText,
                    key = "proc-" + steps.joinToString("-") { it.label },
                )
                i = j
            } else {
                // 无 tool 结果消息（异常数据）→ 按单条渲染
                result += MessageGroup.Single(m)
                i++
            }
        } else {
            if (m.role == Role.TOOL) {
                // 孤立 TOOL（前面无 assistant 工具段，如 serve 返回截断段尾）→
                // 单步 Process 组，保持"工具调用整体折叠"语义（不平铺）
                result += MessageGroup.Process(
                    steps = listOf(
                        ToolProcessStep(
                            id = m.id,
                            name = "工具调用",
                            args = "",
                            output = m.content,
                            error = "",
                            durationMs = 0,
                            status = ToolCallStatus.DONE,
                        ),
                    ),
                    finalText = "",
                    key = "proc-single-${m.id}",
                )
            } else {
                result += MessageGroup.Single(m)
            }
            i++
        }
    }
    return result
}

/** 历史过程段渲染：ProcessCard（默认折叠全部步骤 + 控制条）+ 最终文本气泡 */
@Composable
private fun ProcessHistoryCard(group: MessageGroup.Process) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ProcessCard(
            steps = group.steps,
            stepContent = { step ->
                when (step) {
                    is ReasoningProcessStep -> Text(
                        "💡 推理过程",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                    is ToolProcessStep -> Text(
                        "🔧 ${step.label}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
            },
        )
        if (group.finalText.isNotBlank()) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.widthIn(max = 340.dp),
            ) {
                MessageContent(
                    content = group.finalText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }
    }
}

// ========== 工作中自动滚动跟随（聊天滚动跟随修复指导） ==========

/**
 * 生成中自动滚动跟随：snapshotFlow 持续监听可见项变化（推理/工具/文本任何
 * 更新都会重新发射），满足全部条件才瞬时滚动到底：
 * 生成中(loading) + 用户未在滚动(isScrollInProgress=false) +
 * 距上次用户主动滚动 > 1.5s + 位于底部附近（最后可见项 ≥ 总项数-2 或内容不满一屏）。
 * 生成结束（loading=false）不强制拉回。
 */
@Composable
private fun rememberAutoScrollFollower(
    listState: LazyListState,
    loading: Boolean,
    enabled: Boolean,
) {
    var lastUserScrollAt by remember { mutableStateOf(0L) }
    // 记录用户滚动：滚动动作开始时打点
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) lastUserScrollAt = System.currentTimeMillis()
    }
    LaunchedEffect(listState, loading, enabled) {
        if (!enabled) return@LaunchedEffect
        snapshotFlow { listState.layoutInfo.visibleItemsInfo }.collect { visible ->
            if (!loading) return@collect
            if (listState.isScrollInProgress) return@collect
            val now = System.currentTimeMillis()
            if (now - lastUserScrollAt < 1500) return@collect
            val total = listState.layoutInfo.totalItemsCount
            val lastVisible = visible.lastOrNull()?.index ?: -1
            // 底部附近：最后可见项在最后 2 项内；内容不满一屏天然在底部
            val atBottom = total <= 0 || lastVisible >= total - 2
            // 节流：已经在最后一项就不重复滚动（流式高频下避免无谓布局）
            if (atBottom && lastVisible != total - 1) {
                listState.scrollToItem(total - 1) // 瞬时，无动画（流式高频下避免动画排队）
            }
        }
    }
}

// ========== 思考档位（文档 §3.2，对标 desktop /effort） ==========

/** 档位 → 按钮标签（auto 显示"自动"） */
private fun effortLabel(level: String): String = when (level) {
    "disabled" -> "关"
    "low" -> "轻"
    "high" -> "高"
    "max" -> "超重"
    else -> "自动"
}

/**
 * 思考档位底部弹层（上拉菜单，对标 rikkahub ReasoningPicker）：
 * 标题 + 提示 + 当前档位名 + 滑杆（松手生效）+ 刻度行（点击直接生效，双向同步）。
 * 档位：disabled(关闭) / auto(自动) / low(轻) / high(高) / max(超重)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EffortSheet(
    current: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val levels = listOf("disabled", "auto", "low", "high", "max")
    val labels = mapOf(
        "disabled" to "关闭",
        "auto" to "自动",
        "low" to "轻",
        "high" to "高",
        "max" to "超重",
    )
    var sliderIndex by remember(current) {
        mutableStateOf(levels.indexOf(current).coerceAtLeast(0))
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Text(
                "思考档位",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Text(
                "调整模型思考深度，档位越高思考越充分、耗时越长",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(18.dp))
            // 当前档位名（选中高亮）
            Text(
                labels[levels[sliderIndex]] ?: "",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            // 滑杆：拖动即时预览，松手吸附并生效
            Slider(
                value = sliderIndex.toFloat(),
                onValueChange = { sliderIndex = it.roundToInt() },
                onValueChangeFinished = { onSelect(levels[sliderIndex]) },
                valueRange = 0f..(levels.size - 1).toFloat(),
                steps = levels.size - 2,
            )
            Spacer(Modifier.height(4.dp))
            // 刻度行：每档一列，点击直接生效，与滑杆双向同步
            Row(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                levels.forEachIndexed { i, level ->
                    val selected = i == sliderIndex
                    Column(
                        Modifier
                            .weight(1f)
                            .clickable {
                                sliderIndex = i
                                onSelect(level)
                            }
                            .padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier
                                .width(if (selected) 20.dp else 16.dp)
                                .height(if (selected) 6.dp else 4.dp)
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant,
                                    RoundedCornerShape(50),
                                ),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            labels[level] ?: level,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }
        }
    }
}

// ========== 用户输入预览（右上角 📋，对标 rikkahub Chat Options） ==========

/** 用户输入预览：搜索框 + 全部用户消息（截断）；点击跳转到正文对应分组并退出预览 */
@Composable
private fun UserInputPreview(
    messages: List<RemoteMessage>,
    grouped: List<MessageGroup>,
    onJumpTo: (Int) -> Unit,
    onClose: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    // 用户消息 → 其在分组中的索引（Single 直接命中；Process 组取组内该消息）
    val userEntries = remember(messages, grouped) {
        messages.mapIndexedNotNull { index, m ->
            if (m.role != Role.USER) return@mapIndexedNotNull null
            val groupIndex = grouped.indexOfFirst { g -> g is MessageGroup.Single && g.message === m }
            if (groupIndex < 0) null else Triple(index, m, groupIndex)
        }
    }
    val filtered = remember(query, userEntries) {
        if (query.isBlank()) userEntries
        else userEntries.filter { (_, m, _) -> m.content.contains(query, ignoreCase = true) }
    }
    Column(Modifier.fillMaxSize()) {
        // 顶部搜索框
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("搜索用户消息…") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            leadingIcon = { Text("🔍", style = MaterialTheme.typography.bodySmall) },
        )
        HorizontalDivider()
        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (query.isBlank()) "本会话还没有用户消息" else "没有匹配「$query」的消息",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(filtered.size) { i ->
                    val (index, m, groupIndex) = filtered[i]
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onJumpTo(groupIndex) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        Text(
                            "#$index",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            m.content.replace(Regex("\\s+"), " ").take(80),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    HorizontalDivider(
                        Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    )
                }
            }
        }
    }
}

// ========== 消息操作行（文档《消息操作与建议指导》§1.2 A 级） ==========

/** assistant 消息下方操作行：复制 | 朗读 | 收藏 | 更多（网页渲染/翻译占位） */
@Composable
private fun MessageActionRow(
    message: RemoteMessage,
    context: Context,
    prefs: AppPreferences,
) {
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var moreOpen by remember { mutableStateOf(false) }
    var webPreview by remember { mutableStateOf(false) }
    // 收藏状态（DataStore 持久化，重启保留）
    var favorites by remember { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(Unit) {
        prefs.favoriteMessages.collect { favorites = it }
    }
    val favKey = "fav-${message.id}"
    val isFav = favKey in favorites
    // TTS 朗读状态（Compose state）
    val ttsSpeaking by MessageTts.speaking

    Row(
        Modifier.padding(start = 4.dp, top = 2.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ActionIconButton("📋") {
            clipboard.setText(AnnotatedString(message.content))
            Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
        }
        // 朗读（TTS 不可用时半透明禁用）
        val ttsOk = MessageTts.isAvailable()
        ActionIconButton(if (ttsSpeaking) "⏹" else "🔊", enabled = ttsOk) {
            if (ttsSpeaking) {
                MessageTts.stop()
            } else {
                MessageTts.ensureInit(context)
                MessageTts.speak(message.content)
            }
        }
        // 收藏
        ActionIconButton(if (isFav) "⭐" else "☆") {
            scope.launch { prefs.toggleFavoriteMessage(favKey, !isFav) }
        }
        // 更多
        Box {
            ActionIconButton("⋯") { moreOpen = true }
            DropdownMenu(expanded = moreOpen, onDismissRequest = { moreOpen = false }) {
                DropdownMenuItem(
                    text = { Text("🖥 网页视图渲染") },
                    onClick = {
                        moreOpen = false
                        webPreview = true
                    },
                )
                DropdownMenuItem(
                    text = { Text("🌐 翻译（复制原文）") },
                    onClick = {
                        moreOpen = false
                        clipboard.setText(AnnotatedString(message.content))
                        Toast.makeText(context, "已复制原文，粘贴到输入框让 agent 翻译", Toast.LENGTH_LONG).show()
                    },
                )
            }
        }
    }

    // 网页视图渲染：markdown → 自包含 HTML → WebView 对话框
    if (webPreview) {
        AlertDialog(
            onDismissRequest = { webPreview = false },
            title = { Text("网页视图预览") },
            text = {
                val html = remember(message.content) { markdownToHtml(message.content) }
                android.webkit.WebView(context).apply {
                    layoutParams = android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        520,
                    )
                    settings.javaScriptEnabled = false
                    loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
                }
            },
            confirmButton = {
                TextButton(onClick = { webPreview = false }) { Text("关闭") }
            },
        )
    }
}

/** 操作行小图标按钮（24dp、onSurfaceVariant、半透明禁用） */
@Composable
private fun ActionIconButton(
    icon: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.height(28.dp),
        contentPadding = PaddingValues(horizontal = 6.dp),
    ) {
        Text(
            icon,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.alpha(if (enabled) 0.85f else 0.38f),
        )
    }
}

/** 简易 markdown → 自包含 HTML（代码块/标题/粗体/行内码/表格/换行） */
private fun markdownToHtml(md: String): String {
    val sb = StringBuilder()
    sb.append(
        "<html><head><meta charset='utf-8'><style>" +
            "body{font-family:sans-serif;font-size:14px;line-height:1.6;padding:8px;color:#1a1a1a}" +
            "pre{background:#f4f4f4;border-radius:8px;padding:10px;overflow-x:auto;font-size:12.5px}" +
            "code{background:#f0f0f0;border-radius:4px;padding:1px 4px}" +
            "pre code{background:none;padding:0}" +
            "table{border-collapse:collapse;margin:8px 0}td,th{border:1px solid #ccc;padding:4px 8px}" +
            "blockquote{border-left:3px solid #ccc;margin:4px 0;padding:2px 8px;color:#555}" +
            "</style></head><body>",
    )
    // 按行处理：代码块状态机
    var inCode = false
    md.lineSequence().forEach { line ->
        val trimmed = line.trim()
        when {
            trimmed.startsWith("```") -> {
                if (inCode) sb.append("</code></pre>") else sb.append("<pre><code>")
                inCode = !inCode
            }
            inCode -> sb.append(escapedHtml(line)).append('\n')
            trimmed.isEmpty() -> sb.append("<br>")
            trimmed.startsWith("# ") -> sb.append("<h3>").append(escapedHtml(trimmed.removePrefix("# "))).append("</h3>")
            trimmed.startsWith("## ") -> sb.append("<h4>").append(escapedHtml(trimmed.removePrefix("## "))).append("</h4>")
            trimmed.startsWith("### ") -> sb.append("<h5>").append(escapedHtml(trimmed.removePrefix("### "))).append("</h5>")
            trimmed.startsWith("> ") -> sb.append("<blockquote>").append(escapedHtml(trimmed.removePrefix("> "))).append("</blockquote>")
            trimmed.startsWith("|") && trimmed.endsWith("|") && trimmed.contains("---") -> { /* 表格分隔行跳过 */ }
            trimmed.startsWith("|") && trimmed.endsWith("|") -> {
                val cells = trimmed.trim('|').split("|").map { escapedHtml(it.trim()) }
                sb.append("<table><tr>").append(cells.joinToString("") { "<td>$it</td>" }).append("</tr></table>")
            }
            trimmed.startsWith("- ") || trimmed.startsWith("* ") ->
                sb.append("• ").append(escapedHtml(trimmed.drop(2))).append("<br>")
            else -> {
                // 行内格式化：**bold**、`code`
                var s = escapedHtml(line)
                s = s.replace(Regex("\\*\\*(.+?)\\*\\*"), "<b>$1</b>")
                s = s.replace(Regex("`([^`]+)`"), "<code>$1</code>")
                sb.append(s).append("<br>")
            }
        }
    }
    if (inCode) sb.append("</code></pre>")
    sb.append("</body></html>")
    return sb.toString()
}

private fun escapedHtml(s: String): String = s
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")

// ========== Suggestion 胶囊（文档《消息操作与建议指导》§2.2 路径二：本地规则） ==========

/** 本地规则建议生成器：按最后一条 assistant 消息内容给出 2-4 条下一步建议（0 成本） */
private fun localSuggestions(messages: List<RemoteMessage>): List<String> {
    val last = messages.lastOrNull { it.role == Role.ASSISTANT } ?: return emptyList()
    val content = last.content
    val out = mutableListOf<String>()
    if ("```" in content) {
        out += "解释一下这段代码"
        out += "指出潜在问题"
    }
    if (Regex("\\|.*\\|.*\\|").containsMatchIn(content)) {
        out += "用表格对比更多方案"
    }
    val trimmed = content.trim()
    if (trimmed.endsWith("?") || trimmed.endsWith("？") || trimmed.endsWith("吗")) {
        out += "继续深入这个话题"
        out += "换个角度回答"
    }
    out += "总结要点"
    out += "列出下一步计划"
    out += "重新生成"
    return out.distinct().take(4)
}

/** 建议胶囊行：横向 LazyRow，全圆角胶囊，点击直接发送（对标 rikkahub chatSuggestions） */
@Composable
private fun SuggestionRow(
    suggestions: List<String>,
    onPick: (String) -> Unit,
) {
    LazyRow(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(suggestions.size) { i ->
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .clickable { onPick(suggestions[i]) }
                    .padding(horizontal = 2.dp, vertical = 1.dp),
            ) {
                Text(
                    suggestions[i],
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    maxLines = 1,
                )
            }
        }
    }
}
