package com.grandcouncil.remote.connection

import com.grandcouncil.remote.api.HttpClientFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI

/**
 * 连接测试四层诊断（P0-1 升级，借鉴 opencode-mobile diagnostics）：
 * 0. 设备网络（公网 204 探测——区分"设备没网"）
 * 1. 网络可达（TCP 连通，12s 交互快超时——坏 IP 快速失败）
 * 2. 认证（HTTP 401/403，区分"未发凭据"与"被拒"）
 * 3. 协议握手（GET /status 确认是 serve）
 *
 * 失败文案"人话化"：URL 格式错 / 设备无网 / 主机不可达 / TLS / 超时 / 认证失败 / 非 serve。
 */
class ConnectionTester {

    sealed class Step(val title: String) {
        data object DeviceNetwork : Step("设备网络")
        data object Network : Step("网络可达")
        data object Authentication : Step("认证")
        data object Handshake : Step("协议握手")
    }

    data class TestResult(
        val step: Step,
        val success: Boolean,
        val message: String,
        val detail: String? = null,
    )

    /** 交互式测试超时（12s，opencode-mobile 教训：业务超时 30s 是首启流失元凶） */
    private val interactiveTimeoutMs = 12_000L

    /**
     * 依次执行四层诊断，返回全部结果（失败即停止）。
     * [onResult] 供 UI 逐步展示。
     */
    suspend fun test(
        profile: ConnectionProfile,
        onResult: suspend (TestResult) -> Unit = {},
    ): List<TestResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<TestResult>()

        // ---- 第 0 层：设备网络 ----
        val deviceResult = testDeviceNetwork(profile)
        results += deviceResult
        onResult(deviceResult)
        if (!deviceResult.success) return@withContext results

        // ---- 第 1 层：TCP 连通 ----
        val tcpResult = testTcp(profile)
        results += tcpResult
        onResult(tcpResult)
        if (!tcpResult.success) return@withContext results

        // ---- 第 2+3 层：带凭据请求 GET /status ----
        val httpResult = testHandshake(profile)
        results += httpResult
        onResult(httpResult)

        results
    }

    /** 设备网络探针：公网 204（google generate_204；国内可用性由失败分类兜底） */
    private fun testDeviceNetwork(profile: ConnectionProfile): TestResult {
        val client = HttpClientFactory.createOkHttp(profile.copy(timeoutMs = interactiveTimeoutMs))
        val request = Request.Builder().url("https://www.gstatic.com/generate_204").get().build()
        return try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    TestResult(Step.DeviceNetwork, true, "设备网络正常")
                } else {
                    TestResult(Step.DeviceNetwork, false, "设备网络异常（HTTP ${response.code}）")
                }
            }
        } catch (e: Exception) {
            // 网络探测失败不阻断后续测试（可能是代理/区域限制），降级为"未验证"
            TestResult(Step.DeviceNetwork, true, "设备网络未验证（${e.message?.take(40) ?: "探测失败"}）")
        }
    }

    private fun testTcp(profile: ConnectionProfile): TestResult {
        val url = profile.normalizedBaseUrl()
        val parsed = try {
            URI(url)
        } catch (e: Exception) {
            return TestResult(Step.Network, false, "地址格式无效，请检查是否以 http(s):// 开头", e.message)
        }
        val host = parsed.host ?: return TestResult(Step.Network, false, "地址缺少主机名（示例：http://192.168.1.100:18789）")
        val port = if (parsed.port > 0) parsed.port else if (parsed.scheme == "https") 443 else 80
        val timeoutMs = interactiveTimeoutMs.coerceIn(1000L, 30_000L).toInt()

        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), timeoutMs)
                TestResult(Step.Network, true, "TCP 连接成功（$host:$port）")
            }
        } catch (e: java.net.ConnectException) {
            TestResult(
                Step.Network, false,
                "无法连接 $host:$port——检查 serve 是否启动、穿透隧道是否开启、地址端口是否正确",
            )
        } catch (e: java.net.SocketTimeoutException) {
            TestResult(
                Step.Network, false,
                "连接超时（${timeoutMs}ms）——主机可能不在线或防火墙拦截",
            )
        } catch (e: java.net.UnknownHostException) {
            TestResult(
                Step.Network, false,
                "主机名无法解析：$host——检查地址拼写（或试试 IP 直连）",
            )
        } catch (e: Exception) {
            TestResult(Step.Network, false, "连接失败：${e.message?.take(80) ?: e.javaClass.simpleName}")
        }
    }

    private suspend fun testHandshake(profile: ConnectionProfile): TestResult {
        val client = HttpClientFactory.createOkHttp(profile.copy(timeoutMs = interactiveTimeoutMs))
        val request = Request.Builder()
            .url(profile.normalizedBaseUrl() + "status")
            .header("Accept", "application/json")
            .get()
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                when {
                    response.code == 401 || response.code == 403 -> TestResult(
                        Step.Authentication, false,
                        if (profile.authMode == AuthMode.NONE) {
                            "服务端已开启认证（HTTP ${response.code}），但此连接未配置凭据——请在认证方式中选择 Token/密码"
                        } else {
                            "认证被拒绝（HTTP ${response.code}）——请检查 token/密码是否正确"
                        },
                    )

                    !response.isSuccessful -> TestResult(
                        Step.Handshake, false,
                        "HTTP ${response.code}——服务异常，请检查 serve 是否正常运行",
                    )

                    else -> {
                        val body = response.body?.string().orEmpty()
                        if (body.contains("\"label\"") || body.contains("\"plan\"")) {
                            TestResult(Step.Handshake, true, "连接成功，已确认 Reasonix serve")
                        } else {
                            TestResult(
                                Step.Handshake, false,
                                "端口有 HTTP 服务但不是 Reasonix serve（或端口不对）",
                            )
                        }
                    }
                }
            }
        } catch (e: java.net.SocketTimeoutException) {
            TestResult(Step.Handshake, false, "请求超时（${interactiveTimeoutMs}ms）——服务响应过慢或端口错误")
        } catch (e: javax.net.ssl.SSLException) {
            TestResult(
                Step.Handshake, false,
                "TLS 证书错误——试试 http:// 地址，或检查穿透域名的证书",
            )
        } catch (e: Exception) {
            val msg = e.message ?: e.javaClass.simpleName
            // unexpected end of stream：HTTP 流被中途切断，最常见是「端口上不是 Reasonix serve」
            //（如误填 80/其他端口），或穿透代理把握手掐断。给用户明确可行动的提示。
            if (msg.contains("unexpected end of stream") || msg.contains("EOF") ||
                e is java.io.EOFException
            ) {
                TestResult(
                    Step.Handshake, false,
                    "该端口不是 Reasonix serve——请确认用 18789 端口（当前地址会连到非 serve 服务）",
                )
            } else {
                TestResult(Step.Handshake, false, "请求失败：${msg.take(80)}")
            }
        }
    }
}
