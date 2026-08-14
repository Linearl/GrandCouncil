package com.grandcouncil.remote.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.grandcouncil.remote.MainActivity
import com.grandcouncil.remote.R

/**
 * A3 分类通知：5 类渠道（审批/问答/完成/错误/连接），
 * 前台不弹（由 NotificationMonitor 只在后台监听）、dedupeKey + 冷却、深链进会话。
 * 依赖 androidx.core:core-ktx 自带 NotificationCompat（core 传递）。
 */
enum class NotifyType(val channelId: String, val channelName: String, val importance: Int) {
    APPROVAL("gc_approval", "审批请求", NotificationManager.IMPORTANCE_HIGH),
    QUESTION("gc_question", "问答请求", NotificationManager.IMPORTANCE_DEFAULT),
    COMPLETED("gc_completed", "任务完成", NotificationManager.IMPORTANCE_DEFAULT),
    ERROR("gc_error", "运行错误", NotificationManager.IMPORTANCE_DEFAULT),
    CONNECTION("gc_connection", "连接状态", NotificationManager.IMPORTANCE_LOW),
}

object AppNotifications {

    /** 通知冷却（同 key 5s 内去重） */
    private const val DEDUPE_COOLDOWN_MS = 5_000L
    private val lastSent = HashMap<String, Long>()

    fun createChannels(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        NotifyType.entries.forEach { t ->
            nm.createNotificationChannel(
                NotificationChannel(t.channelId, t.channelName, t.importance),
            )
        }
    }

    /** 是否有通知权限（Android 13+ 需要运行时授权） */
    fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun requestPermissionIfNeeded(activity: android.app.Activity) {
        if (Build.VERSION.SDK_INT >= 33 && !hasPermission(activity)) {
            activity.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
        }
    }

    /** 发送通知；dedupeKey 非空时 5s 冷却去重；sessionName 非空时深链进会话 */
    fun notify(
        context: Context,
        type: NotifyType,
        title: String,
        body: String,
        dedupeKey: String? = null,
        sessionName: String? = null,
    ) {
        if (!hasPermission(context)) return
        if (dedupeKey != null) {
            val now = System.currentTimeMillis()
            val last = lastSent[dedupeKey]
            if (last != null && now - last < DEDUPE_COOLDOWN_MS) return
            lastSent[dedupeKey] = now
            if (lastSent.size > 64) lastSent.clear()
        }
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            sessionName?.let { putExtra("open_session", it) }
        }
        val pi = PendingIntent.getActivity(
            context,
            type.ordinal,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(context, type.channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(
                if (type.importance >= NotificationManager.IMPORTANCE_HIGH)
                    NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT,
            )
            .setAutoCancel(true)
            .setContentIntent(pi)
        runCatching {
            NotificationManagerCompat.from(context).notify(type.ordinal * 1000 + title.hashCode() % 1000, builder.build())
        }
    }
}
