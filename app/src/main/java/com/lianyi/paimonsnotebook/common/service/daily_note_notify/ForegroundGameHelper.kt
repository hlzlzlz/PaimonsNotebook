package com.lianyi.paimonsnotebook.common.service.daily_note_notify

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process

/*
* 前台游戏检测(便笺免打扰)
* 通过使用情况访问权限读取最近的前台应用事件,判断原神是否在前台
* */
object ForegroundGameHelper {

    //国服/渠道服/国际服原神包名
    private val gamePackages = setOf(
        "com.miHoYo.Yuanshen",
        "com.miHoYo.Yuanshen.bilibili",
        "com.miHoYo.GenshinImpact"
    )

    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager

        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }

        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun isGameForeground(context: Context): Boolean {
        if (!hasUsageAccess(context)) {
            return false
        }

        val usageStatsManager =
            context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

        val end = System.currentTimeMillis()
        val events = usageStatsManager.queryEvents(end - 90_000, end)

        var lastForegroundPackage: String? = null
        var foreground = false

        while (events.hasNextEvent()) {
            val event = UsageEvents.Event()
            events.getNextEvent(event)

            when (event.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND, UsageEvents.Event.ACTIVITY_RESUMED -> {
                    lastForegroundPackage = event.packageName
                    foreground = true
                }

                UsageEvents.Event.MOVE_TO_BACKGROUND, UsageEvents.Event.ACTIVITY_PAUSED -> {
                    if (event.packageName == lastForegroundPackage) {
                        foreground = false
                    }
                }
            }
        }

        return foreground && lastForegroundPackage in gamePackages
    }
}
