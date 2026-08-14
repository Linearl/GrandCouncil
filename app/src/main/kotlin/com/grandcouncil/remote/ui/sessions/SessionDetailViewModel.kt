package com.grandcouncil.remote.ui.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grandcouncil.remote.api.SseClient
import com.grandcouncil.remote.api.dto.ApprovalEventDto
import com.grandcouncil.remote.api.dto.ServeEventKind
import com.grandcouncil.remote.api.dto.ToolEventDto
import com.grandcouncil.remote.connection.ConnectionProfile
import com.grandcouncil.remote.model.RemoteMessage
import com.grandcouncil.remote.model.RemoteSession
import com.grandcouncil.remote.model.Role
import com.grandcouncil.remote.ui.AppPreferences
import com.grandcouncil.remote.model.ToolCall
import com.grandcouncil.remote.model.ToolCallStatus
import com.grandcouncil.remote.repository.SessionRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/** 工具调用状态（流式） */
data class ChatTool(
    val id: String,
    val name: String,
    val args: String,
    val output: String = "",
    val error: String = "",
    val durationMs: Long = 0,
    val status: ToolCallStatus = ToolCallStatus.RUNNING,
)

/** 正在流式构建的 assistant 消息 */
data class StreamingMessage(
    val reasoning: String = "",
    val text: String = "",
    val tools: List<ChatTool> = emptyList(),
) {
    val isEmpty: Boolean get() = reasoning.isEmpty() && text.isEmpty() && tools.isEmpty()
}

/** 会话详情状态（聊天） */
data class SessionDetailUiState(
    val loading: Boolean = true,
    val messages: List<RemoteMessage> = emptyList(),
    val streaming: StreamingMessage? = null,
    val running: Boolean = false,
    val statusText: String = "",
    val pendingApproval: ApprovalEventDto? = null,
    /** 工具审批模式：ask | auto | yolo（PC 端三档，避免被审批卡住） */
    val approvalMode: String? = null,
    val error: String? = null,
    /** T7 上下文用量：(used, window) token；null=未加载/不可用 */
    val contextUsed: Int? = null,
    val contextWindow: Int? = null,
    /** T5 发送失败后待恢复的输入框文本（UI 消费后调 clearRestoreInput） */
    val restoreInput: String? = null,
    /** 联网搜索意图注入开关（文档 §2.2） */
    val webSearchEnabled: Boolean = false,
    val webSearchPrompt: String = AppPreferences.DEFAULT_WEB_SEARCH_PROMPT,
    /** 思考档位（auto=不干预；disabled/low/high/max 对应 /effort） */
    val effortLevel: String = "auto",
    /** serve 拒绝 /effort（不支持）→ 思考按钮置灰 */
    val effortUnsupported: Boolean = false,
    /** 右上角「新建对话」进行中（POST /new 等待） */
    val newSessionBusy: Boolean = false,
    /** 发送后排队提示（§1.3：submit 后 3-5s 无事件） */
    val queued: Boolean = false,
)

/** 审批模式三档（对应 serve ask/auto/yolo） */
enum class ApprovalMode(val wire: String, val label: String) {
    ASK("ask", "询问"),
    AUTO("auto", "自动"),
    YOLO("yolo", "YOLO"),
}

