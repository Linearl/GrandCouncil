<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-08-14 | Updated: 2026-08-14 -->

# repository

## Purpose

会话仓库层：UI 与协议之间的数据存取门面。会话列表/历史只读加载（内部切换 serve 当前会话）、删除会话（持有中 409 提示）、模型切换（/model 斜杠命令）。

## Key Files

| File | Description |
|------|-------------|
| `SessionRepository.kt` | 仓库实现：按 profile 加载会话列表与历史、删除、模型切换；serve 状态（会话页服务器信息行） |

## Subdirectories

（无）

## For AI Agents

### Working In This Directory

- 会话删除是破坏性操作：走 serve `POST /delete-session`（name 即 session.id），持有中 409 向上抛出由 UI 提示
- 会话列表数据含 `-recovery-` 恢复副本时，serve 端已过滤（根治）；App 端渲染前再过滤兜底（name/path 含 `-recovery-` 跳过）
- 只读加载会切换 serve 当前会话，注意并发调用顺序

### Testing Requirements

- 删除会话后列表刷新；持有中删除 → 409 提示；recovery 副本不出现在列表

### Common Patterns

- StateFlow 暴露状态；错误统一转用户可读文案

## Dependencies

### Internal

- `../agent/` — AgentAdapter（协议调用）
- `../model/` — RemoteSession/RemoteMessage
- `../connection/` — ConnectionProfile/Store

### External

- Coroutines Flow

<!-- MANUAL: -->
