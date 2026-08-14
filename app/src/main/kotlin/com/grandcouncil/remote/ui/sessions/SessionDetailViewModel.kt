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
    /** T5 发送失败后待恢复的输入框文本（UI 消费后调 clearRestoreInput） */
    val restoreInput: String? = null,
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
    private val sseClient: SseClient = SseClient.forProfile(profile),
) : ViewModel() {

    private val _uiState = MutableStateFlow(SessionDetailUiState())
    val uiState: StateFlow<SessionDetailUiState> = _uiState.asStateFlow()

    init {
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
        val input = text.trim()
        if (input.isEmpty()) return
        viewModelScope.launch {
            val userMsg = RemoteMessage(
                id = "user-${System.currentTimeMillis()}",
                role = Role.USER,
                content = input,
                timestamp = System.currentTimeMillis(),
            )
            _uiState.value = _uiState.value.copy(
                messages = _uiState.value.messages + userMsg,
                error = null,
            )
            runCatching {
                repository.submit(profile, input)
            }.onFailure { e ->
                // 失败：移除乐观消息 + 恢复输入框文本（UI 收到 restoreInput 后回填）
                _uiState.value = _uiState.value.copy(
                    messages = _uiState.value.messages.filterNot { it.id == userMsg.id },
                    error = "发送失败：${e.message ?: "未知错误"}",
                    restoreInput = input,
                )
            }
        }
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
