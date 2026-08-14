<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-08-14 | Updated: 2026-08-14 -->

# scripts

## Purpose

开发辅助脚本：本机多项目 serve 生命周期管理（懒加载）、模拟器联调一键环境（serve + 模拟器 + 连通性验证）、模拟器 UI 自动化验证。这些脚本是测试环境的事实标准入口，App 的连接引导 UI 也直接引用它们的命令。

## Key Files

| File | Description |
|------|-------------|
| `serve-manager.sh` | 多项目 serve 管理器：`list|status|start <项目名>|stop <项目名>|start-all`。端口从 8788 起按 desktop-projects.json 清单顺序分配；日志在 `../temp/serve-logs/` |
| `dev-emulator.sh` | 一键联调：启动 reasonix serve（8787）→ 启动模拟器 → 验证宿主连通性 → 建测试会话 → 截图；模拟器访问宿主用 `10.0.2.2` |
| `ui-auto.py` | 模拟器 UI 自动化：adb + uiautomator 添加连接(10.0.2.2:8787) → 测试连接 → 会话列表验证；依赖 dev-emulator.sh 已就绪的环境 |

## Subdirectories

（无）

## For AI Agents

### Working In This Directory

- 脚本路径引用用项目相对路径；`serve-manager.sh` 依赖 `$APPDATA/reasonix/desktop-projects.json` 与 `$APPDATA/reasonix/.env`（DEEPSEEK_API_KEY），缺失时对应功能不可用
- 在 agent 自动化环境中调用 `serve-manager.sh start` 需保持进程组（`preserve_background_processes`），否则 serve 随调用 shell 退出被清理
- serve 停止/启动属于「破坏性操作边界」：`stop` 会 taskkill 端口对应 PID，执行前确认端口归属（netstat 核对）
- `ui-auto.py` 中 ADB 路径硬编码为 `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe`

### Testing Requirements

- 修改脚本后至少执行 `bash scripts/serve-manager.sh list` 验证语法与状态输出
- `dev-emulator.sh` 完整跑一遍验证 serve + 模拟器 + 连通性链路（耗时较长）

### Common Patterns

- bash 脚本用 `set -euo pipefail`；端口状态探测用 `netstat -ano | grep ":$port.*LISTEN"` + curl /status
- Python 脚本用 subprocess 封装 adb，输出统一 `[OK]/[FAIL]` 前缀便于阅读

## Dependencies

### Internal

- `../temp/serve-logs/` — serve 日志输出目录（gitignore）
- `../README.md` — 联调流程文档（脚本是其命令的自动化封装）

### External

- reasonix-cli（`%LOCALAPPDATA%\Programs\Reasonix\reasonix-cli.exe`）
- Android SDK platform-tools（adb）、Android 模拟器
- Python 3（ui-auto.py）

<!-- MANUAL: -->
