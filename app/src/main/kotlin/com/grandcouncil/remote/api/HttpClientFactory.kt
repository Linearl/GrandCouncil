package com.grandcouncil.remote.api

import com.grandcouncil.remote.connection.AuthMode
import com.grandcouncil.remote.connection.ConnectionProfile
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/**
 * OkHttp/Retrofit 客户端工厂。
 *
 * 认证（源码确认 internal/serve/auth.go）：
 * - token 模式：`?token=xxx` 查询参数（拦截器统一注入）
 * - password 模式：POST /login（表单 password 字段）换 `reasonix_session` cookie，
 *   由 PasswordAuthInterceptor + 共享 SessionCookieJar 处理（登录/续期/401 重登）
 *
 * 共享 CookieJar：进程内所有 client 共用，避免频繁登录触发 serve 的 /login 速率限制
 * （auth.go rateLimit：per-IP，一分钟内多次失败会 429）。
 *
 * CSRF：state-changing 请求必须带 application/json Content-Type，
 * 由 asConverterFactory + Retrofit @Body 保证。
 */
object HttpClientFactory {

    /** 进程级共享 cookie 存储（按 host 隔离，多连接互不串扰） */
    private val sharedCookieJar = SessionCookieJar()

    fun createOkHttp(profile: ConnectionProfile): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(profile.timeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(profile.timeoutMs, TimeUnit.MILLISECONDS)
            .writeTimeout(profile.timeoutMs, TimeUnit.MILLISECONDS)
            .cookieJar(sharedCookieJar)

        if (profile.authMode == AuthMode.TOKEN && profile.token.isNotBlank()) {
            builder.addInterceptor { chain ->
                val request = chain.request()
                val url = request.url.newBuilder()
                    .addQueryParameter("token", profile.token)
                    .build()
                chain.proceed(request.newBuilder().url(url).build())
            }
        }
        if (profile.authMode == AuthMode.PASSWORD && profile.password.isNotBlank()) {
            builder.addInterceptor(PasswordAuthInterceptor(profile, sharedCookieJar))
        }
        return builder.build()
    }

    fun createApi(profile: ConnectionProfile): ReasonixApi {
        val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
        return Retrofit.Builder()
            .baseUrl(profile.normalizedBaseUrl())
            .client(createOkHttp(profile))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ReasonixApi::class.java)
    }
}
