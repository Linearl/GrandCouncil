package com.grandcouncil.remote.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.grandcouncil.remote.model.ToolCallStatus
import com.grandcouncil.remote.ui.theme.ReasonixColors

/**
 * 过程步骤（推理/工具调用共用，对标 rikkahub ChainOfThought 的步骤模型）。
 */
sealed interface ProcessStep {
    val label: String
    val isError: Boolean
    val isRunning: Boolean
}

/** 工具调用步骤 */
data class ToolProcessStep(
    val id: String,
    val name: String,
    val args: String,
    val output: String,
    val error: String,
    val durationMs: Long,
    val status: ToolCallStatus,
) : ProcessStep {
    override val label get() = name
    override val isError get() = error.isNotEmpty()
    override val isRunning get() = status == ToolCallStatus.RUNNING
}

/** 推理步骤 */
data class ReasoningProcessStep(val text: String) : ProcessStep {
    override val label get() = "推理过程"
    override val isError get() = false
    override val isRunning get() = false
}

/**
 * 过程聚合容器：多步工具/推理过程**默认折叠**，只展示尾部若干步；
 * 顶部控制条点击展开/收起全部（对标 rikkahub ChainOfThought）。
 *
 * 折叠态高度预算：控制条（≈32dp）+ 尾部 N 步（每步 ≈32dp）；步骤数 ≤
 * collapsedVisibleCount 时不出现折叠壳（小过程直接展示）。
 * 折叠态控制条右侧显示失败角标「⚠ N 个失败」（步骤 error 非空时）。
 */
@Composable
fun ProcessCard(
    steps: List<ProcessStep>,
    collapsedVisibleCount: Int = 2,
    modifier: Modifier = Modifier,
    stepContent: @Composable (ProcessStep) -> Unit,
) {
    if (steps.isEmpty()) return
    var expanded by remember { mutableStateOf(false) }
    val total = steps.size
    val showToggle = total > collapsedVisibleCount
    val visible = if (expanded) steps else steps.takeLast(collapsedVisibleCount)
    val failCount = steps.count { it.isError }

    Surface(
        modifier = modifier.animateContentSize(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) {
            visible.forEach { step -> stepContent(step) }
            if (showToggle) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { expanded = !expanded }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (expanded) "▴ 收起" else "▾ 展开全部 ${total - collapsedVisibleCount} 步",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (failCount > 0) {
                        Text(
                            "⚠ $failCount 个失败",
                            style = MaterialTheme.typography.labelSmall,
                            color = ReasonixColors.err,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        }
    }
}
