package com.lianyi.paimonsnotebook.common.service.sign_in

import com.lianyi.paimonsnotebook.common.application.PaimonsNotebookApplication
import com.lianyi.paimonsnotebook.common.database.user.util.AccountHelper
import com.lianyi.paimonsnotebook.common.data.hoyolab.PlayerUid
import com.lianyi.paimonsnotebook.common.extension.data_store.editValue
import com.lianyi.paimonsnotebook.common.extension.string.errorNotify
import com.lianyi.paimonsnotebook.common.extension.string.notify
import com.lianyi.paimonsnotebook.common.util.data_store.PreferenceKeys
import com.lianyi.paimonsnotebook.common.util.data_store.datastorePf
import com.lianyi.paimonsnotebook.common.util.json.JSON
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
                    }

                    //已签到
                    result.retcode == -5003 -> {
                        completedMap[uid] = today
                        changed = true
                    }

                    //命中风控
                    result.data.gt.isNotBlank() || result.data.risk_code != 0 -> {
                        "UID[${uid}]自动签到触发风控,请手动前往签到页面完成签到".notify()
                    }

                    else -> {
                        "UID[${uid}]自动签到失败:${result.message}".errorNotify()
                    }
                }

                //请求间隔,避免请求过快
                Thread.sleep(1500)
            }
        }

        if (changed) {
            saveCompletedMap(completedMap)
        }
    }

    private fun serverToday() = serverTimeFormat.format(Date())

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
