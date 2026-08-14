<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-08-14 | Updated: 2026-08-14 -->

# agent

## Purpose

多 agent 适配层：把「远端 AI agent serve 协议」抽象成统一接口，UI 只依赖这里的抽象，具体协议差异（Reasonix serve v1，26 端点协议已实测）在适配器实现内隔离。当前唯一实现是 Reasonix；MiMo Code（opencode 系）为 v2 接入目标。

## Key Files

| File | Description |
|------|-------------|
| `AgentAdapter.kt` | 核心抽象：Feature 能力字典（STREAMING/APPROVAL/TAKEOVER 等，UI 按 supports(feature) 显隐入口）+ AgentAdapter 接口（会话/消息/事件流/审批） |
| `AgentAdapterFactory.kt` | 工厂：按连接 profile 的 agent 类型创建适配器实例；未知 agent 静默回退 |
| `ReasonixAdapter.kt` | Reasonix serve 适配实现（v1）：A1 能力字典、26 端点协议映射；serve 无 release/takeover 端点，TAKEOVER 不展示入口 |

## Subdirectories

（无）

## For AI Agents

### Working In This Directory

- 新增 agent 支持 = 新增 Adapter 实现类 + Factory 注册，**不改 UI 层**
- 能力差异用 Feature 枚举表达（能力字典驱动显隐），不要用 try/catch 探测
- 未知 agent/未知事件静默回退，禁止崩溃

### Testing Requirements

- 适配层改动后回归：会话列表加载、聊天 SSE 流、审批回复、409 占用提示

### Common Patterns

- 接口方法返回 Flow（事件流）；状态通过 RemoteEvent 承载
- 协议端点映射集中在 ReasonixAdapter，注释标注 serve 源码路由依据

## Dependencies

### Internal

- `../model/` — RemoteSession/RemoteMessage/RemoteEvent
- `../api/` — ReasonixApi/SseClient 具体协议调用

### External

- （无直接外部依赖）

<!-- MANUAL: -->
