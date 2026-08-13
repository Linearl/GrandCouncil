package com.grandcouncil.remote.ui.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grandcouncil.remote.connection.ConnectionProfile
import com.grandcouncil.remote.connection.ConnectionStore
import com.grandcouncil.remote.model.HeldBy
import com.grandcouncil.remote.model.RemoteSession
import com.grandcouncil.remote.repository.SessionRepository
import com.grandcouncil.remote.ui.AppPreferences
import com.grandcouncil.remote.ui.theme.DensityPreset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

/** 会话筛选（V3 会话页：按设备筛选 + 只显示本地聊天 + 状态筛选） */
enum class SessionFilter(val label: String) {
    ALL("全部"),
    MINE("本地聊天"),
    RUNNING("运行中"),
    PENDING("待审批"),
    FAILED("失败"),
}

/** 服务器状态信息（会话页顶部显示，来自 GET /status） */
data class ServeInfo(
    val label: String,
    val running: Boolean,
    val cwd: String,
)

/** 会话列表状态 */
data class SessionListUiState(
    val profiles: List<ConnectionProfile> = emptyList(),
    val activeProfile: ConnectionProfile? = null,
    val sessions: List<RemoteSession> = emptyList(),
    val serveInfo: ServeInfo? = null,
    val filter: SessionFilter = SessionFilter.ALL,
    val density: DensityPreset = DensityPreset.COMFORTABLE,
    val loading: Boolean = false,
    val error: String? = null,
) {
    /** 应用筛选后的会话（本地聊天 = 非其他设备持有） */
    val filteredSessions: List<RemoteSession>
        get() = when (filter) {
            SessionFilter.ALL -> sessions
            SessionFilter.MINE -> sessions.filter { it.heldBy != HeldBy.OTHER }
            SessionFilter.RUNNING -> sessions.filter { it.isCurrent }
            SessionFilter.PENDING -> sessions.filter { it.heldBy == HeldBy.ME }
            SessionFilter.FAILED -> emptyList()
        }
}

class SessionListViewModel(
    private val connectionStore: ConnectionStore,
    private val repository: SessionRepository,
    appPreferences: AppPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SessionListUiState())
    val uiState: StateFlow<SessionListUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            appPreferences.density.collect { density ->
                _uiState.value = _uiState.value.copy(density = density)
            }
        }
        viewModelScope.launch {
            connectionStore.profiles.collect { profiles ->
                val current = _uiState.value
                if (profiles.isEmpty()) {
                    _uiState.value = SessionListUiState(profiles = profiles, density = current.density)
                } else {
                    val activeStillValid =
                        current.activeProfile?.let { ap -> profiles.any { it.id == ap.id } } == true
                    if (!activeStillValid) {
                        val activeId = connectionStore.activeId.firstOrNull()
                        val active = profiles.firstOrNull { it.id == activeId } ?: profiles.first()
                        _uiState.value = current.copy(profiles = profiles, activeProfile = active)
                        refresh()
                    } else {
                        _uiState.value = current.copy(profiles = profiles)
                    }
                }
            }
        }
    }

    fun selectProfile(profile: ConnectionProfile) {
        _uiState.value = _uiState.value.copy(activeProfile = profile)
        viewModelScope.launch {
            connectionStore.setActive(profile.id)
            refresh()
        }
    }

    fun setFilter(filter: SessionFilter) {
        _uiState.value = _uiState.value.copy(filter = filter)
    }

    fun refresh() {
        val profile = _uiState.value.activeProfile ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            repository.listSessions(profile).fold(
                onSuccess = { sessions ->
                    _uiState.value = _uiState.value.copy(loading = false, sessions = sessions)
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        error = e.message ?: "加载失败",
                    )
                },
            )
            // 服务器信息（label/running/cwd），失败不阻塞列表
            repository.getStatus(profile).onSuccess { status ->
                _uiState.value = _uiState.value.copy(
                    serveInfo = ServeInfo(
                        label = status.label ?: "Reasonix serve",
                        running = status.running ?: false,
                        cwd = status.cwd ?: "",
                    ),
                )
            }
        }
    }
}
