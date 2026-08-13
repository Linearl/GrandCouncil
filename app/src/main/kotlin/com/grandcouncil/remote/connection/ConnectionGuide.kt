package com.grandcouncil.remote.connection

/**
 * 各穿透方式内置配置向导（速查，面向新用户）。
 * 穿透服务本身由用户自行选择/搭建（非目标：App 不内嵌 frpc/Tailscale 客户端）。
 */
object ConnectionGuide {

    /** 电脑端通用第一步 */
    const val SERVE_START = "电脑端启动服务：reasonix serve --addr 0.0.0.0:8787 --auth password"

    fun stepsFor(type: ConnectionType): List<String> = when (type) {
        ConnectionType.LAN -> listOf(
            SERVE_START,
            "手机与电脑连接同一 WiFi",
            "电脑上 ipconfig 查本机局域网 IP",
            "App 中填入 http://<电脑IP>:8787（模拟器访问宿主机填 http://10.0.2.2:8787）",
        )

        ConnectionType.NODE_XIAOBAO -> listOf(
            SERVE_START,
            "电脑安装节点小宝客户端并用同一账号登录",
            "创建隧道：内网地址 127.0.0.1:8787",
            "App 中填入隧道地址 https://xxx.iepose.cn",
        )

        ConnectionType.ORAY -> listOf(
            SERVE_START,
            "电脑安装花生壳客户端",
            "添加映射：内网主机 127.0.0.1，端口 8787",
            "App 中填入映射地址 https://xxx.oray.com",
        )

        ConnectionType.TAILSCALE -> listOf(
            SERVE_START,
            "电脑与手机安装 Tailscale 并登录同一账号",
            "电脑上 tailscale ip 查 100.x 网段地址",
            "App 中填入 http://100.x.x.x:8787（组网已加密，无需 HTTPS）",
        )

        ConnectionType.FRP -> listOf(
            SERVE_START,
            "租 VPS 部署 frps，电脑运行 frpc 映射本机 8787（配置模板见 frp 官方文档）",
            "VPS 安全组放行 frps 端口与映射端口",
            "App 中填入 http://VPS-IP:映射端口（建议 frp 开 TLS + serve 开认证）",
        )

        ConnectionType.CUSTOM -> listOf(
            SERVE_START,
            "用任意隧道（cloudflared/ngrok/自建反代）映射本机 8787",
            "公网暴露必须开启 serve 认证（禁止 auth_mode=none 暴露公网）",
            "App 中填入隧道提供的地址",
        )
    }

    /** 推荐组合 */
    const val RECOMMENDATION =
        "推荐：局域网直连（开发期）→ 外出用节点小宝/花生壳/Tailscale → 自控用 frp（自建 VPS）"
}
