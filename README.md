# GrandCouncil

**手机端多 agent 编排客户端**（Android，Kotlin/Compose）。

> 军机处 / Grand Council —— 统一指挥多个 AI agent 的中枢。
> 路线：远程控制起步，终局是多设备多 agent 编排。

## 路线图

- **v1**：远程控制 Reasonix（serve 协议，HTTP + SSE）——会话列表 / 聊天 / 审批 / 历史
- **v2**：接入 MiMo Code（小米，opencode 系协议）——多 agent 适配层
- **v3**：编排层——手机端编排多设备多会话（并行 / 接力 / 汇总）

## 特性

- 多远端连接配置：局域网 / 节点小宝 / 花生壳 / Tailscale / frp / 自定义
- 连接测试三层诊断：网络可达 → 认证 → 协议握手
- 会话列表展示（含会话占用状态）
- 面向国产 harness / 国产模型生态（Reasonix、MiMo Code）

## 构建

要求：JDK 17+、Android SDK（compileSdk 37）、Gradle 9.5+（wrapper 自动下载）。

```bash
./gradlew :app:assembleDebug
```

APK 输出：`app/build/outputs/apk/debug/app-debug.apk`

## 许可证

[Apache License 2.0](LICENSE)
