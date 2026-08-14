package com.grandcouncil.remote.api

import com.grandcouncil.remote.connection.AuthMode
import com.grandcouncil.remote.connection.ConnectionProfile
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.util.concurrent.TimeUnit

/**
 * password 模式登录拦截器（协议实测 + 源码确认 internal/serve/auth.go）：
 *
 * - `POST /login` 是**表单**（application/x-www-form-urlencoded，字段 password，非 JSON）
 * - 登录成功：302 + `Set-Cookie: reasonix_session=<签名值>`（30 天）
 * - 后续请求凭 cookie 通过 authGate
 *
 * 行为：
 * 1. 请求前未持有会话 cookie → 用独立 loginClient（防拦截器递归）同步登录
 * 2. 响应 401 且刚才还持有会话（服务端会话失效）→ 清 cookie 重登录后重试一次；
 *    密码错误（从未持有会话）→ 不重试，原样返回 401 让上层提示
 */
class PasswordAuthInterceptor(
    private val profile: ConnectionProfile,
    private val cookieJar: SessionCookieJar,
) : Interceptor {

    private val loginClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .cookieJar(cookieJar)
            .connectTimeout(profile.timeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(profile.timeoutMs, TimeUnit.MILLISECONDS)
            .writeTimeout(profile.timeoutMs, TimeUnit.MILLISECONDS)
            .build()
    }

    private val lock = Any()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (profile.authMode != AuthMode.PASSWORD || profile.password.isBlank()) {
            return chain.proceed(request)
        }
        val host = request.url.host

        if (!cookieJar.hasSession(host)) {
            synchronized(lock) {
                if (!cookieJar.hasSession(host)) doLogin(request.url)
            }
        }

        val response = chain.proceed(request)
        if (response.code == 401 && cookieJar.hasSession(host)) {
            // 曾持有会话但被拒：会话失效 → 清空重登 → 重试一次
            response.close()
            val relogged = synchronized(lock) {
                cookieJar.clear(host)
                doLogin(request.url)
            }
            return if (relogged) chain.proceed(request) else {
                // 重登失败：构造 401 响应返回（避免裸奔重试）
                Response.Builder()
                    .request(request)
                    .protocol(okhttp3.Protocol.HTTP_1_1)
                    .code(401)
                    .message("Unauthorized")
                    .body(okhttp3.ResponseBody.create(null, ""))
                    .build()
            }
        }
        return response
    }

    private fun doLogin(baseUrl: HttpUrl): Boolean {
        return try {
            // 注意：不能用 addPathSegments（会把请求路径 /status 追加成 /status/login）
            val loginUrl = profile.normalizedBaseUrl().trimEnd('/') + "/login"
            val form = FormBody.Builder().add("password", profile.password).build()
            val req = Request.Builder().url(loginUrl).post(form).build()
            loginClient.newCall(req).execute().use { resp ->
                resp.code == 302 && cookieJar.hasSession(baseUrl.host)
            }
        } catch (e: Exception) {
            false
        }
    }
}
