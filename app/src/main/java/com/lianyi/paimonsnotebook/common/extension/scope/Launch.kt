package com.lianyi.paimonsnotebook.common.extension.scope

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun CoroutineScope.launchIO(block: suspend CoroutineScope.() -> Unit) =
    this.launch(Dispatchers.IO) {
        block.invoke(this)
    }

fun CoroutineScope.launchMain(block: suspend CoroutineScope.() -> Unit) =
    this.launch(Dispatchers.Main) {
        block.invoke(this)
    }

fun CoroutineScope.launchUnconfined(block: suspend CoroutineScope.() -> Unit) =
    this.launch(Dispatchers.Unconfined) {
        block.invoke(this)
    }

suspend fun withContextMain(block: suspend CoroutineScope.() -> Unit) =
    withContext(Dispatchers.Main) {
        block.invoke(this)
    }

/*
* 无父级Scope的安全IO协程
*
* 专用于object/class的init块等"没有viewModelScope可用"的场景。
* 裸CoroutineScope(Dispatchers.IO).launch里的未捕获异常没有任何父级接管,
* 会走默认UncaughtExceptionHandler直接杀掉整个进程;而本应用CAOC配的是
* BACKGROUND_MODE_SILENT(崩溃页不弹),表现为"应用凭空消失"且无法定位。
*
* SupervisorJob保证子协程之间互不影响,CoroutineExceptionHandler兜住全部异常。
* 结论:凡是要在init块里发起网络/数据库/文件IO的,一律用本函数替代裸launch。
* */
private val appCoroutineExceptionHandler by lazy {
    CoroutineExceptionHandler { _, throwable ->
        throwable.printStackTrace()
    }
}

fun launchSafeIO(block: suspend CoroutineScope.() -> Unit) =
    CoroutineScope(SupervisorJob() + Dispatchers.IO + appCoroutineExceptionHandler).launch {
        block.invoke(this)
    }
