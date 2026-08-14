package com.grandcouncil.remote.ui.wizard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.grandcouncil.remote.connection.AuthMode
import com.grandcouncil.remote.connection.ConnectionGuide
import com.grandcouncil.remote.connection.ConnectionProfile
import com.grandcouncil.remote.connection.ConnectionStore
import com.grandcouncil.remote.connection.ConnectionTester
import com.grandcouncil.remote.connection.ConnectionType
import com.grandcouncil.remote.model.AgentType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 连接向导（V3 分步）：
 * 1. 选连接方式（LAN/节点小宝/花生壳/Tailscale/frp/自定义）
 * 2. 填 Base URL + 认证方式
 * 3. 宿主侧启动命令（可复制）+ token/密码
 * 4. 三层诊断测试 → 通过后保存
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WizardScreen() {
    val context = LocalContext.current
    val store = remember { ConnectionStore(context) }
    val scope = rememberCoroutineScope()
    val tester = remember { ConnectionTester() }

    var step by remember { mutableIntStateOf(1) }
    var connectionType by remember { mutableStateOf(ConnectionType.LAN) }
    var baseUrl by remember { mutableStateOf("") }
    var authMode by remember { mutableStateOf(AuthMode.TOKEN) }
    var token by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var testing by remember { mutableStateOf(false) }
    var testResults by remember { mutableStateOf<List<ConnectionTester.TestResult>>(emptyList()) }
    var saved by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("连接向导 · 第 $step 步 / 共 4 步") },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            // 步骤指示器
            Row(
                Modifier.fillMaxWidth().padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                repeat(4) { i ->
                    val on = i + 1 <= step
                    Text(
                        if (on) "●" else "○",
                        color = if (on) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant,
                    )
                }
            }

            when (step) {
                1 -> StepConnectionType(connectionType) { connectionType = it }
                2 -> StepUrl(
                    connectionType, baseUrl, authMode,
                    onUrl = { baseUrl = it },
                    onAuth = { authMode = it },
                )
                3 -> StepCommand(
                    connectionType, authMode,
                    token, password,
                    onToken = { token = it },
                    onPassword = { password = it },
                )
                4 -> StepTest(
                    testing, testResults, saved,
                    onTest = {
                        val profile = draftProfile(connectionType, baseUrl, authMode, token, password)
                        testing = true
                        testResults = emptyList()
                        scope.launch {
                            val results = mutableListOf<ConnectionTester.TestResult>()
                            tester.test(profile) { results += it }
                            testResults = results.toList()
                            testing = false
                            if (results.all { it.success }) {
                                val existing = store.profiles.first()
                                store.save(existing + profile)
                                saved = true
                            }
                        }
                    },
                )
            }

            // 底部导航按钮
            Row(
                Modifier.fillMaxWidth().padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                OutlinedButton(
                    onClick = { if (step > 1) step-- },
                    enabled = step > 1,
                ) { Text("上一步") }
                if (step < 4) {
                    Button(
                        onClick = { step++ },
                        enabled = step != 2 || baseUrl.isNotBlank(),
                    ) { Text("下一步") }
                }
            }
        }
    }
}

private fun draftProfile(
    type: ConnectionType,
    baseUrl: String,
    auth: AuthMode,
    token: String,
    password: String,
) = ConnectionProfile(
    id = ConnectionProfile.newId(),
    name = baseUrl,
    agentType = AgentType.REASONIX,
    type = type,
    baseUrl = baseUrl,
    authMode = auth,
    token = token,
    password = password,
)

@Composable
private fun StepConnectionType(
    selected: ConnectionType,
    onSelect: (ConnectionType) -> Unit,
) {
    Text("选择你的连接方式", style = MaterialTheme.typography.titleLarge)
    Text(
        "穿透服务由你自行选择/搭建，App 只按地址连接",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 12.dp),
    )
    ConnectionType.entries.forEach { type ->
        Card(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 5.dp)
                .selectable(
                    selected = selected == type,
                    onClick = { onSelect(type) },
                ),
        ) {
            Row(
                Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(type.label, style = MaterialTheme.typography.titleSmall)
                    Text(
                        type.exampleUrl,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    if (selected == type) "●" else "○",
                    color = if (selected == type) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

@Composable
private fun StepUrl(
    type: ConnectionType,
    baseUrl: String,
    authMode: AuthMode,
    onUrl: (String) -> Unit,
    onAuth: (AuthMode) -> Unit,
) {
    Text("填写地址与认证", style = MaterialTheme.typography.titleLarge)
    Text(
        ConnectionGuide.stepsFor(type).joinToString("\n") { "· $it" },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 12.dp),
    )
    OutlinedTextField(
        value = baseUrl,
        onValueChange = onUrl,
        label = { Text("Base URL（${type.exampleUrl}）") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    HorizontalDivider(Modifier.padding(vertical = 12.dp))
    Text("认证方式", style = MaterialTheme.typography.titleSmall)
    AuthMode.entries.forEach { mode ->
        Row(
            Modifier
                .fillMaxWidth()
                .selectable(selected = authMode == mode, onClick = { onAuth(mode) })
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(mode.label, modifier = Modifier.weight(1f))
            Text(
                if (authMode == mode) "●" else "○",
                color = if (authMode == mode) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@Composable
private fun StepCommand(
    type: ConnectionType,
    authMode: AuthMode,
    token: String,
    password: String,
    onToken: (String) -> Unit,
    onPassword: (String) -> Unit,
) {
    Text("在电脑上启动 serve", style = MaterialTheme.typography.titleLarge)
    Text(
        "复制下面的命令到电脑运行；启动后 token 会显示在日志中",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 12.dp),
    )
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            val cmd = buildString {
                append("reasonix serve --addr 0.0.0.0:8787")
                if (authMode != AuthMode.NONE) {
                    append(" --auth ${if (authMode == AuthMode.TOKEN) "token" else "password"}")
                }
            }
            Text(
                cmd,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            var copied by remember { mutableStateOf(false) }
            val clipboard = LocalClipboardManager.current
            val scope = rememberCoroutineScope()
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
    if (authMode == AuthMode.TOKEN) {
        OutlinedTextField(
            value = token,
            onValueChange = onToken,
            label = { Text("Token（serve 启动日志中查看）") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )
    }
    if (authMode == AuthMode.PASSWORD) {
        OutlinedTextField(
            value = password,
            onValueChange = onPassword,
            label = { Text("serve 密码") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )
    }
    HorizontalDivider(Modifier.padding(vertical = 12.dp))
    Text(
        ConnectionGuide.RECOMMENDATION,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.tertiary,
    )
}

@Composable
private fun StepTest(
    testing: Boolean,
    results: List<ConnectionTester.TestResult>,
    saved: Boolean,
    onTest: () -> Unit,
) {
    Text("测试并保存", style = MaterialTheme.typography.titleLarge)
    Text(
        "测试通过后连接自动保存，立即可以在会话页使用",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 12.dp),
    )
    Button(onClick = onTest, enabled = !testing, modifier = Modifier.fillMaxWidth()) {
        if (testing) {
            CircularProgressIndicator(Modifier.padding(end = 8.dp))
            Text("测试中…")
        } else {
            Text("测试连接")
        }
    }
    results.forEach { result ->
        Text(
            "${if (result.success) "✓" else "✗"} ${result.step.title}：${result.message}",
            color = if (result.success) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = 8.dp),
            textAlign = TextAlign.Start,
        )
    }
    if (saved) {
        Text(
            "✓ 已保存，去会话页开始使用",
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}
