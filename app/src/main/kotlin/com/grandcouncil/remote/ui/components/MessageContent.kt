package com.grandcouncil.remote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

// ---- 代码块分段（``` 围栏提取） ----

private val CODE_BLOCK_RE = Regex("```([\\w+-]*)\\n([\\s\\S]*?)(?:\\n```|```)")

/** 消息内容分段：文本 / 代码块 交替 */
data class ContentSegment(val text: String, val isCode: Boolean, val language: String?)

fun splitCodeBlocks(content: String): List<ContentSegment> {
    val result = mutableListOf<ContentSegment>()
    var last = 0
    for (m in CODE_BLOCK_RE.findAll(content)) {
        if (m.range.first > last) result += ContentSegment(content.substring(last, m.range.first), false, null)
        result += ContentSegment(m.groupValues[2], true, m.groupValues[1].ifBlank { null })
        last = m.range.last + 1
    }
    if (last < content.length) result += ContentSegment(content.substring(last), false, null)
    return result
}

// ---- 轻量语法高亮（Atom One 明/暗配色） ----

private data class CodePalette(
    val keyword: Color,
    val string: Color,
    val number: Color,
    val comment: Color,
    val function: Color,
    val default: Color,
    val background: Color,
    val diffAdd: Color,
    val diffDel: Color,
    val diffMeta: Color,
)

private val ATOM_ONE_DARK = CodePalette(
    keyword = Color(0xFFC678DD), string = Color(0xFF98C379), number = Color(0xFFD19A66),
    comment = Color(0xFF5C6370), function = Color(0xFF61AFEF), default = Color(0xFFABB2BF),
    background = Color(0xFF282C34),
    diffAdd = Color(0xFF98C379), diffDel = Color(0xFFE06C75), diffMeta = Color(0xFF61AFEF),
)

private val ATOM_ONE_LIGHT = CodePalette(
    keyword = Color(0xFFA626A4), string = Color(0xFF50A14F), number = Color(0xFFC18401),
    comment = Color(0xFF9CA0A4), function = Color(0xFF4078F2), default = Color(0xFF383A42),
    background = Color(0xFFFAFAFA),
    diffAdd = Color(0xFF50A14F), diffDel = Color(0xFFE45649), diffMeta = Color(0xFF4078F2),
)

private val KEYWORDS: Map<String, Set<String>> = mapOf(
    "kotlin" to setOf("fun", "val", "var", "if", "else", "when", "class", "object", "interface",
        "return", "suspend", "import", "package", "data", "sealed", "override", "private",
        "public", "internal", "protected", "companion", "init", "constructor", "try", "catch",
        "finally", "throw", "null", "true", "false", "this", "super", "for", "while", "do",
        "in", "is", "as", "by", "break", "continue", "typealias", "enum", "open", "abstract"),
    "java" to setOf("public", "private", "protected", "static", "final", "void", "int", "long",
        "double", "float", "boolean", "byte", "char", "short", "String", "class", "interface",
        "extends", "implements", "import", "package", "new", "return", "if", "else", "for",
        "while", "do", "switch", "case", "break", "continue", "try", "catch", "finally", "throw",
        "throws", "null", "true", "false", "this", "super", "instanceof", "enum"),
    "python" to setOf("def", "class", "if", "elif", "else", "for", "while", "return", "import",
        "from", "as", "with", "try", "except", "finally", "raise", "pass", "break", "continue",
        "lambda", "yield", "global", "nonlocal", "None", "True", "False", "and", "or", "not",
        "in", "is", "del", "assert", "async", "await"),
    "go" to setOf("func", "package", "import", "if", "else", "for", "range", "return", "var",
        "const", "type", "struct", "interface", "map", "chan", "go", "defer", "select", "switch",
        "case", "break", "continue", "fallthrough", "default", "nil", "true", "false"),
    "javascript" to setOf("function", "const", "let", "var", "if", "else", "for", "while", "do",
        "switch", "case", "break", "continue", "return", "new", "class", "extends", "import",
        "export", "default", "async", "await", "try", "catch", "finally", "throw", "null",
        "undefined", "true", "false", "this", "typeof", "instanceof", "in", "of"),
    "typescript" to setOf("function", "const", "let", "var", "if", "else", "for", "while", "do",
        "switch", "case", "break", "continue", "return", "new", "class", "extends", "import",
        "export", "default", "async", "await", "try", "catch", "finally", "throw", "null",
        "undefined", "true", "false", "this", "typeof", "instanceof", "in", "of", "interface",
        "type", "enum", "implements", "namespace", "declare", "readonly", "public", "private",
        "protected", "abstract", "static"),
    "bash" to setOf("if", "then", "else", "elif", "fi", "for", "while", "do", "done", "case",
        "esac", "function", "echo", "exit", "return", "export", "local", "source", "set",
        "unset", "read", "cd", "ls", "grep", "sed", "awk", "curl", "wget", "sudo", "rm", "mv",
        "cp", "mkdir", "touch", "chmod", "true", "false"),
    "sql" to setOf("select", "from", "where", "insert", "into", "values", "update", "set",
        "delete", "create", "table", "drop", "alter", "join", "left", "right", "inner", "outer",
        "on", "group", "by", "order", "having", "limit", "as", "and", "or", "not", "null",
        "distinct", "count", "sum", "avg", "min", "max", "exists", "between", "like", "in",
        "primary", "key", "foreign", "references", "index", "view"),
    "json" to setOf("true", "false", "null"),
    "yaml" to setOf("true", "false", "null", "yes", "no", "on", "off"),
    "yml" to setOf("true", "false", "null", "yes", "no", "on", "off"),
    "xml" to setOf("true", "false", "null"),
    "html" to setOf("true", "false", "null"),
)

