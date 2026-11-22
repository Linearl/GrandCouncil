package com.grandcouncil.remote.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** tl1539 ②：扫码回填拆两字段（Base URL 不收 query，token 独立承载）。 */
class ScanUrlSplitTest {

    @Test
    fun `https tunnel 地址带 token 拆两字段`() {
        val (base, token) = splitScannedUrl("https://abc-xyz.trycloudflare.com?token=s3cret")
        assertEquals("https://abc-xyz.trycloudflare.com", base)
        assertEquals("s3cret", token)
    }

    @Test
    fun `无 query 原样返回且 token 为 null`() {
        val (base, token) = splitScannedUrl("http://10.0.2.2:18789")
        assertEquals("http://10.0.2.2:18789", base)
        assertNull(token)
    }

    @Test
    fun `带端口与路径的地址保留`() {
        val (base, token) = splitScannedUrl("https://host.example:8443/serve?token=abc")
        assertEquals("https://host.example:8443/serve", base)
        assertEquals("abc", token)
    }

    @Test
    fun `非 token 参数丢弃且无 token 时返回 null`() {
        val (base, token) = splitScannedUrl("https://host/?foo=bar")
        assertEquals("https://host/", base)
        assertNull(token)
    }

    @Test
    fun `token 参数在多参数中仍能提取`() {
        val (base, token) = splitScannedUrl("https://host/?a=1&token=tk&b=2")
        assertEquals("https://host/", base)
        assertEquals("tk", token)
    }

    @Test
    fun `空 token 值按 null 处理`() {
        val (_, token) = splitScannedUrl("https://host/?token=")
        assertNull(token)
    }

    @Test
    fun `fragment 在 query 之后不混入 token`() {
        val (base, token) = splitScannedUrl("https://host/?token=tk#frag")
        assertEquals("https://host/", base)
        assertEquals("tk", token)
    }
}
