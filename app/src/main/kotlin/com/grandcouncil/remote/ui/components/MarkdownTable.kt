package com.grandcouncil.remote.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.rememberTextMeasurer

/**
 * Markdown 表格渲染（无第三方依赖，与现有轻量渲染风格一致）。
 *
 * 解析：表头行 + 分隔行（`|---|---|`）+ 数据行；渲染：表头 SemiBold + 底色区分、
 * 单元格 padding 6-8dp、列宽按内容测量（TextMeasurer）、窄屏横向滚动、
 * 1dp outlineVariant 描边圆角容器、表头下分隔线。
 * 交互：点击整行展开/收起该行全文（默认每单元格 2 行截断）。
 */
@Composable
fun MarkdownTable(
    lines: List<String>,
    modifier: Modifier = Modifier,
) {
    if (lines.isEmpty()) return
    val parsed = remember(lines) { parseTable(lines) }
    if (parsed.columns.isEmpty()) {
        // 无法解析（如全是分隔线）→ 原样等宽展示
        Text(
            lines.joinToString("\n"),
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.padding(vertical = 4.dp),
        )
        return
    }

    val style = MaterialTheme.typography.bodySmall // 12sp 单元格文字
    val measurer = rememberTextMeasurer()
    val density = androidx.compose.ui.platform.LocalDensity.current
    // 列宽 = 该列全部单元格（含表头）内容最大宽度 + 两侧 padding（8dp×2 = 16dp）
    val colWidths = remember(parsed, measurer) {
        parsed.columns.mapIndexed { col, _ ->
            val maxW = parsed.rows.map { row -> row.getOrNull(col) ?: "" }
                .plus(parsed.header.getOrNull(col) ?: "")
                .maxOf { text ->
                    measurer.measure(text, style = style).size.width
                }
            with(density) { (maxW + 32).toDp() }
        }
    }

    var expandedRow by remember { mutableStateOf<Int?>(null) }
    val cellPadH = 8.dp
    val cellPadV = 6.dp

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.horizontalScroll(rememberScrollState())) {
            // 表头行
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            ) {
                parsed.columns.forEachIndexed { col, header ->
                    Text(
                        header,
                        style = style.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .width(colWidths[col])
                            .padding(horizontal = cellPadH, vertical = cellPadV),
                    )
                }
            }
            // 数据行
            parsed.rows.forEachIndexed { rowIdx, row ->
                val expanded = expandedRow == rowIdx
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { expandedRow = if (expanded) null else rowIdx }
                        .background(
                            if (rowIdx % 2 == 1) {
                                MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.6f)
                            } else {
                                androidx.compose.ui.graphics.Color.Transparent
                            },
                        ),
                ) {
                    parsed.columns.forEachIndexed { col, _ ->
                        Text(
                            row.getOrNull(col) ?: "",
                            style = style,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = if (expanded) Int.MAX_VALUE else 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .width(colWidths[col])
                                .padding(horizontal = cellPadH, vertical = cellPadV),
                        )
                    }
                }
            }
        }
    }
}

/** 解析 markdown 表格：返回表头列与数据行（分隔行剔除）。无法解析时 columns 为空。 */
private data class ParsedTable(
    val header: List<String>,
    val columns: List<String>,
    val rows: List<List<String>>,
)

private fun parseTable(lines: List<String>): ParsedTable {
    val cleaned = lines.filter { it.trim().isNotEmpty() }
    if (cleaned.isEmpty()) return ParsedTable(emptyList(), emptyList(), emptyList())

    val header = splitRow(cleaned[0])
    if (header.size < 2) return ParsedTable(emptyList(), emptyList(), emptyList())

    // 第二行是分隔行（|---|---|）→ 剔除；否则按普通数据行
    val dataStart = if (cleaned.size > 1 && cleaned[1].trim().matches(Regex("^\\|?[\\s:|-]+\\|?$")) &&
        cleaned[1].contains("-")
    ) 2 else 1

    val rows = cleaned.drop(dataStart).map { splitRow(it) }
        .filter { it.isNotEmpty() }
    return ParsedTable(header, header, rows)
}

private fun splitRow(line: String): List<String> {
    val t = line.trim()
    val body = if (t.startsWith("|")) t.drop(1) else t
    val end = if (body.endsWith("|")) body.dropLast(1) else body
    return end.split("|").map { it.trim() }
}
