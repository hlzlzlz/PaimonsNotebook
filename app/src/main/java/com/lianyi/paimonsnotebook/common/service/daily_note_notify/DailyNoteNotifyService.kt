package com.lianyi.paimonsnotebook.common.service.daily_note_notify

import android.content.Intent
import com.lianyi.paimonsnotebook.common.application.PaimonsNotebookApplication
import com.lianyi.paimonsnotebook.common.data.hoyolab.PlayerUid
import com.lianyi.paimonsnotebook.common.database.daily_note.util.DailyNoteHelper
import com.lianyi.paimonsnotebook.common.extension.data_store.editValue
import com.lianyi.paimonsnotebook.common.util.data_store.PreferenceKeys
import com.lianyi.paimonsnotebook.common.util.data_store.datastorePf
import com.lianyi.paimonsnotebook.common.util.json.JSON
import com.lianyi.paimonsnotebook.common.util.notification.NotificationHelper
import com.lianyi.paimonsnotebook.ui.screen.daily_note.view.DailyNoteScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/*
* 实时便笺后台检查与系统通知
*
* 按周期检查每个便笺账号,评估树脂/家园币/每日委托/参量物质/派遣五类条件,
* 条件从"不满足"变为"满足"(上升沿)时通知一次,持续满足期间不重复提醒,
* 条件回落后重新武装(规则与胡桃工具箱一致)
* */
object DailyNoteNotifyService {

    //家园币提醒阈值
    private const val HOME_COIN_NOTIFY_THRESHOLD = 1800

    //树脂提醒阈值默认值(可在设置中调整)
    private const val RESIN_NOTIFY_THRESHOLD_DEFAULT = 120

    //通知id起点,按uid散列分配不同通知槽,避免多账号互相覆盖
    private const val NOTIFY_ID_BASE = 30000
    private const val NOTIFY_ID_RANGE = 10000

    //参与抑制评估的全部条件键
    private val conditionKeys = listOf(
        "resin", "homeCoin", "dailyTask", "transformer", "expedition"
    )

