<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-08-14 | Updated: 2026-08-14 -->

# security

## Purpose

本地安全域（A4 能力）：设备级生物识别锁（打开 App/从后台返回时验证）与凭据加密（serve token/password 落盘保护）。

## Key Files

| File | Description |
|------|-------------|
| `BiometricLock.kt` | 生物识别锁：设备强生物识别（指纹/人脸）或设备凭据；开关默认关闭（设置页可开启）；需 FragmentActivity 上下文 |
| `CredentialCipher.kt` | 凭据加密：Android Keystore AES/GCM（minSdk 26 原生支持）；token/password 加密后落盘；卸载重装后密钥消失（凭据自动清除） |

## Subdirectories

（无）

## For AI Agents

### Working In This Directory

- **所有落盘的凭据必须经过 CredentialCipher**，禁止明文存储（见 connection/ConnectionStore）
- Keystore 密钥不可导出：备份/迁移场景按「凭据失效需重新输入」设计
- 生物识别失败/不可用必须优雅降级（设备凭据或提示），不能卡死 UI

### Testing Requirements

- 凭据加解密往返测试；模拟器（无生物识别硬件）验证降级路径
- 卸载重装后凭据清除行为

### Common Patterns

- 单例 object + Context 注入；加密结果 Base64 存储

## Dependencies

### Internal

- 被 `../connection/`（凭据存储）与 `../ui/settings/`（锁开关）使用

### External

- AndroidX Biometric 1.1.0、Android Keystore（java.security）

<!-- MANUAL: -->
