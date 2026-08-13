package com.grandcouncil.remote.ui.sessions

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.grandcouncil.remote.connection.ConnectionProfile
import com.grandcouncil.remote.model.HeldBy
import com.grandcouncil.remote.model.RemoteMessage
import com.grandcouncil.remote.model.RemoteSession
import com.grandcouncil.remote.model.Role
import com.grandcouncil.remote.repository.SessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 会话详情状态 */
data class SessionDetailUiState(
    val loading: Boolean = true,
    val messages: List<RemoteMessage> = emptyList(),
    val error: String? = null,
)

class SessionDetailViewModel(
    private val profile: ConnectionProfile,
    private val session: RemoteSession,
    private val repository: SessionRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SessionDetailUiState())
    val uiState: StateFlow<SessionDetailUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = SessionDetailUiState(loading = true)
            repository.loadHistory(profile, session).fold(
                onSuccess = { messages ->
                    _uiState.value = SessionDetailUiState(
                        loading = false,
                        messages = messages.reversed(), // 新消息在下，聊天式布局
                    )
                },
                onFailure = { e ->
                    _uiState.value = SessionDetailUiState(
                        loading = false,
                        error = e.message ?: "加载失败",
                    )
                },
            )
        }
    }
}

/**
 * 会话详情页（只读历史，M1 前奏）：点击会话进入，展示 /history 消息列表。
 * M2 将在此页加入输入框与 SSE 流式聊天、审批卡片。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailScreen(
    profile: ConnectionProfile,
    session: RemoteSession,
    onBack: () -> Unit,
    viewModel: SessionDetailViewModel = viewModel(
        factory = SessionDetailViewModelFactory(
            profile = profile,
            session = session,
            appContext = LocalContext.current.applicationContext,
        ),
        key = "detail-${session.id}",
    ),
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(session.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (session.heldBy == HeldBy.OTHER) {
                        Text(
                            "🔒 只读",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 12.dp),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // 只读会话提示（P0-1：桌面版持有时显示，发送消息将接管——M2 接入）
            if (session.heldBy == HeldBy.OTHER) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "该会话被其他设备（桌面版等）持有，当前只读查看；发送消息将接管会话",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
            when {
            state.loading -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            state.error != null -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.error!!, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { viewModel.load() }) { Text("重试") }
                }
            }

            state.messages.isEmpty() -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { Text("该会话暂无消息") }

            else -> LazyColumn(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.messages, key = { it.id }) { message ->
                    MessageItem(message)
                }
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
                modifier = Modifier.padding(10.dp),
            )
        }

        Role.TOOL -> Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        ) {
            Column(Modifier.padding(10.dp)) {
                Text(
                    message.toolCalls.firstOrNull()?.let { "工具：${it.name}" } ?: "工具调用",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    message.content.take(300),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        else -> {
            val isUser = message.role == Role.USER
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
            ) {
                Surface(
                    color = if (isUser) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.widthIn(max = 300.dp),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        if (!isUser && !message.reasoning.isNullOrBlank()) {
                            // 推理内容（折叠展示首段）
                            Text(
                                "🤔 " + message.reasoning.trim().lineSequence().first().take(80),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            HorizontalDivider(Modifier.padding(vertical = 4.dp))
                        }
                        Text(
                            message.content,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (message.toolCalls.isNotEmpty()) {
                            message.toolCalls.forEach { tool ->
                                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                                Text(
                                    "🔧 ${tool.name}(${tool.arguments.take(60)})",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
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

/** ViewModel 工厂（携带 profile/session 参数） */
class SessionDetailViewModelFactory(
    private val profile: ConnectionProfile,
    private val session: RemoteSession,
    private val appContext: android.content.Context,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        SessionDetailViewModel(profile, session, SessionRepository()) as T
}
