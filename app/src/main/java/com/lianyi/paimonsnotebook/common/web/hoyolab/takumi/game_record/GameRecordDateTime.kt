package com.lianyi.paimonsnotebook.common.web.hoyolab.takumi.game_record

import java.text.SimpleDateFormat
import java.util.Calendar

/*
* 游戏记录接口中的日期时间对象
* 剧诗/幽境危战等接口的日期字段是分字段的对象而非字符串
* */
data class GameRecordDateTime(
    val year: Int,
    val month: Int,
    val day: Int,
    val hour: Int,
    val minute: Int,
    val second: Int
) {
    fun format(): String {
        val calendar = Calendar.getInstance()
        calendar.set(year, (month - 1).coerceAtLeast(0), day.coerceAtLeast(1), hour, minute, second)
        return SimpleDateFormat("yyyy-MM-dd HH:mm").format(calendar.time)
    }
}