    suspend fun checkAndNotify() {
        val context = PaimonsNotebookApplication.context

        val (enabled, resinThreshold, dndGaming) = context.datastorePf.data.map {
            Triple(
                it[PreferenceKeys.EnableDailyNoteNotify] ?: false,
                it[PreferenceKeys.DailyNoteResinNotifyThreshold] ?: RESIN_NOTIFY_THRESHOLD_DEFAULT,
                it[PreferenceKeys.DailyNoteNotifyDndGaming] ?: false
            )
        }.first()

        /*
        * Webhook 地址独立读取。
        *
        * 注意:Webhook 与"是否开启系统通知"是两件事 —— 用户可能只想要
        * Webhook 推送而不想要系统通知,故不能因为它复用了本方法的
        * 检查周期就要求 EnableDailyNoteNotify 也为真。
        * 但若两者都没配,则直接返回,省掉整轮网络请求。
        * */
        val webhookUrl = context.datastorePf.data.map {
            it[PreferenceKeys.DailyNoteWebhookUrl] ?: ""
        }.first()

        if (!enabled && !DailyNoteWebhook.isValidUrl(webhookUrl)) {
            return
        }

        //便笺列表由DailyNoteHelper的后台数据流维护,worker协程内读取安全
        val dailyNotes = DailyNoteHelper.dailyNoteFlow.value

        if (dailyNotes.isEmpty()) {
            return
        }

        //免打扰:原神前台时本周期不写抑制也不通知,退出游戏后下一周期重新评估
        val gamingDnd = dndGaming && ForegroundGameHelper.isGameForeground(context)

        val suppressed = readSuppressed().toMutableMap()
        var changed = false

        dailyNotes.forEach { dailyNote ->
            val uid = dailyNote.dailyNoteEntity.uid

            val result = try {
                DailyNoteHelper.getDailyNoteResultData(
                    user = dailyNote.userEntity,
                    playerUid = PlayerUid.fromGameRole(dailyNote.role)
                )
            } catch (e: Exception) {
                null
            }

            //请求失败(含触发验证)本周期静默跳过,下个周期自然重试
            if (result == null || !result.success) {
                return@forEach
            }

            val data = result.data

            /*
            * Webhook 推送:每轮都推(不受抑制表影响)。
            *
            * 与系统通知不同 —— Webhook 的用途是让外部系统持续拿到最新数据,
            * 若按"上升沿"只推一次,外部系统就拿不到后续变化。
            * 放在通知评估之前,保证即使通知被免打扰跳过,数据仍然推送。
            * */
            if (DailyNoteWebhook.isValidUrl(webhookUrl)) {
                DailyNoteWebhook.post(
                    url = webhookUrl,
                    uid = uid,
                    data = data
                )
            }

            //未开启系统通知时,本轮只做推送,不再评估提醒条件
            if (!enabled) {
                return@forEach
            }

            //逐项评估,满足则记录条件键与提醒文案
            val triggered = mutableListOf<Pair<String, String>>()

            if (data.current_resin >= resinThreshold) {
                triggered += "resin" to
                        "树脂 ${data.current_resin}/${data.max_resin}" +
                        (if (data.current_resin >= data.max_resin) "（已满）" else "")
            }

            if (data.current_home_coin >= HOME_COIN_NOTIFY_THRESHOLD) {
                triggered += "homeCoin" to
                        "家园币 ${data.current_home_coin}/${data.max_home_coin}"
            }

            if (data.total_task_num > 0 && data.finished_task_num >= data.total_task_num
                && !data.is_extra_task_reward_received
            ) {
                triggered += "dailyTask" to "每日委托已完成,追加奖励可领取"
            }

            if (data.transformer.obtained && data.transformer.recovery_time.reached) {
                triggered += "transformer" to "参量物质已可收取"
            }

            if (data.expeditions.isNotEmpty()
                && data.expeditions.all { it.status == "Finished" }
            ) {
                triggered += "expedition" to "探索派遣全部完成"
            }

            val lines = mutableListOf<String>()
            val triggeredKeys = triggered.map { it.first }.toSet()

            //上升沿:满足且未提醒过 -> 提醒并抑制;免打扰期间保持未武装,退出游戏后再提醒
            triggered.forEach { (key, text) ->
                val suppressKey = "$uid:$key"

                if (suppressKey !in suppressed && !gamingDnd) {
                    suppressed[suppressKey] = true
                    changed = true
                    lines += text
                }
            }

            //回落:条件不再满足 -> 解除抑制,下次满足时重新提醒
            conditionKeys.filterNot { it in triggeredKeys }.forEach { key ->
                val suppressKey = "$uid:$key"

                if (suppressed.remove(suppressKey) != null) {
                    changed = true
                }
            }

            if (lines.isNotEmpty()) {
                val notificationId = NOTIFY_ID_BASE +
                        Math.floorMod(uid.hashCode(), NOTIFY_ID_RANGE)

                NotificationHelper.buildLargeTextNotification(
                    title = "便笺提醒 | ${dailyNote.role.game_uid}",
                    content = lines.joinToString("\n"),
                    type = NotificationHelper.Type.DailyNote,
                    intent = Intent(
                        PaimonsNotebookApplication.context,
                        DailyNoteScreen::class.java
                    ),
                    autoCancel = true,
                    notificationId = notificationId
                )
            }
        }

        if (changed) {
            saveSuppressed(suppressed)
        }
    }

    private suspend fun readSuppressed(): Map<String, Boolean> {
        val json = PaimonsNotebookApplication.context.datastorePf.data.map {
            it[PreferenceKeys.DailyNoteNotifySuppressed]
        }.first()

        if (json.isNullOrBlank()) {
            return emptyMap()
        }

        return try {
            JSON.parse<Map<String, Boolean>>(json)
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private suspend fun saveSuppressed(map: Map<String, Boolean>) {
        PreferenceKeys.DailyNoteNotifySuppressed.editValue(JSON.stringify(map))
    }
}
