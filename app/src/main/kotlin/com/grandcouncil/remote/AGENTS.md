<!-- Parent: ../../../../../../AGENTS.md -->
<!-- Generated: 2026-08-14 | Updated: 2026-08-14 -->

# remote

## Purpose

GrandCouncil 的全部业务代码根包（`com.grandcouncil.remote`）：从远端 serve 连接（多隧道类型）到会话列表/聊天/审批的完整链路。架构核心是「多 agent 适配层」——UI 只依赖 `agent/AgentAdapter` 抽象接口，协议差异（Reasonix serve v1 26 端点协议已实测）隔离在适配器实现内。

## Key Files

| File | Description |
|------|-------------|
| `MainActivity.kt` | 唯一 Activity：Compose 入口，edge-to-edge，装配 MainScreen |

## Subdirectories

| Directory | Purpose |
|-----------|---------|
| `agent/` | 多 agent 适配层：能力字典 Feature + AgentAdapter 接口 + ReasonixAdapter 实现（见 `agent/AGENTS.md`） |
| `api/` | 网络层：Retrofit REST（26 端点）、OkHttp SSE、认证拦截器、CookieJar、协议 DTO（见 `api/AGENTS.md`） |
| `connection/` | 连接配置：多隧道类型（局域网/节点小宝/花生壳/Tailscale/frp/自定义）、三层连接测试、配置持久化（见 `connection/AGENTS.md`） |
| `model/` | 数据模型：RemoteMessage（含 tool use/审批）、RemoteSession、RemoteEvent（见 `model/AGENTS.md`） |
| `notify/` | 通知：订阅 serve SSE 事件流推送系统通知（见 `notify/AGENTS.md`） |
| `repository/` | 会话仓库：列表/历史只读加载、删除、模型切换（见 `repository/AGENTS.md`） |
| `security/` | 安全：生物识别锁 + Keystore 凭据加密（见 `security/AGENTS.md`） |
| `ui/` | Compose UI：底部三栏（会话/配置/向导）、设置、主题、组件（见 `ui/AGENTS.md`） |

## For AI Agents

### Working In This Directory

- 分层依赖方向：ui → repository/agent → api/connection → model；不要反向依赖（如 api 引用 ui）
- 新增协议端点/字段时：`api/ReasonixApi.kt` 加端点 → `api/dto/ServeDtos.kt` 加 DTO（容错解析）→ 适配层/仓库暴露能力 → UI 消费
- 会话占用（heldBy/409）语义只做提示不做强抢：serve 无 release/takeover 端点（2026-08-14 源码路由表确认）
- 新 agent 接入（如 MiMo Code v2）在 `agent/` 增加适配器实现，复用 connection/api 层

### Testing Requirements

- 网络层改动 → 模拟器实测连接测试三层诊断 + 会话列表 + 聊天流（scripts/dev-emulator.sh + ui-auto.py）
- 认证/凭据改动 → 重点回归 PasswordAuthInterceptor 与 SessionCookieJar 会话保持

### Common Patterns

- DTO `ignoreUnknownKeys` 容错；SSE 事件按 kind 分发，未知事件忽略（协议演进容错）
- 中文注释/UI 文案；`Feature` 能力字典驱动 UI 显隐

## Dependencies

### Internal

- `../..` 即 `app/src/main/`：AndroidManifest 权限、res 资源

### External

- Retrofit/OkHttp/okhttp-sse/kotlinx-serialization、Coroutines、Compose Material3、DataStore、Biometric

<!-- MANUAL: -->
