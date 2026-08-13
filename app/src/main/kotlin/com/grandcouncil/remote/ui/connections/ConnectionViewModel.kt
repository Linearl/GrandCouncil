package com.grandcouncil.remote.ui.connections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grandcouncil.remote.connection.ConnectionProfile
import com.grandcouncil.remote.connection.ConnectionStore
import com.grandcouncil.remote.connection.ConnectionTester
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ConnectionUiState(
    val profiles: List<ConnectionProfile> = emptyList(),
    /** 正在测试的连接 id → 测试结果序列 */
    val testResults: Map<String, List<ConnectionTester.TestResult>> = emptyMap(),
    val testingIds: Set<String> = emptySet(),
    /** 正在编辑的连接（null 表示未打开编辑器） */
    val editing: ConnectionProfile? = null,
    val isNewEditor: Boolean = false,
)

class ConnectionViewModel(
    private val connectionStore: ConnectionStore,
    private val tester: ConnectionTester,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConnectionUiState())
    val uiState: StateFlow<ConnectionUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            connectionStore.profiles.collect { profiles ->
                _uiState.value = _uiState.value.copy(profiles = profiles)
            }
        }
    }

    fun openEditor(profile: ConnectionProfile? = null) {
        _uiState.value = _uiState.value.copy(
            editing = profile,
            isNewEditor = profile == null,
        )
    }

    fun closeEditor() {
        _uiState.value = _uiState.value.copy(editing = null, isNewEditor = false)
    }

    fun saveProfile(profile: ConnectionProfile) {
        val current = _uiState.value.profiles
        val updated = if (current.any { it.id == profile.id }) {
            current.map { if (it.id == profile.id) profile else it }
        } else {
            current + profile
        }
        viewModelScope.launch {
            connectionStore.save(updated)
            closeEditor()
        }
    }

    fun deleteProfile(profile: ConnectionProfile) {
        viewModelScope.launch {
            connectionStore.save(_uiState.value.profiles.filter { it.id != profile.id })
        }
    }

    /** 三层诊断测试（网络可达 → 认证 → 协议握手），逐层回传结果 */
    fun testProfile(profile: ConnectionProfile) {
        if (profile.id in _uiState.value.testingIds) return
        _uiState.value = _uiState.value.copy(
            testingIds = _uiState.value.testingIds + profile.id,
            testResults = _uiState.value.testResults - profile.id,
        )
        viewModelScope.launch {
            val results = mutableListOf<ConnectionTester.TestResult>()
            tester.test(profile) { result ->
                results += result
                _uiState.value = _uiState.value.copy(
                    testResults = _uiState.value.testResults + (profile.id to results.toList()),
                )
            }
            _uiState.value = _uiState.value.copy(
                testingIds = _uiState.value.testingIds - profile.id,
            )
        }
    }
}
