<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-08-14 | Updated: 2026-08-14 -->

# connection

## Purpose

远端连接配置域：定义连接类型（局域网/节点小宝/花生壳/Tailscale/frp/自定义）、连接档案（地址、认证方式、凭据）的持久化，以及「连接测试」三层诊断（网络可达 → 认证 → 协议握手）。

## Key Files

| File | Description |
|------|-------------|
| `ConnectionType.kt` | 连接类型枚举（含各类型默认配置语义） |
| `ConnectionProfile.kt` | 连接档案数据类：名称、baseUrl、auth_mode（none/token/password）、凭据；多连接列表 |
| `ConnectionStore.kt` | 档案持久化（DataStore），多连接增删改查 |
| `ConnectionTester.kt` | 三层连接测试：网络可达 → 认证 → 协议握手（GET /status 确认是 serve）；失败文案人话化 |
| `ConnectionGuide.kt` | 引导文案：各连接类型的 serve 启动命令/隧道配置提示（SERVE_START 常量被向导页引用） |

## Subdirectories

（无）

## For AI Agents

### Working In This Directory

- 凭据字段（token/password）必须经 `../security/CredentialCipher.kt` 加密后由 ConnectionStore 落盘，禁止明文持久化
- 新增连接类型：ConnectionType 加枚举 + ConnectionGuide 加引导文案 + ConnectionTester 按需调整诊断
- 测试文案「人话化」是本目录约定：错误信息面向手机用户，不用技术黑话

### Testing Requirements

- 连接测试三层诊断的每层失败分支都要有对应文案；模拟器实测成功/失败路径

### Common Patterns

- auth_mode 与 serve 配置一致（password 模式有 /login 速率限制，见 api 包）
- 连接档案 key 用 profileId，标签等本地数据见 ui/sessions/SessionLabelsStore

## Dependencies

### Internal

- `../api/` — HttpClientFactory/ReasonixApi（握手与认证调用）
- `../security/` — CredentialCipher（凭据加密）

### External

- DataStore Preferences

<!-- MANUAL: -->
