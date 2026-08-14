<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-08-14 | Updated: 2026-08-14 -->

# model

## Purpose

纯数据模型层：与 serve 协议无关的领域模型，供适配层/仓库/UI 三方共享。M1 先定结构、M2 起被聊天功能使用。

## Key Files

| File | Description |
|------|-------------|
| `RemoteMessage.kt` | 消息模型：role、content、toolCalls（tool use）、reasoning（推理内容）、等待审批状态 |
| `RemoteSession.kt` | 会话模型：name（serve 返回，无 .jsonl 后缀，作为稳定 id）、path、title、turns、lease 归属（ME/OTHER/FREE） |
| `RemoteEvent.kt` | SSE 事件序列模型（实测）：按 kind 区分事件类型，供 notify/repository/ui 消费 |

## Subdirectories

（无）

## For AI Agents

### Working In This Directory

- 模型尽量保持「与协议无关」：serve 原始字段在 `../api/dto/` 解析后再映射到这里
- 新增事件类型时同步检查 `../api/dto/ServeDtos.kt` 的 kind 常量与 `../notify/` 的订阅逻辑

### Testing Requirements

- 模型字段变更影响面大（全链路共享），改动后跑编译 + 模拟器回归

### Common Patterns

- 不可变 data class；默认参数避免破坏性变更

## Dependencies

### Internal

- （无——本层不依赖其他包）

### External

- （无）

<!-- MANUAL: -->
