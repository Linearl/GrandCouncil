package com.grandcouncil.remote.connection

import com.grandcouncil.remote.api.HttpClientFactory
import com.grandcouncil.remote.api.dto.CapabilitiesDto
import com.grandcouncil.remote.repository.SessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import retrofit2.HttpException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI

/**
 * 连接测试四层诊断 + 能力探测层（任务 7，借鉴 opencode-mobile diagnostics）：
 * 0. 设备网络（公网 204 探测——区分"设备没网"）
 * 1. 网络可达（TCP 连通，12s 交互快超时——坏 IP 快速失败）
 * 2. 认证（HTTP 401/403，区分"未发凭据"与"被拒"）
 * 3. 协议握手（GET /status 确认是 serve）
 * 4. 能力探测（GET /capabilities——能力清单展示 + 协议兼容警告；信息层，永不判失败）
 *
 * 失败文案"人话化"：URL 格式错 / 设备无网 / 主机不可达 / TLS / 超时 / 认证失败 / 非 serve。
 */
class ConnectionTester(
    // 复用底层 capabilities() API（任务 7）：与 540 派生入口同一探测通道，行为一致
    private val repository: SessionRepository = SessionRepository(),
) {

    sealed class Step(val title: String) {
        data object DeviceNetwork : Step("设备网络")
        data object Network : Step("网络可达")
        data object Authentication : Step("认证")
        data object Handshake : Step("协议握手")
        data object Capabilities : Step("能力探测")
    }

    data class TestResult(
        val step: Step,
        val success: Boolean,
        val message: String,
        val detail: String? = null,
        /** 任务 7：警示态——连接可用但能力受限（旧 serve / 探测失败 / 协议偏差）。
         *  计入通过（不阻断保存与向导），UI 用警示色与纯通过区分。 */
        val warn: Boolean = false,
    )

    /** 交互式测试超时（12s，opencode-mobile 教训：业务超时 30s 是首启流失元凶） */
    private val interactiveTimeoutMs = 12_000L

    /**
     * 依次执行四层诊断 + 能力探测，返回全部结果（失败即停止；能力探测层除外，见其注释）。
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
        if (!httpResult.success) return@withContext results

        // ---- 第 4 层：能力探测（GET /capabilities，信息层永不判失败） ----
        val capResult = testCapabilities(profile)
        results += capResult
        onResult(capResult)

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

    companion object {
        /**
         * GC 客户端当前讲的协议版本（对应 serve 端 serveProtocolVersion，capabilities.go：
         * 仅当既有路由发生不兼容变更时才随服务端一起递增）。
         */
        const val CLIENT_PROTOCOL = 1

        /** 已知能力 → 用户可读名（仅展示用；未知名原样显示，不臆造） */
        private val capabilityLabels = mapOf(
            "events" to "事件流",
            "runtime-states" to "运行状态",
            "history" to "历史",
            "context" to "上下文",
            "submit" to "发送",
            "provider-setup" to "模型供应商配置",
            "inbox" to "引导收件箱",
            "checkpoints" to "检查点",
            "child-session" to "派生子会话",
        )

        fun capabilityLabel(name: String): String = capabilityLabels[name] ?: name
    }

    /**
     * 第 4 层能力探测（任务 7）：复用底层 capabilities() API 拉取 serve 能力宣告。
     *
     * ⚠️「不反向禁用」保证（前置报告风险 5）：serve 的 capabilities 清单是「新增能力」
     * 不是「全量端点」（takeover/heartbeat/new 等既有端点不在清单内），因此本层：
     * 1. **永不返回 success=false**——旧 serve（无 /capabilities 路由 → 404/405）或探测
     *    失败只降级为 warn 展示，绝不阻断连接测试通过/保存（保存前必测与向导的放行
     *    条件都是 results.all { it.success }）；
     * 2. 探测结果只用于连接页展示与协议兼容警告；新能力入口的显隐 gate 在各自消费点
     *    （540 派生入口按 "child-session" 判定，后续 539 引导通道按 "inbox"），
     *    既有功能从不按本清单裁剪。
     */
    private suspend fun testCapabilities(profile: ConnectionProfile): TestResult {
        val result = repository.capabilitiesDetail(profile.copy(timeoutMs = interactiveTimeoutMs))
        val dto: CapabilitiesDto? = result.getOrNull()
        if (dto != null) {
            val names = dto.capabilities.joinToString("、") { capabilityLabel(it) }
            return when {
                // 服务端要求的最低协议高于本客户端：可能不兼容——警告但不拦截，
                // 既有端点是否可用以实际调用为准，探测层不下「禁用令」
                dto.minProtocol > CLIENT_PROTOCOL -> TestResult(
                    Step.Capabilities, true,
                    "服务端要求协议 ≥${dto.minProtocol}，本客户端为 v$CLIENT_PROTOCOL——" +
                        "部分新能力可能不可用，既有功能不受影响",
                    warn = true,
                )

                else -> TestResult(
                    Step.Capabilities, true,
                    if (names.isBlank()) "协议 v${dto.protocol} 兼容，服务端未宣告新增能力"
                    else "协议 v${dto.protocol} 兼容，服务端能力：$names",
                )
            }
        }
        // 探测失败：区分「旧 serve 无此路由」与其他异常，均降级为 warn（success=true）
        val httpCode = (result.exceptionOrNull() as? HttpException)?.code()
        return if (httpCode == 404 || httpCode == 405) {
            TestResult(
                Step.Capabilities, true,
                "服务端未提供能力宣告（旧版 serve）——新增能力入口将隐藏，既有功能不受影响",
                warn = true,
            )
        } else {
            val reason = result.exceptionOrNull()?.message?.take(60) ?: "未知原因"
            TestResult(
                Step.Capabilities, true,
                "能力探测未完成（$reason）——新增能力入口按不可用处理，既有功能不受影响",
                warn = true,
            )
        }
    }
}
