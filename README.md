# GrandCouncil

**手机端 AI agent 远程控制客户端**（Android / Kotlin / Compose）

在手机上远程连接 Reasonix serve，查看和使用远端会话。

## 功能特性

- 多远端连接配置：局域网 / 节点小宝 / 花生壳 / Tailscale / frp / 自定义
- 连接测试三层诊断：网络可达 → 认证 → 协议握手
- 会话列表展示（标题 / 轮数 / 当前会话 / 占用状态）
- 多 agent 适配层接口（AgentAdapter），协议差异在适配层内隔离

## 目录结构

- `app/`：Android App 源码（Kotlin/Compose）
- `docs/`：文档
- `temp/`：临时文件（不入库）

## 构建

环境：JDK 17+、Android SDK（compileSdk 37）、Gradle 9.5+（wrapper 自动下载）

```bash
./gradlew :app:assembleDebug
```

APK 输出：`app/build/outputs/apk/debug/app-debug.apk`

## 调试

### 安装到手机（adb）

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 局域网联调

1. 电脑启动 serve：`reasonix serve --addr 0.0.0.0:8787 --auth password`
2. 手机与电脑连接同一 WiFi
3. App「连接」页添加连接：`http://<电脑IP>:8787`，认证方式与 serve 配置一致
4. 「测试连接」通过后，切到「会话」页查看远端会话列表

## 许可证

[Apache License 2.0](LICENSE)
