<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-08-14 | Updated: 2026-08-14 -->

# app

## Purpose

GrandCouncil 的 Android 应用模块（`:app`），包含全部 Kotlin 源码与 Android 资源。核心功能在 `src/main/kotlin/com/grandcouncil/remote/`：远端 serve 连接管理、会话列表/聊天（SSE 流式）、审批、通知、生物识别锁与主题体系。UI 采用 Jetpack Compose + Material3，底部三栏导航（会话/配置/向导）。

## Key Files

| File | Description |
|------|-------------|
| `build.gradle.kts` | 模块构建配置：namespace/applicationId `com.grandcouncil.remote`、compileSdk 37、minSdk 26、targetSdk 37、依赖均来自 version catalog |
| `proguard-rules.pro` | R8/ProGuard 规则（release 混淆配置） |
| `src/main/AndroidManifest.xml` | 权限声明（INTERNET、网络状态、通知、录音）与 application 配置（networkSecurityConfig 允许明文 HTTP 局域网直连） |

## Subdirectories

| Directory | Purpose |
|-----------|---------|
| `src/main/kotlin/com/grandcouncil/remote/` | 全部业务代码：适配层/网络/连接/UI（见 `src/main/kotlin/com/grandcouncil/remote/AGENTS.md`） |
| `src/main/res/` | Android 资源：`values/`（strings、themes）、`drawable/`（通知图标）、`xml/`（backup 规则、明文网络配置） |

## For AI Agents

### Working In This Directory

- 全部 UI 与业务代码位于 `src/main/kotlin/com/grandcouncil/remote/`，新增代码按现有包结构落位（agent/api/connection/model/notify/repository/security/ui）
- 依赖版本一律通过 `../gradle/libs.versions.toml` 管理，不要硬编码版本号
- 新增网络端点/协议字段时同步更新 `api/dto/ServeDtos.kt`（保持 `ignoreUnknownKeys` 容错）与 `api/ReasonixApi.kt` 端点注释
- UI 文案用中文（strings.xml 中集中管理）；主题色沿用 `ui/theme/Theme.kt` 的 reasonix 语义色

### Testing Requirements

- 编译：项目根执行 `./gradlew :app:assembleDebug`（本目录下执行需注意工作目录）
- 模拟器联调：`bash ../scripts/dev-emulator.sh`；UI 自动化 `python ../scripts/ui-auto.py`
- 修改网络层后重点回归：连接测试三层诊断（网络可达→认证→协议握手）与会话列表/聊天流

### Common Patterns

- Retrofit + OkHttp + kotlinx-serialization；SSE 走 okhttp-sse（`api/SseClient.kt`）
- ViewModel + StateFlow 状态管理；导航用 Navigation Compose
- 凭据经 `security/CredentialCipher.kt`（Keystore AES/GCM）加密后落盘

## Dependencies

### Internal

- `../gradle/` — 版本目录（libs.versions.toml）与本项目构建工具链

### External

- Jetpack Compose BOM 2026.08.00 / Material3 / Navigation Compose 2.9.8
- Retrofit 3.0.0 / OkHttp 5.4.0 / kotlinx-serialization-json 1.11.0
- Coroutines 1.11.0 / DataStore 1.2.1 / Biometric 1.1.0 / core-ktx 1.19.0

<!-- MANUAL: -->
