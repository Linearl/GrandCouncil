package com.grandcouncil.remote.ui.connections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.grandcouncil.remote.R
import com.grandcouncil.remote.connection.ConnectionGuide
import com.grandcouncil.remote.connection.ConnectionProfile
import com.grandcouncil.remote.connection.ConnectionStore
import com.grandcouncil.remote.connection.ConnectionTester
import com.grandcouncil.remote.connection.RecentLinksStore
import com.grandcouncil.remote.scan.ScannerActivity
import com.grandcouncil.remote.scan.isValidPairUrl
import com.grandcouncil.remote.scan.splitScannedUrl
import kotlinx.coroutines.launch

/**
 * 连接管理（M1 验收：增/删/改 + 连接诊断测试；任务 7 起含 /capabilities 能力探测层）。
 * embedded=true 时由外层（ConfigScreen）提供 Scaffold 顶栏，本组件只渲染内容。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionScreen(
    embedded: Boolean = false,
    padding: androidx.compose.foundation.layout.PaddingValues =
        androidx.compose.foundation.layout.PaddingValues(),
    viewModel: ConnectionViewModel = viewModel(
        factory = ConnectionViewModelFactory(LocalContext.current.applicationContext),
    ),
) {
    val state by viewModel.uiState.collectAsState()

    if (embedded) {
        ConnectionContent(state, viewModel, padding)
    } else {
        Scaffold(
            topBar = { TopAppBar(title = { Text(stringResource(R.string.connections_title)) }) },
            floatingActionButton = {
                FloatingActionButton(onClick = { viewModel.openEditor() }) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.connections_add))
                }
            },
        ) { p ->
            ConnectionContent(state, viewModel, p)
        }
    }

    state.editing?.let { profile ->
        ProfileEditorDialog(
            initial = if (state.isNewEditor) null else profile,
            onDismiss = { viewModel.closeEditor() },
            onSave = { viewModel.saveWithTest(it) },
            saving = state.saving,
            editorError = state.editorError,
        )
    }
}

@Composable
private fun ConnectionContent(
    state: ConnectionUiState,
    viewModel: ConnectionViewModel,
    padding: androidx.compose.foundation.layout.PaddingValues,
) {
    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.openEditor() }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.connections_add))
            }
        },
    ) { p ->
        val contentModifier = Modifier.padding(padding)
        if (state.profiles.isEmpty()) {
            Box(contentModifier.fillMaxSize().padding(p), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.connections_empty))
            }
        } else {
            LazyColumn(contentModifier.fillMaxSize().padding(p)) {
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
                        "${profile.agentType.label} · ${profile.type?.label ?: "自定义地址"} · ${profile.baseUrl}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onTest, enabled = !testing) {
                    if (testing) {
                        CircularProgressIndicator(Modifier.padding(4.dp))
                    } else {
                        Icon(
                            Icons.Filled.CheckCircle,
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
                // 任务 7：warn=连接可用但能力受限（旧 serve/能力探测未完成），警示色与纯通过区分
                val mark = if (result.success) if (result.warn) "△" else "✓" else "✗"
                val color = when {
                    !result.success -> MaterialTheme.colorScheme.error
                    result.warn -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.primary
                }
                Text(
                    "$mark ${result.step.title}：${result.message}",
                    style = MaterialTheme.typography.bodySmall,
                    color = color,
                )
            }
            // 连接失败提示：电脑端按需启动对应项目 serve（懒加载闭环）
            if (results.any { !it.success }) {
                Text(
                    "💡 未启动？在电脑上运行：bash scripts/serve-manager.sh start ${profile.name}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
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

/** 连接编辑/新建对话框（含向导文案；C2 宿主命令 + C3 保存前必测） */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ProfileEditorDialog(
    initial: ConnectionProfile?,
    onDismiss: () -> Unit,
    onSave: (ConnectionProfile) -> Unit,
    saving: Boolean = false,
    editorError: String? = null,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var baseUrl by remember { mutableStateOf(initial?.baseUrl ?: "") }
    var token by remember { mutableStateOf(initial?.token ?: "") }
    var timeout by remember { mutableStateOf((initial?.timeoutMs ?: 10_000L).toString()) }
    var agentType by remember { mutableStateOf(initial?.agentType ?: com.grandcouncil.remote.model.AgentType.REASONIX) }
    var connectionType by remember { mutableStateOf<com.grandcouncil.remote.connection.ConnectionType?>(initial?.type) }
    var projectId by remember { mutableStateOf(initial?.projectId ?: "") }
    // tl1539 简化：认证固定 TOKEN（不再提供无 token/密码方式），authMode 选择器与密码框移除
    // GC-20：扫码配对 launcher（扫到 http(s) 地址回填；tl1539②：query 拆两字段——
    // Base URL 只收干净地址，token 拆进 token 字段并切 TOKEN 模式）
    val scanLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val url = result.data?.getStringExtra(ScannerActivity.EXTRA_URL)
        if (!url.isNullOrBlank()) {
            val (clean, scannedToken) = splitScannedUrl(url)
            baseUrl = clean
            if (scannedToken != null) {
                token = scannedToken
            }
        }
    }
    // GC-20：最近链接（新建时显示，点击复用；私有存储最多 5 条）
    val editorContext = LocalContext.current
    val recentLinks = remember(editorContext) { RecentLinksStore(editorContext).list() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) stringResource(R.string.connections_add) else "编辑连接") },
        text = {
            Column {
                // tl1539 简化：接入方式科普——本质都是「地址+访问令牌」两样，三种填法殊途同归
                Text(
                    "接入方式说明：所有方式本质都是填「地址 + 访问令牌」两样。\n" +
                        "· 手动输入：电脑端运行 reasonix serve 后，把地址和令牌填入下方\n" +
                        "· 扫码：电脑端「设置 → 本地服务」展示连接二维码，扫码可拍照或从相册选图\n" +
                        "· 剪贴板：电脑端点「复制连接」，再点下方剪贴板按钮自动填入\n" +
                        "（令牌与二维码均在电脑端 reasonix 桌面版「设置 → 本地服务」中获取）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                OutlinedField(stringResource(R.string.field_name), name) { name = it }
                // 核心两框（tl1539 简化：只此两样必填）
                OutlinedField(stringResource(R.string.field_base_url), baseUrl) { baseUrl = it }
                OutlinedField(stringResource(R.string.field_token), token) { token = it }
                // 三入口：剪贴板解析 / 扫码（扫码页内含相册选图）
                val entryScope = rememberCoroutineScope()
                val entryContext = LocalContext.current
                val entryClipboard = LocalClipboardManager.current
                Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "快速填入：",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = {
                        entryScope.launch {
                            val text = entryClipboard.getText()?.toString()?.trim() ?: ""
                            val (clean, scanned) = splitScannedUrl(text)
                            if (text.isNotEmpty() && isValidPairUrl(clean)) {
                                baseUrl = clean
                                if (scanned != null) token = scanned
                                android.widget.Toast.makeText(entryContext, "已从剪贴板填入地址与令牌", android.widget.Toast.LENGTH_SHORT).show()
                            } else {
                                android.widget.Toast.makeText(entryContext, "剪贴板无有效连接（先在电脑端点「复制连接」）", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    }) {
                        Text("📋 剪贴板", style = MaterialTheme.typography.labelMedium)
                    }
                    TextButton(onClick = {
                        scanLauncher.launch(Intent(entryContext, ScannerActivity::class.java))
                    }) {
                        Text("📷 扫码", style = MaterialTheme.typography.labelMedium)
                    }
                }
                // 高级可选项（网关/超时）——普通直连无需动；agent 类型固定 REASONIX 不再暴露
                OutlinedField("项目（servepool 网关多项目时，可选）", projectId) { projectId = it }
                OutlinedField(stringResource(R.string.field_timeout), timeout) { timeout = it }
                // C2 宿主侧命令：按表单值生成 serve 启动命令（端口取 baseUrl，默认 18789）
                val port = Regex(":(\\d+)").find(baseUrl)?.groupValues?.get(1) ?: "18789"
                val cmd = "reasonix serve --addr 0.0.0.0:$port --auth token"
                var copied by remember { mutableStateOf(false) }
                val clipboard = LocalClipboardManager.current
                val scope = rememberCoroutineScope()
                Card(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Row(
                        Modifier.padding(start = 12.dp, top = 8.dp, bottom = 4.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            cmd,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = {
                            clipboard.setText(AnnotatedString(cmd))
                            copied = true
                            scope.launch {
                                kotlinx.coroutines.delay(2000)
                                copied = false
                            }
                        }) {
                            Text(if (copied) "已复制 ✓" else "复制命令")
                        }
                    }
                }
                // GC-20 最近链接（仅新建时展示，点击复用）
                if (initial == null && recentLinks.isNotEmpty()) {
                    FlowRow(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        recentLinks.forEach { link ->
                            AssistChip(
                                onClick = { baseUrl = link },
                                label = {
                                    Text(link.removePrefix("http://").removePrefix("https://").take(28))
                                },
                            )
                        }
                    }
                }
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    com.grandcouncil.remote.connection.ConnectionType.entries.forEach { option ->
                        AssistChip(
                            onClick = {
                                connectionType = option
                                // 地址为空或仍是模板地址时才填入，避免覆盖用户手填
                                val isTemplate =
                                    com.grandcouncil.remote.connection.ConnectionType.entries.any { it.exampleUrl == baseUrl }
                                if (baseUrl.isBlank() || isTemplate) baseUrl = option.exampleUrl
                            },
                            label = { Text(option.label) },
                        )
                    }
                }
                // 组网帮助文案（未选预设时显示通用建议）
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
                // C3 保存前必测：失败原因展示
                if (editorError != null) {
                    Text(
                        editorError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
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
                            authMode = com.grandcouncil.remote.connection.AuthMode.TOKEN,
                            projectId = projectId.trim().ifBlank { null },
                            token = token.trim(),
                            password = "",
                            timeoutMs = timeout.toLongOrNull() ?: 10_000L,
                        )
                    )
                },
                // tl1539 简化：地址与令牌都非空才可保存（不再允许无 token 认证）
                enabled = baseUrl.isNotBlank() && token.isNotBlank() && !saving,
            ) { Text(if (saving) "测试中…" else stringResource(R.string.connections_save)) }
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
            // 点击整行展开菜单（修复：此前无 clickable，菜单永远打不开）
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle() },
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
