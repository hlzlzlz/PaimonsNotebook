package com.lianyi.paimonsnotebook.common.service.sign_in

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lianyi.paimonsnotebook.common.application.PaimonsNotebookApplication

/*
* 每日自动签到任务
* */
class AutoSignInWorker(
    context: Context,
    parameters: WorkerParameters
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        return try {
            AutoSignInService.signInAll()

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
