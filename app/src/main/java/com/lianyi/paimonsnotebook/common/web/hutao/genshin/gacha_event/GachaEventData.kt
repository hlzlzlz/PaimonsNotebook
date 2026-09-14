package com.lianyi.paimonsnotebook.common.web.hutao.genshin.gacha_event

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/*
* 祈愿卡池事件元数据(Snap.Metadata GachaEvent.json)
* From/To为带+08:00时区的ISO时间字符串
* Type对应祈愿类型: 301角色活动 302武器活动 305集录 等
* */
data class GachaEventData(
    val Name: String,
    val Version: String,
    val Order: Int,
    val Banner: String,
    val Banner2: String,
    val From: String,
    val To: String,
    val Type: Int,
    val UpOrangeList: List<Int>,
    val UpPurpleList: List<Int>
) {
    companion object {
        //带时区偏移的ISO时间解析(服务器统一+08:00)
        private val eventTimeFormat by lazy {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
        }

        //祈愿记录/本地时间的通用格式(东八区)
        val commonTimeFormat by lazy {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("GMT+8")
            }
        }

        fun parseEventTime(time: String): Long? =
            try {
                eventTimeFormat.parse(time)?.time
            } catch (e: Exception) {
                null
            }

        fun parseCommonTime(time: String): Long? =
            try {
                commonTimeFormat.parse(time)?.time
            } catch (e: Exception) {
                null
            }

        //版本+期数展示文案
        fun versionText(event: GachaEventData) =
            "${event.Version}版本 ${if (event.Order == 1) "上半" else "下半"}"
    }
}

//解析过起止时间戳的事件条目
data class GachaEventEntry(
    val event: GachaEventData,
    val fromMillis: Long,
    val toMillis: Long
)
