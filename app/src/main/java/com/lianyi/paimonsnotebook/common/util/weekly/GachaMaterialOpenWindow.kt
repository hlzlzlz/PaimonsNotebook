package com.lianyi.paimonsnotebook.common.util.weekly

import com.lianyi.paimonsnotebook.common.web.hutao.genshin.gacha_event.GachaEventData
import com.lianyi.paimonsnotebook.common.web.hutao.genshin.gacha_event.GachaEventEntry
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/*
* 卡池开启后的「全素材本开放」窗口判定
*
* 移植自胡桃 Snap.Hutao.Remastered 的 GachaEventSchedule.cs
* （提交 6d521208f「在卡池开启的7天内 无刷本限制」）。
*
* ## 游戏机制
* 天赋本/周本材料的"周几开放"轮换，在**新角色卡池开启后的 7 天内**会被解除
* —— 这 7 天全部素材本全天可刷。PN 原先只按"周几"硬算，于是新版本开卡池
* 那一周显示的素材是**错的**（用户以为某材料今天不可刷，其实可以）。
*
* ## 为什么只认 301 / 400
* 触发这个窗口的是**角色活动祈愿**：
*   - 301 角色活动祈愿
*   - 400 特殊角色活动祈愿（`GachaType.SpecialActivityAvatar`）
* 武器池(302)、集录(500)不触发。
* 与胡桃的 `IsDateInAllMaterialsOpenWindow` 判定完全一致（它用的是
* `GachaType.ActivityAvatar or GachaType.SpecialActivityAvatar`）。
*
* ## 窗口边界（与胡桃逐字对齐）
* 起算点是该卡池的 **From**（换卡池/版本更新起点），窗口为
* `[From 当日, From 当日 + 7 天)` —— 左闭右开，故 From 当天算第 1 天，
* 第 7 天仍开放，第 8 天恢复按周几轮换。
*
* ⚠️ 这里用 **DateOnly 语义**（按服务器时区的"日期"比较），不是按 7×24 小时
*    算。胡桃同样先 `ToOffset(serverTimeZoneOffset)` 再取 `.Date`。
*    否则 06:00 开池时，"第 8 天 05:00" 会被多算进窗口。
*
* ## 缺少元数据时的行为
* `entries` 为空（未下载元数据 / 文件损坏）时返回 false ——
* **退化为原有的"按周几"行为**，即与修复前一致，不会因为读不到排期就把
* 全部素材都判成开放（那会让日历在一整周里显示错误的全开放）。
* */
object GachaMaterialOpenWindow {

    //窗口长度(天),与胡桃 AllMaterialsOpenWindowLengthInDays 一致
    const val WINDOW_LENGTH_IN_DAYS = 7

    //触发窗口的卡池类型:301 角色活动祈愿 / 400 特殊角色活动祈愿
    private val triggerTypes = setOf(301, 400)

    /*
    * 指定服务器日期是否落在某个"角色卡池开启后全素材开放"窗口内
    *
    * @param events  已解析起止时间的卡池事件(可为空)
    * @param date    待判定的服务器本地日期
    * @param zoneOffset 服务器时区偏移(GMT+8)
    * */
    fun isDateInOpenWindow(
        events: List<GachaEventEntry>,
        date: LocalDate,
        zoneOffset: ZoneOffset = ZoneOffset.ofHours(8)
    ): Boolean = findActiveWindowSource(events, date, zoneOffset) != null

    /*
    * 取"当前生效的窗口来源"卡池名,供 UI 说明"为何今天全开放"。
    *
    * ⚠️ 这是窗口判定的**唯一实现**,`isDateInOpenWindow` 只是它的布尔包装 ——
    *    避免两处各写一遍日期比较而在将来改动时产生分歧(一处改了另一处没改,
    *    会出现"日历显示全开放但说明文案说不出原因"这种自相矛盾)。
    *
    * 一个日期理论上可能同时落在多个窗口内(叠池),取 **From 最晚**的那个 ——
    * 用户关心的是"最近一次开池",不是历史。
    * */
    fun findActiveWindowSource(
        events: List<GachaEventEntry>,
        date: LocalDate,
        zoneOffset: ZoneOffset = ZoneOffset.ofHours(8)
    ): GachaEventData? = events
        .filter { entry ->
            entry.event.Type in triggerTypes && isInWindow(entry.fromMillis, date, zoneOffset)
        }
        .maxByOrNull { it.fromMillis }
        ?.event

    /*
    * 单条事件的窗口判定: [From 当日, From 当日 + 7 天)
    * 按"服务器日期"比较,不是按 7×24 小时 —— 详见文件头注释。
    * */
    private fun isInWindow(fromMillis: Long, date: LocalDate, zoneOffset: ZoneOffset): Boolean {
        val fromDate = Instant.ofEpochMilli(fromMillis)
            .atZone(zoneOffset)
            .toLocalDate()

        return date >= fromDate && date < fromDate.plusDays(WINDOW_LENGTH_IN_DAYS.toLong())
    }
}
