package com.grandcouncil.remote.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.grandcouncil.remote.MainActivity
import com.grandcouncil.remote.R
import com.grandcouncil.remote.connection.ConnectionStore
import com.grandcouncil.remote.repository.SessionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * 任务 702 B④：桌面 Widget——会话状态概览最小面。
 * 内容：设备数 / 会话数 / 运行中数（isCurrent）；点按整块打开 App。
 * 数据：onUpdate 时直连各 profile 的 /sessions（并行 + 6s 总超时，失败该设备按空计）；
 * 进程无关（Widget 自身进程拉取），App 是否存活不影响刷新。
 * 刷新：系统 updatePeriodMillis 下限 30 分钟 + 桌面手动刷新（launcher 行为）。
 */
class SessionSummaryWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync() // 广播收尾交给异步拉取（系统给 ~10s 窗口）
        val app = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val profiles = runCatching { ConnectionStore(app).profiles.first() }.getOrDefault(emptyList())
                val sessionsPerDevice = coroutineScope {
                    profiles.map { profile ->
                        async {
                            runCatching {
                                withTimeout(6_000L) { SessionRepository().listSessions(profile) }
                            }.getOrNull()?.getOrDefault(emptyList()) ?: emptyList()
                        }
                    }.awaitAll()
                }
                val summary = WidgetSummary.ofDevices(sessionsPerDevice)
                val views = buildViews(context, summary)
                appWidgetManager.updateAppWidget(appWidgetIds, views)
            } catch (_: Exception) {
                // 拉取超时/异常：放弃本次刷新（保留旧内容），下个周期重试
            } finally {
                pending.finish()
            }
        }
    }

    /** 组装 RemoteViews（internal：供单测对文案逻辑的间接验证走 WidgetSummary，此处只接线） */
    private fun buildViews(context: Context, summary: WidgetSummary): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.session_summary_widget).apply {
            setTextViewText(R.id.widget_headline, WidgetSummary.headline(summary))
            setTextViewText(R.id.widget_detail, WidgetSummary.detail(summary))
        }
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        views.setOnClickPendingIntent(R.id.widget_root, open)
        return views
    }
}
