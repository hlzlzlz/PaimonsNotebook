package com.lianyi.paimonsnotebook.common.util.time

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/*
* 服务端时间字符串("2026-09-12 21:15:00")与"剩余时间"文案的换算
*
* 为什么单独抽成 object 而不是塞进 TimeHelper:
*   1. TimeHelper 用的是 SimpleDateFormat(非线程安全,内部会改写 Calendar),
*      而本文件用 DateTimeFormatter(不可变、线程安全),无需加锁。
*   2. 纯函数便于单元测试 —— 剩余时间文案有边界(已结束/不足1天/跨多天),
*      这类分支最容易写错却最难在真机上覆盖。
*
* 服务端下发的时间是**北京时间**(UTC+8),不带时区后缀。若直接用系统默认时区
* 解析,海外用户会算错剩余时间。故这里固定按 +08:00 解析。
* */
object TimeRemainingHelper {

    //服务端下发格式,实测形如 "2026-11-03 00:00:00"
    private val SERVER_FORMATTER: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    //服务端时间固定为北京时间
    private const val SERVER_UTC_OFFSET_HOURS = 8L

    private const val SECONDS_PER_MINUTE = 60L
    private const val SECONDS_PER_HOUR = 3600L
    private const val SECONDS_PER_DAY = 86400L

    /*
    * 解析服务端时间字符串为 Unix 秒;失败返回 null(不抛异常)。
    *
    * 注意:不能返回 0 作为失败标志 —— 0 是合法的 Unix 时间戳(1970),
    * 若调用方拿 0 去算剩余时间会得到"已结束"这种误导性结论。
    * */
    fun parseServerTimeToEpochSecond(text: String?): Long? {
        if (text.isNullOrBlank()) return null

        return try {
            val local = LocalDateTime.parse(text.trim(), SERVER_FORMATTER)
            local.toEpochSecond(
                java.time.ZoneOffset.ofTotalSeconds((SERVER_UTC_OFFSET_HOURS * 3600).toInt())
            )
        } catch (e: DateTimeParseException) {
            null
        } catch (e: Exception) {
            null
        }
    }

    /*
    * 剩余时间文案。
    *
    * endTimeText 服务端结束时间;nowEpochSecond 当前时间(便于测试注入)。
    * 已结束或解析失败返回 null,由调用方决定是否隐藏该行 ——
    * 返回 "已结束" 会让"本期深渊"卡片自相矛盾(数据明明是本期的)。
    * */
    fun formatRemaining(
        endTimeText: String?,
        nowEpochSecond: Long = System.currentTimeMillis() / 1000
    ): String? {
        val end = parseServerTimeToEpochSecond(endTimeText) ?: return null

        val remaining = end - nowEpochSecond

        if (remaining <= 0) return null

        //不足 1 天:只显示小时/分钟,避免出现"0天后"
        if (remaining < SECONDS_PER_MINUTE) {
            return "剩余${remaining}秒"
        }

        if (remaining < SECONDS_PER_HOUR) {
            return "剩余${remaining / SECONDS_PER_MINUTE}分钟"
        }

        if (remaining < SECONDS_PER_DAY) {
            val hours = remaining / SECONDS_PER_HOUR
            val minutes = remaining % SECONDS_PER_HOUR / SECONDS_PER_MINUTE

            return if (minutes > 0) {
                "剩余${hours}小时${minutes}分钟"
            } else {
                "剩余${hours}小时"
            }
        }

        val days = remaining / SECONDS_PER_DAY
        val hours = remaining % SECONDS_PER_DAY / SECONDS_PER_HOUR

        return if (hours > 0) {
            "剩余${days}天${hours}小时"
        } else {
            "剩余${days}天"
        }
    }
}
