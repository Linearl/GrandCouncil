<!-- Generated: 2026-08-14 | Updated: 2026-08-14 -->

# GrandCouncil

## Purpose

**手机端 AI agent 远程控制客户端**（Android / Kotlin / Jetpack Compose）。在手机上远程连接 Reasonix serve（局域网 / 节点小宝 / 花生壳 / Tailscale / frp / 自定义隧道），查看、使用和接管远端 AI 会话。核心架构是「多 agent 适配层」：协议差异隔离在 `app/src/main/kotlin/com/grandcouncil/remote/agent/` 适配器内，UI 按能力字典（Feature）显隐功能入口。

## Key Files

| File | Description |
|------|-------------|
| `settings.gradle.kts` | 单模块项目（`:app`），插件仓库与依赖仓库配置（google/mavenCentral） |
| `build.gradle.kts` | 根构建脚本（AGP 9.3.1，插件全部走 version catalog） |
| `gradle.properties` | Gradle 配置（JVM 参数、AndroidX 等） |
| `gradle/libs.versions.toml` | 版本目录：2026-08 稳定依赖组合（compose BOM、retrofit/okhttp、serialization 等） |
| `README.md` | 项目简介、构建、安装与局域网联调说明 |
| `LICENSE` | Apache License 2.0 |
| `gradlew` / `gradlew.bat` | Gradle wrapper（Gradle 9.5.0） |

## Subdirectories

| Directory | Purpose |
|-----------|---------|
| `app/` | Android App 模块（全部源码与资源，见 `app/AGENTS.md`） |
| `scripts/` | 开发辅助脚本：serve 管理器、模拟器联调、UI 自动化（见 `scripts/AGENTS.md`） |
| `gradle/` | Gradle wrapper 与版本目录（见 `gradle/AGENTS.md`） |
| `docs/` | 文档目录（当前为空） |
| `temp/` | 临时文件与评测产物（`temp/ref/` 为第三方参考源码；已 gitignore，不入库） |

## For AI Agents

### Working In This Directory

- 代码/注释/文档惯例为**中文**，保持一致性；标识符与 API 名称保持英文
- **不要修改 `temp/` 下的参考源码**（`temp/ref/opencode-mobile`、`temp/ref/paseo`、`temp/ref/rikkahub-src` 等是第三方对照基准，只读）；`temp/report/` 是评测报告产物
- 构建环境要点（已验证组合）：AGP 9.3.1 + Gradle 9.5.0（wrapper）+ **built-in Kotlin 2.2.10**（AGP 9 内置，`org.jetbrains.kotlin.android` 插件被 AGP 9.3 拒绝；compose/serialization 插件版本必须等于内置 Kotlin 版本）；compileSdk 37 / minSdk 26 / targetSdk 37
- 本机 Android SDK 在 `%LOCALAPPDATA%\Android\Sdk`；Gradle 依赖下载需走代理 127.0.0.1:10808（`GRADLE_OPTS`/代理环境变量）
- `README.md` 不得引用内部文档（如 `temp/report/`）——用户明确要求

### Testing Requirements

- 编译验证：`./gradlew :app:assembleDebug`（需 JDK 17+ 与 Android SDK 环境变量）
- 模拟器联调：`bash scripts/dev-emulator.sh`（起 serve → 起模拟器 → 连通性验证 → 截图）；UI 自动化 `python scripts/ui-auto.py`
- 多 serve 管理：`bash scripts/serve-manager.sh list|start|stop <项目名>`（一个 serve = 一个项目，端口从 8788 起分配）

### Common Patterns

- 网络层走 Retrofit + OkHttp + kotlinx-serialization；SSE 用 okhttp-sse
- 凭据（token/password）用 Android Keystore AES/GCM 加密落盘（`security/CredentialCipher.kt`），CookieJar 进程内共享
- DTO 统一 `ignoreUnknownKeys` 容错——serve 协议随版本演进
- 会话持有冲突（serve 返回 409）向上抛出由 UI 提示，不在客户端强抢

## Dependencies

### Internal

- 无内部模块依赖（单模块 `:app`）

### External

- AGP 9.3.1 / Gradle 9.5.0 / built-in Kotlin 2.2.10
- Jetpack Compose BOM 2026.08.00 + Material3 + Navigation Compose 2.9.8
- Retrofit 3.0.0 / OkHttp 5.4.0（含 okhttp-sse）/ kotlinx-serialization-json 1.11.0
- Kotlinx Coroutines 1.11.0 / DataStore Preferences 1.2.1 / AndroidX Biometric 1.1.0
- 运行时依赖服务：Reasonix serve（局域网/隧道连接的目标）

<!-- MANUAL: 自定义项目说明可加在此行下方（重新生成时保留） -->
