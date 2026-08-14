<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-08-14 | Updated: 2026-08-14 -->

# ui

## Purpose

Compose UI 层：底部三栏导航（会话/配置/向导）+ 设置页 + 主题体系 + 通用组件。对标 reasonix desktop/TUI 的体验（V3 友好引导型、多主题、卡片密度可配置）。

## Key Files

| File | Description |
|------|-------------|
| `MainScreen.kt` | 主界面：底部三栏（会话/配置/向导）+ 顶部栏，导航装配 |
| `AppPreferences.kt` | 应用偏好（DataStore）：主题、密度等本地设置 |
| `theme/Theme.kt` | 主题体系：对标 reasonix TUI 8 套主题取 4 套代表（每套含明暗），默认暖色 sandstone/graphite；状态色语义沿用 reasonix（见 `theme/` 说明） |

## Subdirectories

| Directory | Purpose |
|-----------|---------|
| `components/` | 通用组件：Markdown 渲染、消息内容、进程卡片（见 `components/AGENTS.md`） |
| `config/` | 配置页（ConfigScreen.kt）：App 级设置入口 |
| `connections/` | 连接管理页：列表/表单/测试（见 `connections/AGENTS.md`） |
| `demo/` | 演示聊天页（DemoChatScreen.kt）：无 serve 时的 UI 演示 |
| `export/` | 会话导出（SessionExporter.kt）：序列化导出会话内容 |
| `sessions/` | 会话列表 + 会话详情（聊天/审批），核心页面（见 `sessions/AGENTS.md`） |
| `settings/` | 设置页（SettingsScreen.kt）：主题/密度/生物识别锁开关等 |
| `theme/` | 主题预设（Theme.kt 内 ThemePreset/DensityPreset） |
| `wizard/` | 首次使用向导（WizardScreen.kt）：4 步引导（连接方式→地址认证→启动 serve→测试保存），带步进器 |

## For AI Agents

### Working In This Directory

- UI 文案中文；对照参考（只读）：`temp/ref/rikkahub-src/`（原版源码查「美」的实现）、`temp/report/`（评测基线报告）
- 敏感工具（审批类）在 YOLO 模式下也强制显式审批（serve RequiresFreshHumanApprovalTool 语义）
- 新增页面：遵循「Screen + ViewModel + StateFlow」模式，注册进 MainScreen 导航

### Testing Requirements

- 模拟器实测（scripts/dev-emulator.sh + ui-auto.py）；UI 改动跑 `./gradlew :app:assembleDebug`
- 主题/密度改动检查明暗两模式与低密度布局

### Common Patterns

- 卡片密度可配置（默认清爽）；Material3 + 主题预设；animateContentSize/滚动跟随（对标 rikkahub 实现）

## Dependencies

### Internal

- `../repository/`、`../agent/`、`../connection/`、`../model/`、`../security/`（锁开关）、`../api/`（经 repository）

### External

- Compose Material3、Navigation Compose、DataStore

<!-- MANUAL: -->
