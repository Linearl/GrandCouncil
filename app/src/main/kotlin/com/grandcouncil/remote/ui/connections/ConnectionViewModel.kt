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
    /** C3 保存前必测：测试中 / 编辑器内错误提示 */
    val saving: Boolean = false,
    val editorError: String? = null,
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
            // 新建（无参）时用空草稿，否则 editing=null 导致对话框不渲染
            editing = profile ?: ConnectionProfile(
                id = ConnectionProfile.newId(),
                name = "",
                baseUrl = "",
            ),
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

    /** C3 保存前必测：自动跑完整诊断，全部通过才保存；失败阻止保存并给出失败原因 */
    fun saveWithTest(profile: ConnectionProfile) {
        if (_uiState.value.saving) return
        _uiState.value = _uiState.value.copy(saving = true, editorError = null)
        viewModelScope.launch {
            val results = mutableListOf<ConnectionTester.TestResult>()
            tester.test(profile) { results += it }
            val ok = results.isNotEmpty() && results.all { it.success }
            if (ok) {
                val current = _uiState.value.profiles
                val updated = if (current.any { it.id == profile.id }) {
                    current.map { if (it.id == profile.id) profile else it }
                } else {
                    current + profile
                }
                connectionStore.save(updated)
                _uiState.value = _uiState.value.copy(saving = false)
                closeEditor()
            } else {
                val reason = results.lastOrNull()?.message ?: "未知错误"
                _uiState.value = _uiState.value.copy(
                    saving = false,
                    editorError = "测试未通过：$reason",
                )
            }
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
