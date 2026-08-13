package com.grandcouncil.remote.connection

import kotlinx.serialization.Serializable

/**
 * 穿透方式预设（计划书 3.5）：内置五种方式 + 自定义。
 * 对 App 透明——差异仅在 URL 与帮助文案；预设为「配置向导 + 诊断提示」服务。
 */
@Serializable
enum class ConnectionType(val label: String, val exampleUrl: String) {
    /** 局域网直连（开发期主力）：http://192.168.x.x:8787；模拟器访问宿主机用 10.0.2.2 */
    LAN("局域网直连", "http://192.168.1.100:8787"),
    /** 节点小宝（玩物科技托管隧道，.iepose.cn 域名） */
    NODE_XIAOBAO("节点小宝", "https://xxx.iepose.cn"),
    /** 花生壳（贝锐托管隧道，.oray.com 域名） */
    ORAY("花生壳", "https://xxx.oray.com"),
    /** Tailscale（WireGuard P2P 组网，100.x 网段） */
    TAILSCALE("Tailscale", "http://100.x.x.x:8787"),
    /** frp 自建（VPS frps + 电脑 frpc） */
    FRP("frp 自建", "http://VPS-IP:6787"),
    /** 任意 HTTPS 地址（cloudflared/ngrok/其他） */
    CUSTOM("自定义", "https://your-host"),
}
