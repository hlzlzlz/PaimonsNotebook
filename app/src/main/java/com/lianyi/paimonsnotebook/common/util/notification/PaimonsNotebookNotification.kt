package com.lianyi.paimonsnotebook.common.util.notification

import androidx.compose.runtime.mutableStateListOf
import com.lianyi.paimonsnotebook.common.extension.scope.launchSafeIO
import com.lianyi.paimonsnotebook.common.extension.scope.withContextMain
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object PaimonsNotebookNotification {

    val notifications = mutableStateListOf<PaimonsNotebookNotificationData>()

    private const val randRange = "abcdefghijklmnopqrstuvwxyz1234567890"

    private val mutex = Mutex()

    //显示一个通知,返回通知的ID
    fun add(
        content: String,
        type: PaimonsNotebookNotificationType = PaimonsNotebookNotificationType.Normal,
        closeable: Boolean = false,
        autoDismissTime: Long = 3000,
        keepShow: Boolean = false,
    ): String {
        val data = PaimonsNotebookNotificationData(
            notificationId = generateNotificationId(),
            content = content,
            type = type,
            closeable = closeable,
            autoDismissTime = autoDismissTime,
            keepShow = keepShow
        )

        /*
        * 用launchSafeIO而非裸CoroutineScope(Dispatchers.Unconfined):
        * Unconfined会直接在调用线程上开始执行,块内同步抛出的异常会沿调用栈
        * 传播到调用方(String.notify()是全程最高频的辅助方法),且没有兜底。
        *
        * 另外原先这里是 mutex.withLock {  } —— 空临界区,真正需要保护的
        * notifications.add 反而在锁外;而 remove() 里的 removeIf 在锁内,
        * 两者并发时会与Compose对同一 SnapshotStateList 的迭代冲突。
        * 现把 add 纳入同一把锁。
        * */
        launchSafeIO {
            mutex.withLock {
                withContextMain {
                    notifications.add(data)
                }
            }

            //设置自动消失
            if (!(data.keepShow || data.closeable)) {
                delay(data.autoDismissTime)
                delayRemove(data)
            }
        }

        return data.notificationId
    }

    private suspend fun delayRemove(data: PaimonsNotebookNotificationData, delayTime: Long = 500L) {
        data.isShowing.value = false

        remove(data.notificationId)
    }

    private suspend fun remove(id: String) {
        mutex.withLock {
            notifications.removeIf { it.notificationId == id }
        }
    }

    fun removeNotifyById(id: String) {
        //同上:裸CoroutineScope(Unconfined)无异常兜底
        launchSafeIO {
            remove(id)
        }
    }

    //用于生成通知ID
    private fun generateNotificationId(): String {
        val timestamp = System.currentTimeMillis()
        val sb = StringBuilder()
        repeat(6) {
            sb.append(randRange.random())
        }
        val p1 = sb.toString()
        sb.clear()
        repeat(6) {
            sb.append(randRange.random())
        }
        val p2 = sb.toString()
        sb.clear()
        return "${timestamp}-${p1}-${p2}"
    }

}