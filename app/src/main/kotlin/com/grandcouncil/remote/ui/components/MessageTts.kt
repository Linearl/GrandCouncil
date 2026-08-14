package com.grandcouncil.remote.ui.components

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.mutableStateOf
import java.util.Locale

/**
 * 消息语音朗读封装（文档《消息操作与建议指导》§1.2 A 级）：
 * 系统 TextToSpeech（中文，离线可用，无新依赖）；单例避免多实例。
 * speaking 为 Compose state，UI 可直接 collectAsState 切换"朗读/停止"图标；
 * 朗读完成自动复位；引擎不可用时 isAvailable=false（UI 半透明禁用）。
 */
object MessageTts {
    private var tts: TextToSpeech? = null
    private var ready = false

    /** 朗读状态（Compose state：朗读中 true；完成/停止自动复位 false） */
    val speaking = mutableStateOf(false)

    /** 初始化（幂等；App 进程内只需一次） */
    fun ensureInit(context: Context) {
        if (tts != null) return
        tts = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            tts?.language = Locale.CHINESE
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    speaking.value = true
                }

                override fun onDone(utteranceId: String?) {
                    speaking.value = false
                }

                override fun onError(utteranceId: String?) {
                    speaking.value = false
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?, errorCode: Int) {
                    speaking.value = false
                }
            })
        }
    }

    /** TTS 引擎是否可用（UI 禁用按钮依据） */
    fun isAvailable(): Boolean = ready

    /** 朗读/停止切换：朗读中调用则停止；否则朗读全文 */
    fun speak(text: String) {
        val engine = tts ?: return
        if (!ready) return
        if (speaking.value) {
            stop()
            return
        }
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "gc-tts")
    }

    fun stop() {
        tts?.stop()
        speaking.value = false
    }

    /** 停止并释放（App 退出时调用） */
    fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
        ready = false
    }
}
