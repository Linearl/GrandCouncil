package com.grandcouncil.remote.connection

import android.content.Context

/**
 * GC-20 最近链接（B7 §1 规格）：连接页存最近 5 条地址（私有 SharedPreferences，
 * 对标 zcode 首页最近 5 条链接）。新在前、去重、超限丢弃最旧。
 */
class RecentLinksStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("gc_recent_links", Context.MODE_PRIVATE)

    /** 记录一次连接地址（保存成功后调用） */
    fun add(url: String) {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return
        val current = prefs.getString(KEY_LINKS, "")!!
            .split(SEP).filter { it.isNotEmpty() }.toMutableList()
        current.remove(trimmed)
        current.add(0, trimmed)
        prefs.edit().putString(
            KEY_LINKS,
            current.take(MAX).joinToString(SEP),
        ).apply()
    }

    /** 最近链接（新在前，最多 5 条） */
    fun list(): List<String> =
        prefs.getString(KEY_LINKS, "")!!
            .split(SEP).filter { it.isNotEmpty() }.take(MAX)

    companion object {
        private const val KEY_LINKS = "links"
        private const val SEP = "\n"
        private const val MAX = 5
    }
}
