package com.lianyi.paimonsnotebook.common.service.daily_note_notify

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/*
* 便笺提醒周期任务,失败由系统按指数退避重试
* */
class DailyNoteNotifyWorker(
    context: Context,
    parameters: WorkerParameters
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        return try {
            DailyNoteNotifyService.checkAndNotify()

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
