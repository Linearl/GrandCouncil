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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

/** 会话筛选（V3 会话页：设备筛选 + 状态筛选） */
enum class SessionFilter(val label: String) {
    ALL("全部"),
    MINE("本地聊天"),
    RUNNING("运行中"),
    PENDING("待审批"),
    FAILED("失败"),
}

/** 服务器状态信息（连接维度） */
data class ServeInfo(
    val label: String,
    val running: Boolean,
    val cwd: String,
)

/** 聚合会话（带所属连接） */
data class AggregatedSession(
    val session: RemoteSession,
    val profile: ConnectionProfile,
)

/** 会话列表状态（聚合视图：多连接会话合并 + 设备筛选） */
data class SessionListUiState(
    val profiles: List<ConnectionProfile> = emptyList(),
    /** profileId → 会话列表 */
    val sessionsByProfile: Map<String, List<RemoteSession>> = emptyMap(),
    /** profileId → 服务器信息 */
    val serveInfoByProfile: Map<String, ServeInfo> = emptyMap(),
    /** 设备筛选：null=全部设备，否则 profileId */
    val deviceFilter: String? = null,
    val filter: SessionFilter = SessionFilter.ALL,
    val density: DensityPreset = DensityPreset.COMFORTABLE,
    val loading: Boolean = false,
    val error: String? = null,
) {
    /** 全部聚合会话（含连接） */
    val aggregated: List<AggregatedSession>
        get() = sessionsByProfile.flatMap { (profileId, sessions) ->
            val profile = profiles.firstOrNull { it.id == profileId } ?: return@flatMap emptyList()
            sessions.map { AggregatedSession(it, profile) }
        }

    /** 设备筛选后的聚合会话 */
    val filteredByDevice: List<AggregatedSession>
        get() = if (deviceFilter == null) aggregated
        else aggregated.filter { it.profile.id == deviceFilter }

    /** 应用状态筛选后的会话（本地聊天 = 非其他设备持有） */
    val filteredSessions: List<AggregatedSession>
        get() = when (filter) {
            SessionFilter.ALL -> filteredByDevice
            SessionFilter.MINE -> filteredByDevice.filter { it.session.heldBy != HeldBy.OTHER }
            SessionFilter.RUNNING -> filteredByDevice.filter { it.session.isCurrent }
            SessionFilter.PENDING -> filteredByDevice.filter { it.session.heldBy == HeldBy.ME }
            SessionFilter.FAILED -> emptyList()
        }

    /** 在线设备数（服务器信息已加载的） */
    val onlineCount: Int get() = serveInfoByProfile.size
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
                _uiState.value = current.copy(profiles = profiles)
                // 设备筛选失效时重置（删除连接等场景）
                if (profiles.isNotEmpty() && current.deviceFilter !in profiles.map { it.id }) {
                    _uiState.value = _uiState.value.copy(deviceFilter = null)
                }
                if (profiles.isEmpty()) {
                    _uiState.value = SessionListUiState(
                        profiles = profiles,
                        density = current.density,
                    )
                } else {
                    refresh()
                }
            }
        }
    }

    fun setDeviceFilter(profileId: String?) {
        _uiState.value = _uiState.value.copy(deviceFilter = profileId)
    }

    fun setFilter(filter: SessionFilter) {
        _uiState.value = _uiState.value.copy(filter = filter)
    }

    /** 聚合刷新：并发拉取全部连接的会话 + 服务器信息 */
    fun refresh() {
        val profiles = _uiState.value.profiles
        if (profiles.isEmpty()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            coroutineScope {
                val jobs = profiles.map { profile ->
                    async {
                        val sessionsResult = repository.listSessions(profile)
                        val statusResult = repository.getStatus(profile)
                        Triple(profile, sessionsResult, statusResult)
                    }
                }
                jobs.forEach { job ->
                    val (profile, sessionsResult, statusResult) = job.await()
                    sessionsResult.onSuccess { sessions ->
                        _uiState.value = _uiState.value.copy(
                            sessionsByProfile = _uiState.value.sessionsByProfile + (profile.id to sessions),
                        )
                    }.onFailure { e ->
                        if (_uiState.value.profiles.size == 1) {
                            _uiState.value = _uiState.value.copy(error = e.message ?: "加载失败")
                        }
                    }
                    statusResult.onSuccess { status ->
                        _uiState.value = _uiState.value.copy(
                            serveInfoByProfile = _uiState.value.serveInfoByProfile + (
                                profile.id to ServeInfo(
                                    label = status.label ?: "Reasonix serve",
                                    running = status.running ?: false,
                                    cwd = status.cwd ?: "",
                                )
                                ),
                        )
                    }
                }
                _uiState.value = _uiState.value.copy(loading = false)
            }
        }
    }
}
