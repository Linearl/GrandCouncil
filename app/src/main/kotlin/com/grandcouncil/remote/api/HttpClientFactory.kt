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
 * - password 模式：POST /login 换 session cookie（M2 细化，见 SseClient/ReasonixApi 注释）
 *
 * CSRF（计划书 2.3）：state-changing 请求必须带 application/json Content-Type，
 * 由 asConverterFactory + Retrofit @Body 保证。
 */
object HttpClientFactory {

    fun createOkHttp(profile: ConnectionProfile): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(profile.timeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(profile.timeoutMs, TimeUnit.MILLISECONDS)
            .writeTimeout(profile.timeoutMs, TimeUnit.MILLISECONDS)
            .addInterceptor { chain ->
                val request = chain.request()
                if (profile.authMode == AuthMode.TOKEN && profile.token.isNotBlank()) {
                    val url = request.url.newBuilder()
                        .addQueryParameter("token", profile.token)
                        .build()
                    chain.proceed(request.newBuilder().url(url).build())
                } else {
                    chain.proceed(request)
                }
            }
            // TODO(M2)：profile.allowSelfSignedTls 时挂自定义 TrustManager；
            //  TODO(M2)：password 模式登录态 CookieJar 持久化
            .build()

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