private val STRING_RE = Regex("\"([^\"\\\\]|\\\\.)*\"|'([^'\\\\]|\\\\.)*'|`([^`\\\\]|\\\\.)*`")
private val NUMBER_RE = Regex("\\b\\d[\\d_.]*\\b")
private val KEYWORD_RE = Regex("\\b[A-Za-z_][A-Za-z0-9_]*\\b")
private val FUNC_RE = Regex("\\b[A-Za-z_][A-Za-z0-9_]*\\s*(?=\\()")

private fun commentPrefixFor(lang: String?): String? = when (lang?.lowercase()) {
    "kotlin", "java", "javascript", "typescript", "go", "sql", "c", "cpp", "csharp", "rust", "dart" -> "//"
    "python", "bash", "yaml", "yml", "toml", "ruby", "perl", "makefile" -> "#"
    "xml", "html", "css" -> "<!--"
    else -> null
}

private data class Token(val start: Int, val end: Int, val kind: Int)

/** 单行高亮：注释 > 字符串 > 关键字 > 数字 > 函数调用（按出现位置贪心，不重叠） */
private fun highlightLine(line: String, lang: String?, keywords: Set<String>, out: MutableList<Token>) {
    val tokens = mutableListOf<Token>()
    commentPrefixFor(lang)?.let { prefix ->
        line.indexOf(prefix).takeIf { it >= 0 }?.let { tokens += Token(it, line.length, 0) }
    }
    STRING_RE.findAll(line).forEach { tokens += Token(it.range.first, it.range.last + 1, 1) }
    if (keywords.isNotEmpty()) {
        KEYWORD_RE.findAll(line).forEach { m -> if (m.value in keywords) tokens += Token(m.range.first, m.range.last + 1, 2) }
    }
    NUMBER_RE.findAll(line).forEach { tokens += Token(it.range.first, it.range.last + 1, 3) }
    FUNC_RE.findAll(line).forEach { tokens += Token(it.range.first, it.range.last + 1, 4) }
    // 排序：位置优先；同位置按类型优先级（注释>字符串>关键字>数字>函数）
    tokens.sortWith(compareBy<Token> { it.start }.thenBy { it.end - it.start }.thenBy { it.kind })
    var cursor = 0
    for (t in tokens) {
        if (t.start < cursor) continue
        if (t.end > line.length) continue
        if (t.kind == 0) { // 注释吞到行尾
            out += t
            cursor = line.length
            break
        }
        out += t
        cursor = t.end
    }
}