class SessionDetailViewModel(
    private val profile: ConnectionProfile,
    private val session: RemoteSession?,
    private val repository: SessionRepository,
    private val prefs: AppPreferences,
    private val sseClient: SseClient = SseClient.forProfile(profile),
) : ViewModel() {

    private val _uiState = MutableStateFlow(SessionDetailUiState())
    val uiState: StateFlow<SessionDetailUiState> = _uiState.asStateFlow()

    init {
        // 收集联网/思考偏好（文档 §2.2 / §3.2 持久化）
        viewModelScope.launch {
            prefs.webSearch.collect { enabled ->
                _uiState.value = _uiState.value.copy(webSearchEnabled = enabled)
            }
        }
        viewModelScope.launch {
            prefs.webSearchPrompt.collect { prompt ->
                _uiState.value = _uiState.value.copy(webSearchPrompt = prompt)
            }
        }
        viewModelScope.launch {
            prefs.effortLevel.collect { level ->
                _uiState.value = _uiState.value.copy(effortLevel = level)
            }
        }
        load()
        observeEvents()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = SessionDetailUiState(loading = true)
            if (session == null) {
                // 草稿模式（新建会话）：无历史，直接就绪
                _uiState.value = _uiState.value.copy(loading = false)
            } else {
            repository.loadHistory(profile, session).fold(
                onSuccess = { messages ->
                    // 历史消息无稳定 id（content.hashCode 会重复）——index 化保证 LazyColumn key 唯一
                    val unique = messages.reversed().mapIndexed { i, m ->
                        m.copy(id = "h-$i-${m.id}")
                    }
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        messages = unique,
                    )
                },
                onFailure = { e ->
                    val msg = e.message ?: "加载失败"
                    // O1 降级：serve 无 release/takeover 端点，409 只能提示用户先在占用方释放
                    val friendly = if (msg.contains("409")) {
                        "该会话正被桌面版等其他进程使用（只读保护）。\n请先在占用它的设备/窗口关闭该会话，再回来重试。"
                    } else msg
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        error = friendly,
                    )
                },
            )
            // 读取当前审批模式（服务端为准）
            repository.getStatus(profile).onSuccess { status ->
                _uiState.value = _uiState.value.copy(
                    approvalMode = status.toolApprovalMode ?: ApprovalMode.ASK.wire,
                )
            }
            // T7 上下文用量（used/window）
            repository.getContext(profile).onSuccess { (used, window) ->
                if (window > 0) {
                    _uiState.value = _uiState.value.copy(contextUsed = used, contextWindow = window)
                }
            }
            }
        }
    }

    /** 切换工具审批模式（询问/自动/YOLO——避免工具调用被审批卡住） */
    fun setApprovalMode(mode: ApprovalMode) {
        _uiState.value = _uiState.value.copy(approvalMode = mode.wire)
        viewModelScope.launch {
            repository.setApprovalMode(profile, mode.wire).onFailure {
                _uiState.value = _uiState.value.copy(error = "切换审批模式失败")
            }
        }
    }

    /** 常开事件流：聊天/审批/状态全部由它驱动（SSE 为唯一真相源；断线自动重连） */
    private fun observeEvents() {
        viewModelScope.launch {
            while (true) {
                try {
                    sseClient.events(SseClient.eventsUrl(profile)).collect { event ->
                        // 任何事件到达 → 清除排队提示
                        if (_uiState.value.queued) {
                            _uiState.value = _uiState.value.copy(queued = false)
                        }
                        when (event.kind) {
                    ServeEventKind.TURN_STARTED -> {
                        _uiState.value = _uiState.value.copy(
                            running = true,
                            streaming = StreamingMessage(),
                            statusText = "开始处理…",
                            pendingApproval = null,
                        )
                    }

                    ServeEventKind.REASONING -> {
                        val s = _uiState.value.streaming ?: return@collect
                        _uiState.value = _uiState.value.copy(
                            streaming = s.copy(reasoning = s.reasoning + event.text),
                        )
                    }

                    ServeEventKind.TEXT -> {
                        val s = _uiState.value.streaming ?: return@collect
                        _uiState.value = _uiState.value.copy(
                            streaming = s.copy(text = s.text + event.text),
                        )
                    }

                    ServeEventKind.MESSAGE -> finalizeStreaming()

                    ServeEventKind.TOOL_DISPATCH -> {
                        val tool = event.tool ?: return@collect
                        val s = _uiState.value.streaming ?: return@collect
                        _uiState.value = _uiState.value.copy(
                            streaming = s.copy(
                                tools = s.tools + ChatTool(
                                    id = tool.id,
                                    name = tool.name,
                                    args = tool.args,
                                ),
                            ),
                            statusText = "运行 ${tool.name}…",
                        )
                    }

                    ServeEventKind.TOOL_RESULT -> {
                        val tool = event.tool ?: return@collect
                        val s = _uiState.value.streaming ?: return@collect
                        _uiState.value = _uiState.value.copy(
                            streaming = s.copy(
                                tools = s.tools.map {
                                    if (it.id == tool.id) {
                                        it.copy(
                                            output = tool.output,
                                            error = tool.err,
                                            durationMs = tool.durationMs,
                                            status = if (tool.err.isNotEmpty()) ToolCallStatus.DONE
                                            else ToolCallStatus.DONE,
                                        )
                                    } else it
                                },
                            ),
                        )
                    }

                    ServeEventKind.APPROVAL_REQUEST -> {
                        event.approval?.let { approval ->
                            _uiState.value = _uiState.value.copy(
                                pendingApproval = approval,
                                statusText = "等待审批：${approval.tool}",
                            )
                        }
                    }

                    ServeEventKind.NOTICE -> {
                        if (event.text.isNotEmpty()) {
                            val msg = RemoteMessage(
                                id = "notice-${event.text.hashCode()}",
                                role = Role.NOTICE,
                                content = event.text,
                            )
                            _uiState.value = _uiState.value.copy(
                                messages = _uiState.value.messages + msg,
                            )
                        }
                    }

                    ServeEventKind.TURN_DONE -> {
                        finalizeStreaming()
                        _uiState.value = _uiState.value.copy(
                            running = false,
                            statusText = if (event.err != null) "回合失败：${event.err}" else "完成",
                        )
                    }

                    ServeEventKind.PHASE -> {
                        if (event.text.isNotEmpty()) {
                            _uiState.value = _uiState.value.copy(statusText = event.text)
                        }
                    }
                }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // 事件流断开（网络/服务重启）：保持已渲染内容，3s 后重连
                _uiState.value = _uiState.value.copy(statusText = "事件流断开，重连中…")
            }
                delay(3000)
            }
        }
    }

    /** 流式消息固化进列表 */
    private fun finalizeStreaming() {
        val s = _uiState.value.streaming ?: return
        if (s.isEmpty) return
        val msg = RemoteMessage(
            id = "assistant-${System.currentTimeMillis()}",
            role = Role.ASSISTANT,
            content = s.text,
            reasoning = s.reasoning.takeIf { it.isNotEmpty() },
            toolCalls = s.tools.map {
                ToolCall(
                    id = it.id,
                    name = it.name,
                    arguments = it.args,
                    status = it.status,
                )
            },
        )
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + msg,
            streaming = null,
        )
    }

    fun clearRestoreInput() {
        _uiState.value = _uiState.value.copy(restoreInput = null)
    }

    /** 发送消息（T5 乐观插入：立即显示用户消息；失败移除并恢复输入框文本） */
    fun send(text: String) {
        val raw = text.trim()
        if (raw.isEmpty()) return
        viewModelScope.launch {
            val st = _uiState.value
            // 联网注入：开启时消息末尾追加注入文本（文档 §2.2）
            val input = if (st.webSearchEnabled) {
                "$raw\n\n${st.webSearchPrompt}"
            } else {
                raw
            }
            // 思考档位：非 auto 时先发 /effort 斜杠命令（serve 端拦截，204 后无消息记录）
            val effort = st.effortLevel
            if (effort != "auto") {
                val code = repository.effortCommand(profile, effort)
                if (code != null && code >= 200 && code < 300) {
                    // 命令生效
                } else if (code != null) {
                    // 500：忙碌或模型不支持；标记不支持（文案含 not configurable）并提示
                    _uiState.value = _uiState.value.copy(
                        statusText = "思考档位切换失败（HTTP $code）：任务进行中或服务不支持",
                    )
                    if (code == 500 && repository.lastEffortError?.contains("not configurable") == true) {
                        _uiState.value = _uiState.value.copy(effortUnsupported = true)
                    }
                }
            }
            val userMsg = RemoteMessage(
                id = "user-${System.currentTimeMillis()}",
                role = Role.USER,
                content = raw,
                timestamp = System.currentTimeMillis(),
            )
            _uiState.value = _uiState.value.copy(
                messages = _uiState.value.messages + userMsg,
                error = null,
            )
            runCatching {
                repository.submit(profile, input)
            }.onSuccess {
                // 首帧反馈（文档 §1.2）：发送成功立即出现 assistant 占位（思考中…），不等 turn_started
                _uiState.value = _uiState.value.copy(
                    streaming = _uiState.value.streaming ?: StreamingMessage(),
                    running = true,
                )
                // 排队提示（§1.3）：4s 内无任何 SSE 事件 → 显示「排队中（服务正忙）…」
                viewModelScope.launch {
                    kotlinx.coroutines.delay(4000)
                    if (_uiState.value.running && _uiState.value.streaming?.isEmpty == true &&
                        _uiState.value.statusText != "开始处理…"
                    ) {
                        _uiState.value = _uiState.value.copy(
                            statusText = "排队中（服务正忙）…",
                            queued = true,
                        )
                    }
                }
            }.onFailure { e ->
                // 失败：移除乐观消息 + 恢复输入框文本（UI 收到 restoreInput 后回填）
                _uiState.value = _uiState.value.copy(
                    messages = _uiState.value.messages.filterNot { it.id == userMsg.id },
                    error = "发送失败：${e.message ?: "未知错误"}",
                    restoreInput = raw,
                )
            }
        }
    }

    /** 联网开关切换（§2.2，DataStore 持久化跨会话保留） */
    fun setWebSearch(enabled: Boolean) {
        viewModelScope.launch { prefs.setWebSearch(enabled) }
        _uiState.value = _uiState.value.copy(webSearchEnabled = enabled)
    }

    /**
     * 右上角「新建对话」（对标 rikkahub New Message）：POST /new 创建空会话。
     * serve 忙碌时 500 → 提示 + 轮询 status 至空闲后自动重试（§1.3）。
     */
    fun newSession(onDone: () -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(newSessionBusy = true)
            val result = repository.newSession(profile)
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(newSessionBusy = false)
                onDone()
                return@launch
            }
            // 失败：忙碌（500）→ 提示 + 轮询重试（最多 3 次，间隔 2s）
            _uiState.value = _uiState.value.copy(
                newSessionBusy = false,
                statusText = "当前服务正在处理其他任务，等待重试…",
            )
            repeat(3) {
                kotlinx.coroutines.delay(2000)
                val status = repository.getStatus(profile).getOrNull()
                if (status?.running == false) {
                    val retry = repository.newSession(profile)
                    if (retry.isSuccess) {
                        _uiState.value = _uiState.value.copy(statusText = "")
                        onDone()
                        return@launch
                    }
                }
            }
            _uiState.value = _uiState.value.copy(
                statusText = "新建会话失败：服务持续繁忙，请稍后再试",
            )
        }
    }

    /** 思考档位切换（§3.2，DataStore 持久化；发送时经 /effort 命令生效） */
    fun setEffort(level: String) {
        viewModelScope.launch { prefs.setEffortLevel(level) }
        _uiState.value = _uiState.value.copy(effortLevel = level, effortUnsupported = false)
    }

    /** 审批回复（allow/session/persist 语义对应 serve /approve） */
    fun approve(approval: ApprovalEventDto, allow: Boolean, sessionScope: Boolean = false, persist: Boolean = false) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(pendingApproval = null)
            runCatching {
                repository.approve(
                    profile,
                    buildJsonObject {
                        put("id", JsonPrimitive(approval.id))
                        put("allow", JsonPrimitive(allow))
                        if (sessionScope) put("session", JsonPrimitive(true))
                        if (persist) put("persist", JsonPrimitive(true))
                    },
                )
            }.onFailure {
                _uiState.value = _uiState.value.copy(error = "审批提交失败")
            }
        }
    }

    /** 中止当前回合（POST /cancel） */
    fun cancel() {
        viewModelScope.launch {
            runCatching { repository.cancel(profile) }
        }
    }
}
