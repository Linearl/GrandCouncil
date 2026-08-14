<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-08-14 | Updated: 2026-08-14 -->

# dto

## Purpose

serve 协议数据结构（v1.21.3+ 源码确认：`internal/serve/serve.go` sessions/status handler 等）。与 `model/` 领域模型的区别：这里是「协议原始形态」，解析后映射到领域模型。

## Key Files

| File | Description |
|------|-------------|
| `ServeDtos.kt` | 统一 DTO 文件：GET /sessions 响应元素（sessionEntry）、GET /status（宽松解析只取握手特征字段）、GET /history 响应元素、SSE 事件（kind 常量经 serve Web UI 解析确认）、GET /models 响应元素 |

## Subdirectories

（无）

## For AI Agents

### Working In This Directory

- **统一 `ignoreUnknownKeys` 容错**：serve 协议随版本演进，客户端做 DTO 容错（风险对策）
- 事件 kind 常量是本协议演进的关键锚点，新增/变更需同步 `../model/RemoteEvent.kt` 与 `../notify/` 订阅
- 字段命名与 serve 返回保持 camelCase 一致，用 @SerialName 映射

### Testing Requirements

- 用 serve 实测 JSON（temp/ 下历史会话抓包 JSON 可参考）验证解析；新字段加测试样例

### Common Patterns

- 单个文件集中管理全部 DTO；宽松解析 + 显式 @SerialName

## Dependencies

### Internal

- 被 `../ReasonixApi.kt`、`../SseClient.kt` 使用；映射到 `../../model/`

### External

- kotlinx-serialization-json

<!-- MANUAL: -->
