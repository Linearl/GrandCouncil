package com.grandcouncil.remote.api

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.util.concurrent.ConcurrentHashMap

/**
 * 内存 Cookie 存储（按 host 隔离）。
 *
 * serve 的认证 cookie（internal/serve/auth.go）Path=/ 全站生效，按 host 保存即可；
 * 多连接（多 serve）各自独立 host，互不串扰。
 */
class SessionCookieJar : CookieJar {

    private val store = ConcurrentHashMap<String, MutableList<Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        val host = url.host
        val list = store.getOrPut(host) { mutableListOf() }
        synchronized(list) {
            cookies.forEach { c ->
                list.removeAll { it.name == c.name }
                list.add(c)
            }
            list.removeAll { it.expiresAt < System.currentTimeMillis() }
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val list = store[url.host] ?: return emptyList()
        synchronized(list) {
            list.removeAll { it.expiresAt < System.currentTimeMillis() }
            return list.toList()
        }
    }

    /** password 模式是否已持有 serve 会话 cookie */
    fun hasSession(host: String): Boolean =
        store[host]?.any { it.name == "reasonix_session" && it.expiresAt > System.currentTimeMillis() } == true

    fun clear(host: String) {
        store.remove(host)
    }
}
