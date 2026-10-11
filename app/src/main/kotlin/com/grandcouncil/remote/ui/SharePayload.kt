package com.grandcouncil.remote.ui

/**
 * 任务 702 B⑤：系统分享文本规整（纯函数，可 JVM 单测）。
 * 外部「分享到 GrandCouncil」的 ACTION_SEND text/plain 载荷 → 新建会话草稿预填文本。
 */
object SharePayload {

    /** 预填上限：超长分享（如整篇文章）截断，避免输入框塞爆/一次发送超大上下文 */
    const val MAX_LENGTH = 2000

    /**
     * 规则：null/空白 → null（不预填，走普通新建）；其余 trim；
     * 超过 [MAX_LENGTH] 截断并加省略号（保留头部内容语义）。
     */
    fun normalize(text: String?): String? {
        val t = text?.trim().orEmpty()
        if (t.isEmpty()) return null
        return if (t.length <= MAX_LENGTH) t else t.take(MAX_LENGTH) + "…"
    }
}