/** 代码高亮 → AnnotatedString（diff 语言按行首字符特判） */
fun highlightCode(code: String, language: String?, dark: Boolean): androidx.compose.ui.text.AnnotatedString {
    val p = if (dark) ATOM_ONE_DARK else ATOM_ONE_LIGHT
    val lang = language?.lowercase()
    val result = androidx.compose.ui.text.AnnotatedString.Builder()
    val keywords = KEYWORDS[lang] ?: emptySet()
    val isDiff = lang == "diff"
    val lines = code.split("\n")
    for ((i, line) in lines.withIndex()) {
        if (i > 0) result.append("\n")
        if (isDiff) {
            val c = line.firstOrNull()
            val color = when {
                c == '+' -> p.diffAdd
                c == '-' -> p.diffDel
                line.startsWith("@@") -> p.diffMeta
                line.startsWith("diff ") || line.startsWith("index ") || line.startsWith("---") || line.startsWith("+++") -> p.comment
                else -> null
            }
            if (color != null) {
                result.withStyle(SpanStyle(color = color)) { result.append(line) }
            } else {
                result.append(line)
            }
            continue
        }
        val tokens = mutableListOf<Token>()
        highlightLine(line, lang, keywords, tokens)
        var pos = 0
        for (t in tokens) {
            if (t.start > pos) result.append(line.substring(pos, t.start))
            val color = when (t.kind) {
                0 -> p.comment
                1 -> p.string
                2 -> p.keyword
                3 -> p.number
                else -> p.function
            }
            result.withStyle(SpanStyle(color = color)) { result.append(line.substring(t.start, t.end)) }
            pos = t.end
        }
        if (pos < line.length) result.append(line.substring(pos))
    }
    return result.toAnnotatedString()
}

// ---- 代码块组件（高亮 + 截断展开 + 横向滚动） ----

private const val CODE_MAX_LINES = 8

@Composable
fun CodeBlock(code: String, language: String?) {
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    val palette = if (dark) ATOM_ONE_DARK else ATOM_ONE_LIGHT
    var expanded by remember { mutableStateOf(false) }
    val lines = code.split("\n")
    val collapsed = lines.size > CODE_MAX_LINES && !expanded
    val visible = if (collapsed) lines.take(CODE_MAX_LINES).joinToString("\n") else code

    Surface(
        color = palette.background,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().padding(start = 10.dp, end = 4.dp, top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Text(
                    language ?: "code",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.comment,
                )
                // T6 代码一键复制（2s 反馈）
                var copied by remember { mutableStateOf(false) }
                val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
                val scope = androidx.compose.runtime.rememberCoroutineScope()
                TextButton(onClick = {
                    clipboard.setText(androidx.compose.ui.text.AnnotatedString(code))
                    copied = true
                    scope.launch {
                        kotlinx.coroutines.delay(2000)
                        copied = false
                    }
                }) {                    Text(
                        if (copied) "已复制 ✓" else "复制",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.function,
                    )
                }
            }
            Box(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(
                    highlightCode(visible, language, dark),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = palette.default,
                )
            }
            if (collapsed) {
                TextButton(onClick = { expanded = true }) {
                    Text("展开全部 +${lines.size - CODE_MAX_LINES} 行", style = MaterialTheme.typography.labelSmall)
                }
            } else if (lines.size > CODE_MAX_LINES) {
                TextButton(onClick = { expanded = false }) {
                    Text("收起", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

// ---- 消息内容：文本段 + 代码块段交替渲染 ----

/**
 * 消息正文渲染：``` 围栏内的代码块用 CodeBlock（高亮+折叠），其余按文本。
 * 文本段沿用调用方 style/color；超长文本仍截断（maxLines）。
 */
@Composable
fun MessageContent(
    content: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val segments = remember(content) { splitCodeBlocks(content) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        segments.forEach { seg ->
            if (seg.isCode) {
                CodeBlock(seg.text, seg.language)
            } else if (seg.text.isNotBlank()) {
                // T6 Markdown 渲染（标题/列表/引用/表格/行内码/粗斜体）；超长纯文本限高防撑屏
                if (seg.text.lines().size > 60) {
                    Text(
                        seg.text,
                        style = style,
                        color = color,
                        maxLines = 15,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    MarkdownText(seg.text, style, color)
                }
            }
        }
    }
}
