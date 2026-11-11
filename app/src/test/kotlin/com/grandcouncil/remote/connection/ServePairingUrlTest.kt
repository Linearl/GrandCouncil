package com.grandcouncil.remote.connection

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * tl1552：serve 配对 URL 形态解析（tl1536 同形态 http(s)://host:port?token=xxx）。
 * 回填解析的等价类覆盖：纯地址 / 带 token / 多参数 / 无值参数 / 编码值 / 空输入。
 */
class ServePairingUrlTest {

    @Test
    fun `纯地址无 query token 为空`() {
        val parsed = parseServePairingUrl("http://192.168.1.10:18789")
        assertEquals("http://192.168.1.10:18789", parsed.baseUrl)
        assertEquals("", parsed.token)
    }

    @Test
    fun `tl1536 配对形态 token 拆出`() {
        val parsed = parseServePairingUrl("http://192.168.1.10:18789?token=abc123")
        assertEquals("http://192.168.1.10:18789", parsed.baseUrl)
        assertEquals("abc123", parsed.token)
    }

    @Test
    fun `带路径的地址只拆 query 保留路径`() {
        val parsed = parseServePairingUrl("https://gw.example.cn/p/proj1?token=t-1")
        assertEquals("https://gw.example.cn/p/proj1", parsed.baseUrl)
        assertEquals("t-1", parsed.token)
    }

    @Test
    fun `多参数时 token 仍被识别`() {
        val parsed = parseServePairingUrl("http://a:1?x=9&token=kk&y=2")
        assertEquals("http://a:1", parsed.baseUrl)
        assertEquals("kk", parsed.token)
    }

    @Test
    fun `token 参数名编码后仍识别`() {
        val parsed = parseServePairingUrl("http://a:1?%74oken=v")
        assertEquals("http://a:1", parsed.baseUrl)
        assertEquals("v", parsed.token)
    }

    @Test
    fun `token 值含编码字符被解码`() {
        val parsed = parseServePairingUrl("http://a:1?token=a%2Bb%3Dc")
        assertEquals("a+b=c", parsed.token)
    }

    @Test
    fun `无值参数不影响解析`() {
        val parsed = parseServePairingUrl("http://a:1?flag&token=z")
        assertEquals("z", parsed.token)
    }

    @Test
    fun `非 token 参数不误取`() {
        val parsed = parseServePairingUrl("http://a:1?tokens=x&mytoken=y")
        assertEquals("http://a:1", parsed.baseUrl)
        assertEquals("", parsed.token)
    }

    @Test
    fun `空输入返回空`() {
        val parsed = parseServePairingUrl("   ")
        assertEquals("", parsed.baseUrl)
        assertEquals("", parsed.token)
    }

    @Test
    fun `question mark 后为空视为无参数`() {
        val parsed = parseServePairingUrl("http://a:1?")
        assertEquals("http://a:1", parsed.baseUrl)
        assertEquals("", parsed.token)
    }

    @Test
    fun `首尾空白被剔除`() {
        val parsed = parseServePairingUrl("  http://a:1?token=q  ")
        assertEquals("http://a:1", parsed.baseUrl)
        assertEquals("q", parsed.token)
    }

    @Test
    fun `URLDecoder 非法序列安全降级为原样`() {
        // URLDecoder.decode 对非法 % 序列抛 IllegalArgumentException，解析须不崩且取原样
        val parsed = parseServePairingUrl("http://a:1?token=%zz")
        assertEquals("http://a:1", parsed.baseUrl)
        assertEquals("%zz", parsed.token)
    }
}
