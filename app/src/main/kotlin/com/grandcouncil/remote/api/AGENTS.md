<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-08-14 | Updated: 2026-08-14 -->

# api

## Purpose

远端 serve 的网络层：REST（Retrofit，26 端点）、SSE 事件流（okhttp-sse）、认证（password/token 拦截器 + CookieJar）、协议 DTO 解析。所有端点语义均有 serve 源码路由依据（`internal/serve/serve.go`、`internal/serve/auth.go`、`internal/event/event.go`）。

## Key Files

| File | Description |
|------|-------------|
| `ReasonixApi.kt` | Reasonix serve REST 接口定义（附录 A：26 端点）；POST /new 需空 body 确保 JSON Content-Type（CSRF 守卫要求） |
| `SseClient.kt` | SSE 订阅 GET /events（keepalive `: ping`），解析为 ServeEventDto，未知事件容错忽略 |
| `HttpClientFactory.kt` | OkHttp/Retrofit 构建：共享 CookieJar、认证拦截器装配 |
| `PasswordAuthInterceptor.kt` | password 模式登录拦截器（避免频繁登录触发 /login 速率限制） |
| `SessionCookieJar.kt` | 认证 cookie 按 host 保存（多 serve 多连接互不串扰），Path=/ 全站生效 |
| `dto/ServeDtos.kt` | 协议 DTO（sessions/status/history/model 响应 + SSE 事件），统一 ignoreUnknownKeys 容错（见 `dto/AGENTS.md`） |

## Subdirectories

| Directory | Purpose |
|-----------|---------|
| `dto/` | serve 协议数据结构（见 `dto/AGENTS.md`） |

## For AI Agents

### Working In This Directory

- 新端点流程：ReasonixApi 加方法 → dto 加类型（`ignoreUnknownKeys`）→ 上层消费；同步更新端点注释
- 认证语义（auth_mode = none|token|password）见 PasswordAuthInterceptor 与 ConnectionProfile；公网暴露必须开启认证
- 共享 CookieJar 保持进程内单例，勿在调用处新建 client（会绕过登录态与限流保护）

### Testing Requirements

- 连接测试三层诊断（ConnectionTester）回归：网络可达 → 认证 → 协议握手
- 模拟器实测会话列表/聊天/审批，确认 SSE 事件流完整

### Common Patterns

- DTO 宽松解析（ignoreUnknownKeys），协议演进容错
- SSE 事件按 kind 分发（事件 kind 常量见 ServeDtos）

## Dependencies

### Internal

- `../connection/` — ConnectionProfile（认证方式/地址）
- `../model/` — RemoteEvent 等上层模型

### External

- Retrofit 3.0.0 / OkHttp 5.4.0 / okhttp-sse / kotlinx-serialization-json

<!-- MANUAL: -->
