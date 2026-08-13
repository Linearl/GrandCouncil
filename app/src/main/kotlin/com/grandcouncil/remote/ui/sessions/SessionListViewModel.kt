package com.grandcouncil.remote.ui.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grandcouncil.remote.connection.ConnectionProfile
import com.grandcouncil.remote.connection.ConnectionStore
import com.grandcouncil.remote.model.RemoteSession
import com.grandcouncil.remote.repository.SessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

/** 会话列表状态（M1：加载/刷新/连接切换） */
data class SessionListUiState(
    val profiles: List<ConnectionProfile> = emptyList(),
    val activeProfile: ConnectionProfile? = null,
    val sessions: List<RemoteSession> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

class SessionListViewModel(
    private val connectionStore: ConnectionStore,
    private val repository: SessionRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SessionListUiState())
    val uiState: StateFlow<SessionListUiState> = _uiState.asStateFlow()

    private var loaded = false

    init {
        viewModelScope.launch {
            connectionStore.profiles.collect { profiles ->
                val current = _uiState.value
                if (profiles.isEmpty()) {
                    // 无连接配置：清空状态，由 UI 引导去连接页
                    _uiState.value = SessionListUiState(profiles = profiles)
                    loaded = false
                } else {
                    val activeStillValid =
                        current.activeProfile?.let { ap -> profiles.any { it.id == ap.id } } == true
                    if (!activeStillValid) {
                        val activeId = connectionStore.activeId.firstOrNull()
                        val active = profiles.firstOrNull { it.id == activeId } ?: profiles.first()
                        loaded = true
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
        loaded = true
        _uiState.value = _uiState.value.copy(activeProfile = profile)
        viewModelScope.launch {
            connectionStore.setActive(profile.id)
            refresh()
        }
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
        }
    }
}
