<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-08-14 | Updated: 2026-08-14 -->

# gradle

## Purpose

Gradle 构建基础设施：wrapper（固定 Gradle 版本）与版本目录（集中管理全部依赖版本）。是 `:app` 模块的依赖来源，任何依赖变更都发生在这里。

## Key Files

| File | Description |
|------|-------------|
| `libs.versions.toml` | 版本目录：versions/libraries/plugins 三段。2026-08 稳定组合：AGP 9.3.1 + built-in Kotlin 2.2.10 + Compose BOM 2026.08.00 |
| `wrapper/gradle-wrapper.jar` | Gradle wrapper 二进制（版本固定 9.5.0） |
| `wrapper/gradle-wrapper.properties` | wrapper 配置：distributionUrl 指向 Gradle 9.5.0 |

## Subdirectories

| Directory | Purpose |
|-----------|---------|
| `wrapper/` | Gradle wrapper 固定版本（勿手动替换，除非有意升级 Gradle） |

## For AI Agents

### Working In This Directory

- **新增依赖必须改 `libs.versions.toml`**：versions 段加版本、libraries 段加坐标、模块内用 `libs.xxx` 引用
- 版本组合约束（编译已验证）：AGP 9.3.1 要求 Gradle 9.5.0+；AGP 9 内置 Kotlin 2.2.10，**不要添加 `org.jetbrains.kotlin.android` 插件**（会被 AGP 9.3 拒绝）；compose/serialization 编译器插件版本必须与内置 Kotlin 版本一致（2.2.10）
- 本机 Gradle 下载依赖需走代理 127.0.0.1:10808；`local.properties` 里的 SDK 路径不入库

### Testing Requirements

- 修改版本目录后跑一次 `./gradlew :app:assembleDebug` 验证依赖解析与编译
- 版本升级遵循「一次只升一组相关依赖」原则，避免连锁失败

### Common Patterns

- 版本引用用 `version.ref = "xxx"` 保持单一来源
- 插件别名（plugins 段）与 libraries 段命名保持 kebab-case 一致

## Dependencies

### Internal

- 无

### External

- Gradle 9.5.0（wrapper 下载）、AGP 9.3.1、Kotlin 2.2.10（AGP 内置）

<!-- MANUAL: -->
