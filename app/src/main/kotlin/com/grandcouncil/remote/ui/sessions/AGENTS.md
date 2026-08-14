<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-08-14 | Updated: 2026-08-14 -->

# sessions

## Purpose

核心页面域：会话列表（多 serve 聚合、占用状态、删除）与会话详情（聊天 SSE 流、Markdown 渲染、工具过程卡片、审批回复）。App 体验的主战场。

## Key Files

| File | Description |
|------|-------------|
| `SessionListScreen.kt` | 会话列表 UI：标题/轮数/当前会话/占用状态（heldBy）、按 profile 分组、删除入口 |
| `SessionListViewModel.kt` | 列表状态：多 serve 在线状态聚合（serveInfoByProfile）、删除会话（持有中 409 提示）、recovery 副本过滤兜底 |
| `SessionDetailScreen.kt` | 会话详情 UI：消息流（文本/工具/推理折叠）、输入栏（毛玻璃+IME 贴角）、滚动跟随、审批模式徽标 |
| `SessionDetailViewModel.kt` | 详情状态：消息订阅（SSE）、发送/审批（allow/session/persist 对应 /approve）、审批模式三档（ask/auto/yolo）、O1 降级（409 提示用户先释放） |
| `SessionLabelsStore.kt` | 会话标签本地存储：key 格式 "profileId:sessionId"（serve 无标签概念），DataStore 持久化 |

## Subdirectories

（无）

## For AI Agents

### Working In This Directory

- 敏感工具审批在 YOLO 模式也强制显式审批（serve RequiresFreshHumanApprovalTool）；审批回复语义 allow/session/persist
- 滚动跟随对标 rikkahub（`temp/ref/rikkahub-src/` 只读参考）：生成中+用户未滚动+位于底部才自动滚动，用户停止滚动 1.5s 内不抢
- 输入栏/消息动效参考 `temp/report/` 评测基线（P0/P1/P2 分级差距）
- 会话被其他进程占用时 serve 返回 409 → 提示用户在占用方释放（无 release/takeover 端点）

### Testing Requirements

- 模拟器实测：会话列表→详情→发送→流式输出→审批回复→删除全链路
- 推理/工具阶段滚动跟随（用户反馈过的「没有滚动效果」场景重点回归）

### Common Patterns

- Screen + ViewModel（StateFlow）；标签本地化（serve 无标签概念）；markdown 渲染复用 components/

## Dependencies

### Internal

- `../../repository/SessionRepository.kt`、`../../agent/`、`../../model/`、`../components/`（Markdown/ProcessCard）

### External

- Compose Material3、Navigation、DataStore（标签）

<!-- MANUAL: -->
