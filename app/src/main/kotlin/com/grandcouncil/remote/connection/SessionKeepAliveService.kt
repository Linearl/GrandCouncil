package com.grandcouncil.remote.connection

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.grandcouncil.remote.MainActivity
import com.grandcouncil.remote.R
import com.grandcouncil.remote.notify.AppNotifications

/**
 * tl1552 子项B 前台服务保活：会话打开期间常驻前台通知，提升进程优先级防系统
 * 杀后台断连；辅以 WifiLock（高性能模式）+ 部分 WakeLock 防 CPU/无线电休眠
 * 掐断 SSE 事件流与 15s 租约心跳（对照同类远程控制 app 做法）。
 *
 * 生命周期由外部驱动：进入会话（MainScreen 选中态）start，退出会话/
 * Activity 结束 stop——服务本身无业务逻辑，只持有前台通知与锁。
 *
 * 类型选 specialUse：长会话挂机无时限（dataSync 在 Android 15+ 有 6h/24h
 * 上限，挂机验收场景不可接受）；subtype 见 AndroidManifest property 声明。
 */
class SessionKeepAliveService : Service() {

    private var wifiLock: WifiManager.WifiLock? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        // 参考计数关闭：acquire/release 成对收敛在 onCreate/onDestroy
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, TAG_WIFI_LOCK).apply {
            setReferenceCounted(false)
            acquire()
        }
        val powerManager = applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG_WAKE_LOCK).apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        val label = intent?.getStringExtra(EXTRA_LABEL).orEmpty().ifBlank { "远程会话" }
        AppNotifications.createChannels(this) // 兜底：渠道被用户删除后重建（幂等）
        startAsForeground(label)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        runCatching { wakeLock?.release() }
        runCatching { wifiLock?.release() }
        wifiLock = null
        wakeLock = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun startAsForeground(label: String) {
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(label),
            // specialUse 常量 API 34 起；ServiceCompat 内部按系统版本降级处理
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
    }

    /** 常驻通知：点按返回 App；静默渠道（gc_connection LOW），不打扰 */
    private fun buildNotification(label: String): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            this,
            0,
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("连接保持中")
            .setContentText(label)
            .setOngoing(true)
            .setAutoCancel(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(pendingIntent)
            .build()
    }

    companion object {
        const val ACTION_START = "gc_keepalive_start"
        const val ACTION_STOP = "gc_keepalive_stop"
        const val EXTRA_LABEL = "gc_keepalive_label"
        private const val CHANNEL_ID = "gc_connection"

        // 通知 id 避开 AppNotifications.notify 的 type.ordinal*1000 区段（0-4xxx）
        private const val NOTIFICATION_ID = 2101
        private const val TAG_WIFI_LOCK = "GrandCouncil:KeepAliveWifi"
        private const val TAG_WAKE_LOCK = "GrandCouncil:KeepAliveCpu"

        /** 进入会话时调用（App 前台；startForegroundService 保证 5s 内前台化） */
        fun start(context: Context, label: String) {
            val intent = Intent(context, SessionKeepAliveService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_LABEL, label)
            ContextCompat.startForegroundService(context, intent)
        }

        /** 退出会话/应用退出时调用（stopService 后台可用，不受 startService 限制） */
        fun stop(context: Context) {
            context.stopService(Intent(context, SessionKeepAliveService::class.java))
        }
    }
}
