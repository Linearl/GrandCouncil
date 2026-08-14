package com.grandcouncil.remote.ui.demo

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
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.grandcouncil.remote.ui.components.MessageContent
import com.grandcouncil.remote.ui.theme.ReasonixColors

/**
 * C4 Try a Demo：零服务器演示模式。
 * 硬编码脚本数据走真实 UI 组件（消息气泡/工具卡片/审批卡片），
 * 完整演示「推理 → 工具 → 审批 → 完成」流程；Allow/Deny 分支结局。
 */

private data class DemoMsg(
    val id: String,
    val role: Int, // 0=user 1=assistant 2=tool
    val content: String = "",
    val reasoning: String? = null,
    val toolName: String? = null,
    val toolDone: Boolean = false,
    val toolError: Boolean = false,
    val pendingApproval: Boolean = false,
)

private val DEMO_SCRIPT = listOf(
    DemoMsg("d1", 1, "👋 这是演示模式（零服务器）。我会模拟一次真实的远程 agent 协作：\n\n**你的请求 → 推理 → 工具调用 → 权限审批 → 完成**。\n\n试着回复我：拆分一下这个项目的 todos，并整理成清单"),
    DemoMsg("d2", 0, "拆分一下这个项目的 todos，并整理成清单"),
    DemoMsg("d3", 1, reasoning = "用户想要把项目 TODO 拆分成可执行清单。我可以先读取现有 TODO 文件，再整理输出。", content = "好的，我先看一下项目里的 TODO 文件。"),
    DemoMsg("d4", 2, content = "cat TODO.md\n\n- [ ] 完成 Reasonix 手机端接入\n- [ ] 审批模式三档切换\n- [ ] 会话列表分组", toolName = "bash"),
    DemoMsg("d5", 2, content = "write_file", toolName = "write_file", pendingApproval = true),
)

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun DemoChatScreen(onBack: () -> Unit) {
    var msgs by remember { mutableStateOf(DEMO_SCRIPT) }
    var approved by remember { mutableStateOf(false) }
    var denied by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("演示 · 试玩") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("← 返回") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(msgs, key = { it.id }) { m ->
                DemoMessageItem(m)
            }
            // 审批结果分支
            if (approved || denied) {
                item("branch") {
                    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                if (approved) "✅ 已允许——工具继续执行，agent 完成整理" else "⛔ 已拒绝——agent 改用安全方式继续",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            if (approved) {
                                Surface(
                                    color = ReasonixColors.success.copy(alpha = 0.12f),
                                    shape = MaterialTheme.shapes.medium,
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                ) {
                                    Text(
                                        "✅ write_file 已完成\n\n📋 Todo 清单：\n- [x] Reasonix 手机端接入（进行中）\n- [x] 审批模式三档切换（已可用）\n- [ ] 分类通知",
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(10.dp),
                                    )
                                }
                                Text(
                                    "演示结束 🎉 现在去「向导」添加真实连接吧",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            } else {
                                Text(
                                    "⛔ 已拒绝——agent 未写入文件，输出纯文本清单。\n\n演示结束 🎉 真实使用中，审批会推送到这里。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }
                    }
                }
            }
            // 审批卡片（未处理时）
            if (!approved && !denied) {
                item("approval") {
                    Card(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                        colors = androidx.compose.material3.CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                        ),
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (running) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.widthIn(max = 14.dp),
                                        strokeWidth = 2.dp,
                                    )
                                    Text(
                                        "🛡 Agent 需要权限",
                                        style = MaterialTheme.typography.titleSmall,
                                        modifier = Modifier.padding(start = 8.dp),
                                    )
                                }
                            }
                            Text(
                                "write_file — todo.md",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                            Row(Modifier.padding(top = 8.dp)) {
                                TextButton(onClick = {
                                    denied = true
                                    running = false
                                }) { Text("Deny", color = MaterialTheme.colorScheme.error) }
                                TextButton(onClick = {
                                    approved = true
                                    running = false
                                }) { Text("Always") }
                                TextButton(onClick = {
                                    approved = true
                                    running = false
                                }) { Text("Allow") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DemoMessageItem(m: DemoMsg) {
    when (m.role) {
        0 -> {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            ) {
                Text(
                    m.content,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(10.dp),
                )
            }
        }
        1 -> {
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (m.reasoning?.isNotBlank() == true) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            "💡 推理过程",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(8.dp),
                        )
                    }
                }
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.widthIn(max = 340.dp),
                ) {
                    MessageContent(
                        content = m.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(10.dp),
                        )
                }
            }
        }
        else -> {
            Surface(
                color = ReasonixColors.success.copy(alpha = 0.12f),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            ) {
                Column(Modifier.padding(10.dp)) {
                    Text(
                        "🔧 ${m.toolName ?: "tool"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = ReasonixColors.success,
                    )
                    Text(
                        m.content,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        modifier = Modifier.padding(top = 4.dp),
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
    HorizontalDivider(Modifier.padding(vertical = 2.dp), color = Color.Transparent)
}
