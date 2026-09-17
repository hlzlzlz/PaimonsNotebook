package com.lianyi.paimonsnotebook.common.service.sign_in

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.lianyi.paimonsnotebook.common.application.PaimonsNotebookApplication
import com.lianyi.paimonsnotebook.common.extension.scope.launchSafeIO
import com.lianyi.paimonsnotebook.common.util.data_store.PreferenceKeys
import com.lianyi.paimonsnotebook.common.util.data_store.datastorePf
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit

/*
* 自动签到任务调度
* */
object AutoSignInScheduler {

    private const val WORK_NAME = "AutoSignInDailyWork"

    fun ensureScheduled() {
        //延迟调度,避免拖慢冷启动
        //用launchSafeIO:本函数在Application.onCreate中调用,裸launch抛异常会杀进程
        launchSafeIO {
            delay(5000)
            val enabled = PaimonsNotebookApplication.context.datastorePf.data.map {
                it[PreferenceKeys.EnableAutoSignIn] ?: false
            }.first()

            if (enabled) {
                enqueue()
            }
        }
    }

    fun setEnabled(enabled: Boolean) {
        if (enabled) {
            enqueue()
        } else {
            WorkManager.getInstance(PaimonsNotebookApplication.context)
                .cancelUniqueWork(WORK_NAME)
        }
    }

    private fun enqueue() {
        val request = PeriodicWorkRequestBuilder<AutoSignInWorker>(1, TimeUnit.DAYS)
            .build()

        WorkManager.getInstance(PaimonsNotebookApplication.context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
