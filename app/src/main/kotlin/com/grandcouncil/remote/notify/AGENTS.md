<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-08-14 | Updated: 2026-08-14 -->

# notify

## Purpose

系统通知域：后台订阅 serve 的 SSE 事件流，把远端会话动态（新消息/审批请求等）转为 Android 系统通知。App 退到后台时仍能感知远端会话进展（A3 能力）。

## Key Files

| File | Description |
|------|-------------|
| `NotificationMonitor.kt` | 通知监视器：按 ConnectionProfile/Store 订阅各连接的 SseClient 事件流，事件驱动通知；CoroutineScope 生命周期管理 |
| `AppNotifications.kt` | 通知构建：channel 创建（Android 13+ POST_NOTIFICATIONS 运行时授权）、PendingIntent 回跳 MainActivity、分类通知 |

## Subdirectories

（无）

## For AI Agents

### Working In This Directory

- Android 13+ 需要运行时申请 POST_NOTIFICATIONS 权限（manifest 已声明），通知不显示先查授权
- 通知内容避免携带敏感信息（会话内容摘要级别即可）
- Monitor 生命周期：随 App 进程存活，重连/断流需容错（delay 重试）

### Testing Requirements

- 模拟器/真机：App 退后台后 serve 侧产生事件 → 验证通知出现与点击回跳

### Common Patterns

- 事件 kind 白名单过滤（与 `../api/dto/ServeDtos.kt` kind 常量对齐），未知 kind 忽略

## Dependencies

### Internal

- `../api/SseClient.kt` — 事件流来源
- `../connection/ConnectionStore.kt` — 订阅哪些连接
- `../model/RemoteEvent.kt` — 事件模型

### External

- AndroidX Core（NotificationCompat）、Coroutines

<!-- MANUAL: -->
