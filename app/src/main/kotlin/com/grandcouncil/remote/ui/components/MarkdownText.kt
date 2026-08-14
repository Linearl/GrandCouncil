package com.grandcouncil.remote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * T6 轻量 Markdown 渲染（无第三方依赖）：
 * - 块级：标题（#~###）、无序列表（短横线/星号）、引用（>）、表格行（| 原样等宽）、分隔线（---）
 * - 行内：`行内码`、**粗体**、*斜体*、[链接](url) 保留原文
 * 双主题跟随外层 color 参数。
 */
@Composable
fun MarkdownText(
    content: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val lines = content.lines()
    Column(modifier) {
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            when {
                line.isBlank() -> Unit

                // 标题
                line.startsWith("### ") -> BlockText(line.drop(4), style, color, FontWeight.SemiBold, 1.15f)
                line.startsWith("## ") -> BlockText(line.drop(3), style, color, FontWeight.Bold, 1.25f)
                line.startsWith("# ") -> BlockText(line.drop(2), style, color, FontWeight.Bold, 1.35f)

                // 分隔线
                line.trim().matches(Regex("[-*_]{3,}")) -> androidx.compose.material3.HorizontalDivider(
                    Modifier.padding(vertical = 4.dp),
                )

                // 引用（> 前缀，斜体 + 左侧边距）
                line.startsWith("> ") -> Text(
                    buildInline(line.drop(2), style, color, FontStyle.Italic),
                    style = style,
                    color = color.copy(alpha = 0.85f),
                    modifier = Modifier.padding(start = 8.dp),
                )

                // 无序列表
                line.trimStart().startsWith("- ") || line.trimStart().startsWith("* ") -> {
                    val indent = line.indexOfFirst { it != ' ' }.coerceAtLeast(0)
                    Text(
                        buildInline("• " + line.trimStart().drop(2), style, color, null),
                        style = style,
                        modifier = Modifier.padding(start = (4 + indent).dp),
                    )
                }

                // 表格行（连续 | 行整体交给 MarkdownTable：表头/边框/横滚/行展开）
                line.startsWith("|") -> {
                    val tableLines = mutableListOf(line)
                    while (i + 1 < lines.size && lines[i + 1].trimStart().startsWith("|")) {
                        tableLines += lines[i + 1]
                        i++
                    }
                    MarkdownTable(tableLines, modifier = Modifier.padding(vertical = 4.dp))
                }

                else -> BlockText(line, style, color, null, 1f)
            }
            i++
        }
    }
}

/** 普通块：行内元素渲染（代码/粗体/斜体） */
@Composable
private fun BlockText(
    text: String,
    style: TextStyle,
    color: Color,
    weight: FontWeight?,
    lineScale: Float,
) {
    val annotated = buildInline(text, style, color, null)
    Text(
        annotated,
        style = style.copy(
            fontWeight = weight ?: style.fontWeight,
            fontSize = style.fontSize * lineScale,
            lineHeight = style.lineHeight * lineScale,
        ),
        color = color,
    )
}

/** 行内元素解析：`code`（等宽+底色）→ **bold** → *italic* → 原文 */
private fun buildInline(
    text: String,
    style: TextStyle,
    color: Color,
    forcedItalic: FontStyle?,
): androidx.compose.ui.text.AnnotatedString {
    val builder = androidx.compose.ui.text.AnnotatedString.Builder()
    var pos = 0
    val inlineRegex = Regex("(`[^`]+`)|(\\*\\*[^*]+\\*\\*)|(\\*[^*]+\\*)|(\\[[^\\]]+\\]\\([^)]+\\))")
    for (m in inlineRegex.findAll(text)) {
        if (m.range.first > pos) builder.append(text.substring(pos, m.range.first))
        val token = m.value
        when {
            token.startsWith("`") -> {
                val code = token.trim('`')
                builder.withStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = style.fontSize * 0.92f,
                        background = color.copy(alpha = 0.12f),
                    ),
                ) { builder.append(code) }
            }
            token.startsWith("**") -> {
                builder.withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    builder.append(token.trim('*'))
                }
            }
            token.startsWith("*") -> {
                builder.withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    builder.append(token.trim('*'))
                }
            }
            else -> builder.append(token) // 链接原文保留
        }
        pos = m.range.last + 1
    }
    if (pos < text.length) builder.append(text.substring(pos))
    // 引用整体斜体（块级强制）
    if (forcedItalic != null) {
        val s = builder.toAnnotatedString()
        return androidx.compose.ui.text.AnnotatedString.Builder().apply {
            withStyle(SpanStyle(fontStyle = forcedItalic)) { append(s) }
        }.toAnnotatedString()
    }
    return builder.toAnnotatedString()
}
