package com.grandcouncil.remote.ui.connections

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.grandcouncil.remote.R
import com.grandcouncil.remote.connection.ConnectionGuide
import com.grandcouncil.remote.connection.ConnectionProfile
import com.grandcouncil.remote.connection.ConnectionStore
import com.grandcouncil.remote.connection.ConnectionTester

/**
 * 连接管理页（M1 验收：增/删/改 + 三层诊断测试 + 向导入口）。
 * 新建连接向导（计划书 3.5.2）：选 agent → 选穿透方式 → 填配置 → 测试，
 * M1 以编辑对话框内嵌方式向导（步骤文案来自 ConnectionGuide）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionScreen(
    viewModel: ConnectionViewModel = viewModel(
        factory = ConnectionViewModelFactory(LocalContext.current.applicationContext),
    ),
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.connections_title)) }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.openEditor() }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.connections_add))
            }
        },
    ) { padding ->
        if (state.profiles.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.connections_empty))
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                items(state.profiles, key = { it.id }) { profile ->
                    ProfileItem(
                        profile = profile,
                        testing = profile.id in state.testingIds,
                        results = state.testResults[profile.id].orEmpty(),
                        onTest = { viewModel.testProfile(profile) },
                        onEdit = { viewModel.openEditor(profile) },
                        onDelete = { viewModel.deleteProfile(profile) },
                    )
                }
            }
        }
    }

    state.editing?.let { profile ->
        ProfileEditorDialog(
            initial = if (state.isNewEditor) null else profile,
            onDismiss = { viewModel.closeEditor() },
            onSave = { viewModel.saveProfile(it) },
        )
    }
}

@Composable
private fun ProfileItem(
    profile: ConnectionProfile,
    testing: Boolean,
    results: List<ConnectionTester.TestResult>,
    onTest: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }

    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(profile.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${profile.agentType.label} · ${profile.type.label} · ${profile.baseUrl}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onTest, enabled = !testing) {
                    if (testing) {
                        CircularProgressIndicator(Modifier.padding(4.dp))
                    } else {
                        Icon(
                            Icons.Filled.NetworkCheck,
                            contentDescription = stringResource(R.string.connections_test),
                        )
                    }
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Filled.Edit, contentDescription = "编辑")
                }
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.connections_delete))
                }
            }
            results.forEach { result ->
                Text(
                    "${if (result.success) "✓" else "✗"} ${result.step.title}：${result.message}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (result.success) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error,
                )
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除连接") },
            text = { Text("确定删除「${profile.name}」？") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text(stringResource(R.string.connections_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.connections_cancel))
                }
            },
        )
    }
}

/** 连接编辑/新建对话框（含向导文案） */
@Composable
private fun ProfileEditorDialog(
    initial: ConnectionProfile?,
    onDismiss: () -> Unit,
    onSave: (ConnectionProfile) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var baseUrl by remember { mutableStateOf(initial?.baseUrl ?: "") }
    var token by remember { mutableStateOf(initial?.token ?: "") }
    var password by remember { mutableStateOf(initial?.password ?: "") }
    var timeout by remember { mutableStateOf((initial?.timeoutMs ?: 10_000L).toString()) }
    var agentType by remember { mutableStateOf(initial?.agentType ?: com.grandcouncil.remote.model.AgentType.REASONIX) }
    var connectionType by remember { mutableStateOf(initial?.type ?: com.grandcouncil.remote.connection.ConnectionType.LAN) }
    var authMode by remember { mutableStateOf(initial?.authMode ?: com.grandcouncil.remote.connection.AuthMode.NONE) }
    var typeMenuOpen by remember { mutableStateOf(false) }
    var agentMenuOpen by remember { mutableStateOf(false) }
    var authMenuOpen by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) stringResource(R.string.connections_add) else "编辑连接") },
        text = {
            Column {
                OutlinedField(stringResource(R.string.field_name), name) { name = it }
                // agent 类型（计划书 3.2：向导第一步选 agent）
                DropdownField(
                    label = stringResource(R.string.field_agent_type),
                    value = agentType.label,
                    expanded = agentMenuOpen,
                    onToggle = { agentMenuOpen = !agentMenuOpen },
                ) {
                    com.grandcouncil.remote.model.AgentType.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label) },
                            onClick = {
                                agentType = option
                                agentMenuOpen = false
                            },
                        )
                    }
                }
                // 穿透方式（向导第二步）
                DropdownField(
                    label = stringResource(R.string.field_connection_type),
                    value = connectionType.label,
                    expanded = typeMenuOpen,
                    onToggle = { typeMenuOpen = !typeMenuOpen },
                ) {
                    com.grandcouncil.remote.connection.ConnectionType.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label) },
                            onClick = {
                                connectionType = option
                                typeMenuOpen = false
                                // 切换方式时用示例地址填空（用户可改）
                                if (baseUrl.isBlank() && initial == null) baseUrl = option.exampleUrl
                            },
                        )
                    }
                }
                OutlinedField(stringResource(R.string.field_base_url), baseUrl) { baseUrl = it }
                // 认证方式
                DropdownField(
                    label = stringResource(R.string.field_auth_mode),
                    value = authMode.label,
                    expanded = authMenuOpen,
                    onToggle = { authMenuOpen = !authMenuOpen },
                ) {
                    com.grandcouncil.remote.connection.AuthMode.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label) },
                            onClick = {
                                authMode = option
                                authMenuOpen = false
                            },
                        )
                    }
                }
                if (authMode == com.grandcouncil.remote.connection.AuthMode.TOKEN) {
                    OutlinedField(stringResource(R.string.field_token), token) { token = it }
                }
                if (authMode == com.grandcouncil.remote.connection.AuthMode.PASSWORD) {
                    OutlinedField(stringResource(R.string.field_password), password) { password = it }
                }
                OutlinedField(stringResource(R.string.field_timeout), timeout) { timeout = it }
                // 穿透方式内置配置向导（计划书 3.5.4）
                Text(
                    ConnectionGuide.stepsFor(connectionType).joinToString("\n") { "· $it" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    ConnectionGuide.RECOMMENDATION,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val id = initial?.id ?: ConnectionProfile.newId()
                    onSave(
                        ConnectionProfile(
                            id = id,
                            name = name.ifBlank { baseUrl },
                            agentType = agentType,
                            type = connectionType,
                            baseUrl = baseUrl,
                            authMode = authMode,
                            token = token,
                            password = password,
                            timeoutMs = timeout.toLongOrNull() ?: 10_000L,
                        )
                    )
                },
                enabled = baseUrl.isNotBlank(),
            ) { Text(stringResource(R.string.connections_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.connections_cancel)) }
        },
    )
}

// 简化表单组件（M1 不引入额外输入框依赖）
@Composable
private fun OutlinedField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
) {
    androidx.compose.material3.OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    )
}

@Composable
private fun DropdownField(
    label: String,
    value: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    menu: @Composable () -> Unit,
) {
    Box(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        ListItem(
            headlineContent = { Text(value) },
            overlineContent = { Text(label) },
            modifier = Modifier.fillMaxWidth(),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = onToggle) { menu() }
    }
}

/** ViewModel 工厂 */
class ConnectionViewModelFactory(private val appContext: android.content.Context) :
    androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        return ConnectionViewModel(ConnectionStore(appContext), ConnectionTester()) as T
    }
}
