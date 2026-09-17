package com.lianyi.paimonsnotebook.common.service.sign_in

import android.content.Intent
import com.lianyi.paimonsnotebook.common.application.PaimonsNotebookApplication
import com.lianyi.paimonsnotebook.common.database.user.util.AccountHelper
import com.lianyi.paimonsnotebook.common.data.hoyolab.PlayerUid
import com.lianyi.paimonsnotebook.common.extension.data_store.editValue
import com.lianyi.paimonsnotebook.common.extension.string.errorNotify
import com.lianyi.paimonsnotebook.common.extension.string.notify
import com.lianyi.paimonsnotebook.common.util.data_store.PreferenceKeys
import com.lianyi.paimonsnotebook.common.util.data_store.datastorePf
import com.lianyi.paimonsnotebook.common.util.json.JSON
import com.lianyi.paimonsnotebook.common.util.notification.NotificationHelper
import com.lianyi.paimonsnotebook.ui.screen.account.view.AccountManagerScreen
import com.lianyi.paimonsnotebook.common.web.hoyolab.takumi.event.bbs_sign_reward.SignInClient
import com.lianyi.paimonsnotebook.common.web.ApiEndpoints
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/*
* 米游社自动签到
* 对所有拥有cookie_token的用户与角色执行每日签到
* 命中风控时不自动过验证,通知用户前往官方页面手动签到
* */
object AutoSignInService {

    private val signInClient = SignInClient()

    private val serverTimeFormat by lazy {
        SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("GMT+8")
        }
    }

    suspend fun signInAll() {
        val users = AccountHelper.userListFlow.first()

        if (users.isEmpty()) {
            return
        }

        val today = serverToday()
        val completedMap = readCompletedMap().toMutableMap()
        var changed = false

        val enableAutoReSign = readAutoResignEnabled()

        //系统通知内容汇总,worker后台执行时应用内浮层无人可见
        val systemMessages = mutableListOf<String>()

        users.forEach { user ->
            user.userGameRoles?.forEach { role ->
                val uid = role.game_uid
                val playerUid = PlayerUid.fromGameRole(role)

                //当天已完成则跳过
                if (completedMap[uid] == today) {
                    return@forEach
                }

                val result = signInClient.sign(user.userEntity, playerUid)

                when {
                    result.success -> {
                        completedMap[uid] = today
                        changed = true
                        "UID[${uid}]自动签到成功".notify()
                        systemMessages += "UID[$uid] 签到成功"
                    }

                    //已签到
                    result.retcode == -5003 -> {
                        completedMap[uid] = today
                        changed = true
                    }

                    //命中风控
                    //data声明非空但服务端可能返回data:null,此处必须判空
                    result.data?.gt.isNullOrBlank() == false || (result.data?.risk_code ?: 0) != 0 -> {
                        "UID[${uid}]自动签到触发风控,请手动前往签到页面完成签到".notify()
                        systemMessages += "UID[$uid] 触发风控,请手动前往签到页面完成签到"
                    }

                    else -> {
                        "UID[${uid}]自动签到失败:${result.message}".errorNotify()
                        systemMessages += "UID[$uid] 签到失败:${result.message}"
                    }
                }

                //补签:当天签到完成且开启开关时,检查漏签并自动补一张(消耗补签卡)
                if (enableAutoReSign && (result.success || result.retcode == -5003)) {
                    val resignInfo = signInClient.getResignInfo(user.userEntity, playerUid)

                    val resignData = resignInfo.data

                    if (resignInfo.success && resignData != null && resignData.canResign) {
                        val resignResult = signInClient.reSign(user.userEntity, playerUid)

                        when {
                            resignResult.success -> {
                                systemMessages += "UID[$uid] 补签成功(剩余补签卡${resignData.coin_cnt - resignData.coin_cost})"
                            }

                            //data声明非空但服务端可能返回data:null,此处必须判空
                            resignResult.data?.gt.isNullOrBlank() == false || (resignResult.data?.risk_code ?: 0) != 0 -> {
                                systemMessages += "UID[$uid] 补签触发风控,请手动前往签到页补签"
                            }

                            else -> {
                                systemMessages += "UID[$uid] 补签失败:${resignResult.message}"
                            }
                        }

                        //补签请求间隔
                        Thread.sleep(1500)
                    }
                }

                //请求间隔,避免请求过快
                Thread.sleep(1500)
            }
        }

        if (changed) {
            saveCompletedMap(completedMap)
        }

        if (systemMessages.isNotEmpty()) {
            NotificationHelper.buildLargeTextNotification(
                title = "米游社自动签到",
                content = systemMessages.joinToString("\n"),
                autoCancel = true,
                notificationId = 30500,
                intent = Intent(
                    PaimonsNotebookApplication.context,
                    AccountManagerScreen::class.java
                )
            )
        }
    }

    private fun serverToday() = serverTimeFormat.format(Date())

    private suspend fun readAutoResignEnabled(): Boolean =
        PaimonsNotebookApplication.context.datastorePf.data.map {
            it[PreferenceKeys.EnableAutoReSign] ?: false
        }.first()

    private suspend fun readCompletedMap(): Map<String, String> {
        val json = PaimonsNotebookApplication.context.datastorePf.data.map {
            it[PreferenceKeys.AutoSignInLastCompletedMap]
        }.first()

        if (json.isNullOrBlank()) {
            return emptyMap()
        }

        return try {
            JSON.parse<Map<String, String>>(json)
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private suspend fun saveCompletedMap(map: Map<String, String>) {
        PreferenceKeys.AutoSignInLastCompletedMap.editValue(JSON.stringify(map))
    }
}
