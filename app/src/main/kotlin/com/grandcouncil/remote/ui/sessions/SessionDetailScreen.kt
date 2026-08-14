package com.grandcouncil.remote.ui.sessions

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
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
import com.grandcouncil.remote.ui.export.SessionExporter
import kotlinx.coroutines.launch
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

    // 工作中滚动展示（问题三）：生成中（streaming != null）且用户未上翻
    // （距底 < 2 屏）→ 瞬时 scrollToItem 持续跟随；上翻暂停、回底恢复；
    // 生成结束时（streaming 变 null）不强制拉回
    LaunchedEffect(
        state.messages.size,
        state.streaming?.text?.length,
        state.streaming?.tools?.size,
        state.streaming?.reasoning?.length,
    ) {
        val streaming = state.streaming
        val count = grouped.size + if (streaming != null) 1 else 0
        if (count <= 0) return@LaunchedEffect
        kotlinx.coroutines.delay(80)
        val info = listState.layoutInfo
        val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
        val visibleCount = info.visibleItemsInfo.size.coerceAtLeast(1)
        val farFromBottom = count - 1 - lastVisible > visibleCount * 2
        if (!farFromBottom && streaming != null) {
            listState.scrollToItem(count - 1)
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
                    voiceSlot = {
                        VoiceInputButton(
                            onResult = { text -> input = if (input.isBlank()) text else "$input $text" },
                            readOnly = session?.heldBy == HeldBy.OTHER,
                        )
                    },
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
                    Box(Modifier.weight(1f).fillMaxWidth()) {
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
                                        MessageItem(group.message)
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
@Composable
private fun InputBar(
    input: String,
    running: Boolean,
    readOnly: Boolean,
    onInput: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    voiceSlot: @Composable () -> Unit = {},
) {
    val haptic = LocalHapticFeedback.current
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
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            voiceSlot()
            OutlinedTextField(
                value = input,
                onValueChange = onInput,
                placeholder = { Text(if (readOnly) "只读会话（发送将接管）" else "回复或输入指令…") },
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
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
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
                MessageContent(
                    content = streaming.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(12.dp),
                )
            }
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
                        if (cur.content.isNotBlank()) finalText = cur.content
                        j++
                        break
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
            result += MessageGroup.Single(m)
            i++
        }
    }
    return result
}

/** 历史过程段渲染：ProcessCard（默认折叠尾部 2 步 + 控制条）+ 最终文本气泡 */
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
