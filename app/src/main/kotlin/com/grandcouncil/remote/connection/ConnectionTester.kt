package com.grandcouncil.remote.connection

import com.grandcouncil.remote.api.HttpClientFactory
import com.grandcouncil.remote.connection.AuthMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.net.InetSocketAddress
import java.net.Socket

/**
 * 连接测试三层诊断：
 * 1. 网络可达（TCP 连通）
 * 2. 认证（HTTP 401/403 判断）
 * 3. 协议握手（GET /status 返回 JSON 确认是 serve）
 *
 * 失败提示不泄露 token 明文（第 4 条）。
 */
class ConnectionTester {

    sealed class Step(val title: String) {
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

    /**
     * 依次执行三层诊断，返回全部结果（含失败即停止）。
     * 回调 [onResult] 供 UI 逐步展示。
     */
    suspend fun test(
        profile: ConnectionProfile,
        onResult: suspend (TestResult) -> Unit = {},
    ): List<TestResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<TestResult>()

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

    private fun testTcp(profile: ConnectionProfile): TestResult {
        val url = profile.normalizedBaseUrl()
        val parsed = try {
            java.net.URI(url)
        } catch (e: Exception) {
            return TestResult(Step.Network, false, "地址格式无效", detail(e))
        }
        val host = parsed.host ?: return TestResult(Step.Network, false, "地址缺少主机名")
        val port = if (parsed.port > 0) parsed.port else if (parsed.scheme == "https") 443 else 80
        val timeoutMs = profile.timeoutMs.coerceIn(1000L, 30_000L).toInt()

        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), timeoutMs)
                TestResult(Step.Network, true, "TCP 连接成功（$host:$port）")
            }
        } catch (e: Exception) {
            TestResult(
                Step.Network, false,
                "无法连接 $host:$port——请检查穿透隧道是否开启、地址端口是否正确",
                detail(e),
            )
        }
    }

    private suspend fun testHandshake(profile: ConnectionProfile): TestResult {
        val client = HttpClientFactory.createOkHttp(profile)
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
                            "认证失败（HTTP ${response.code}）——服务端已开启认证，请配置 token/密码"
                        } else {
                            "认证失败（HTTP ${response.code}）——请检查认证方式与 token/密码是否正确"
                        },
                    )

                    !response.isSuccessful -> TestResult(
                        Step.Handshake, false,
                        "HTTP ${response.code}——服务异常，请检查 serve 是否正常运行",
                    )

                    else -> {
                        // 握手：响应体必须是 serve 的 status JSON
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
            TestResult(Step.Handshake, false, "请求超时（${profile.timeoutMs}ms）", detail(e))
        } catch (e: Exception) {
            TestResult(Step.Handshake, false, "请求失败", detail(e))
        }
    }

    private fun detail(e: Exception): String =
        e.message?.take(200) ?: e.javaClass.simpleName
}
