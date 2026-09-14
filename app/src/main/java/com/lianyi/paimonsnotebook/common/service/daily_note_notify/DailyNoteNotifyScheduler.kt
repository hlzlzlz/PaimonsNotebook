package com.lianyi.paimonsnotebook.common.service.daily_note_notify

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.lianyi.paimonsnotebook.common.application.PaimonsNotebookApplication
import com.lianyi.paimonsnotebook.common.util.data_store.PreferenceKeys
import com.lianyi.paimonsnotebook.common.util.data_store.datastorePf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/*
* 便笺提醒周期任务调度
* WorkManager周期任务下限为15分钟,可选间隔30/60分钟
* */
object DailyNoteNotifyScheduler {

    private const val WORK_NAME = "DailyNoteNotifyWork"

    const val DEFAULT_INTERVAL_MINUTES = 30

    fun ensureScheduled() {
        //延迟调度,避免拖慢冷启动
        CoroutineScope(Dispatchers.IO).launch {
            delay(5000)

            val state = PaimonsNotebookApplication.context.datastorePf.data.map {
                (it[PreferenceKeys.EnableDailyNoteNotify] ?: false) to
                        (it[PreferenceKeys.DailyNoteNotifyInterval]
                                ?: DEFAULT_INTERVAL_MINUTES)
            }.first()

            if (state.first) {
                enqueue(state.second, ExistingPeriodicWorkPolicy.KEEP)
            }
        }
    }

    //开关或间隔变更时重新注册,UPDATE策略保证间隔修改立即生效
    fun setEnabled(enabled: Boolean, intervalMinutes: Int) {
        if (enabled) {
            enqueue(intervalMinutes, ExistingPeriodicWorkPolicy.UPDATE)
        } else {
            WorkManager.getInstance(PaimonsNotebookApplication.context)
                .cancelUniqueWork(WORK_NAME)
        }
    }

    private fun enqueue(intervalMinutes: Int, policy: ExistingPeriodicWorkPolicy) {
        val request = PeriodicWorkRequestBuilder<DailyNoteNotifyWorker>(
            intervalMinutes.coerceAtLeast(15).toLong(), TimeUnit.MINUTES
        ).build()

        WorkManager.getInstance(PaimonsNotebookApplication.context).enqueueUniquePeriodicWork(
            WORK_NAME,
            policy,
            request
        )
    }
}
