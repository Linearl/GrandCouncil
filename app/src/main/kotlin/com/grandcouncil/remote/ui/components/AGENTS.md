<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-08-14 | Updated: 2026-08-14 -->

# components

## Purpose

通用 Compose 组件：会话消息内容的渲染全家桶（Markdown 解析渲染、消息气泡、工具调用/审批过程卡片）。被会话详情页（sessions/）复用。

## Key Files

| File | Description |
|------|-------------|
| `MarkdownText.kt` | Markdown 文本渲染（inline 样式：粗体/斜体/行内代码等，AnnotatedString 构建） |
| `MarkdownTable.kt` | Markdown 表格渲染（横向滚动、可点击单元格） |
| `MessageContent.kt` | 消息内容聚合渲染：文本 + Markdown + 代码块（横向滚动）+ 工具结果展示 |
| `ProcessCard.kt` | 工具调用/审批过程卡片：步骤折叠（只留尾部 2 步 + 「展开全部」）、animateContentSize 动画、加载态 shimmer |

## Subdirectories

（无）

## For AI Agents

### Working In This Directory

- 组件只做渲染不做业务：状态由上层（SessionDetailViewModel）传入
- Markdown 支持范围以「会话消息常见形态」为准，不追求完整规范（保持轻量）
- 代码块/长表格必须可横向滚动，避免撑爆布局

### Testing Requirements

- 模拟器实测：含表格/代码块/工具调用的消息渲染、步骤折叠交互、长内容滚动

### Common Patterns

- 纯 Composable + Modifier 参数化；主题色用 MaterialTheme 语义色

## Dependencies

### Internal

- `../../model/` — RemoteMessage 内容结构

### External

- Compose Foundation/Material3

<!-- MANUAL: -->
