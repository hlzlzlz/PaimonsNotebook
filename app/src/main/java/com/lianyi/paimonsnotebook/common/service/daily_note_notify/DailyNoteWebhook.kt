package com.lianyi.paimonsnotebook.common.service.daily_note_notify

import com.lianyi.paimonsnotebook.common.web.hoyolab.takumi.game_record.daily_note.DailyNoteData
import com.lianyi.paimonsnotebook.common.util.json.JSON
import com.lianyi.paimonsnotebook.common.util.request.buildRequest
import com.lianyi.paimonsnotebook.common.util.request.emptyOkHttpClient
import com.lianyi.paimonsnotebook.common.util.request.getAsText
import com.lianyi.paimonsnotebook.common.util.request.toRequestBody

/*
* 便笺 Webhook 推送
*
* 行为对照胡桃工具箱的 DailyNoteWebhookOperation(已读源码):
*   把该 uid 的便笺数据以 JSON POST 到用户配置的地址,并带 x-uid 请求头。
*
* 为什么放在这里而不是塞进 DailyNoteNotifyService:
*   推送与"是否要弹通知"是两件独立的事 —— 用户可能只想要 Webhook
*   而不想要系统通知。独立出来也便于单独测试 URL 校验逻辑。
*
* ⚠️ 与通知不同,推送**不受抑制表影响**:Webhook 的用途是让外部系统
* (如 HomeAssistant / 自建看板)持续拿到最新数据,若按"上升沿"只推一次,
* 外部系统拿不到后续变化。故每轮检查都推。
* */
object DailyNoteWebhook {

    /*
    * 校验 webhook 地址是否可用。
    *
    * 只接受 http/https 绝对地址:
    *   - 空串表示未配置,不算错误(调用方静默跳过)
    *   - 其它 scheme(如 file:// / content://)会让 OkHttp 抛异常,提前挡掉
    *
    * 抽成纯函数便于单测(无法在 JVM 单测里真的发请求)。
    * */
    fun isValidUrl(url: String?): Boolean {
        val trimmed = url?.trim().orEmpty()

        if (trimmed.isEmpty()) return false

        val lower = trimmed.lowercase()

        return (lower.startsWith("http://") || lower.startsWith("https://")) &&
                //排除 "http://" 这种只有 scheme 没有主机的写法
                trimmed.length > "https://".length
    }

    /*
    * 推送一次便笺数据。
    *
    * 返回是否推送成功(供调用方记录);任何异常都被吞掉并返回 false ——
    * 后台 Worker 里抛异常会杀掉整个检查周期,不能让一个配错的 webhook
    * 地址影响系统通知。
    * */
    suspend fun post(
        url: String,
        uid: String,
        data: DailyNoteData
    ): Boolean {
        if (!isValidUrl(url)) return false

        return try {
            //便笺数据整体作为 JSON body(与胡桃的 PostJson(dailyNote) 一致)
            val body = JSON.stringify(data)

            buildRequest {
                url(url.trim())
                addHeader("x-uid", uid)
                post(body.toRequestBody())
            }.getAsText(emptyOkHttpClient)

            true
        } catch (e: Exception) {
            false
        }
    }
}
