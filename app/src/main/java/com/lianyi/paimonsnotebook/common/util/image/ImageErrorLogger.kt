package com.lianyi.paimonsnotebook.common.util.image

import com.lianyi.paimonsnotebook.common.application.PaimonsNotebookApplication
import com.lianyi.paimonsnotebook.common.extension.string.errorNotify
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/*
* 网络图片加载失败记录器
*
* 将失败原因写入 files/image_error.log,并在当次运行首次失败时弹出通知
* 用于在无法连接调试器的情况下定位图片加载失败的真实原因
* */
object ImageErrorLogger {

    private val context by lazy { PaimonsNotebookApplication.context }

    private val format by lazy { SimpleDateFormat("MM-dd HH:mm:ss", Locale.US) }

    @Volatile
    private var firstErrorNotified = false

    fun log(url: String, throwable: Throwable?) {
        val message = buildString {
            append(format.format(Date()))
            append(" ")
            append(url)
            append(" -> ")
            append(throwable?.javaClass?.simpleName)
            append(": ")
            append(throwable?.message ?: "unknown")
        }

        appendToFile(message)

        synchronized(this) {
            if (!firstErrorNotified) {
                firstErrorNotified = true
                "图片加载失败:${throwable?.javaClass?.simpleName}:${throwable?.message?.take(120)}\n完整日志:files/image_error.log".errorNotify()
            }
        }
    }

    private fun appendToFile(message: String) {
        try {
            val file = File(context.filesDir, "image_error.log")
            file.appendText(message + "\n")
        } catch (_: Exception) {
        }
    }
}
