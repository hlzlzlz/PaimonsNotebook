package com.lianyi.paimonsnotebook

import com.lianyi.paimonsnotebook.common.util.weekly.GachaMaterialOpenWindow
import com.lianyi.paimonsnotebook.common.web.hutao.genshin.gacha_event.GachaEventData
import com.lianyi.paimonsnotebook.common.web.hutao.genshin.gacha_event.GachaEventEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

/*
* 卡池开启后「全素材本开放」窗口判定
*
* 移植自胡桃 GachaEventSchedule.cs（6d521208f）。该逻辑的坑都在**边界**上：
*   - 窗口是左闭右开 [From, From+7)
*   - 只认 301/400，武器池/集录不触发
*   - 按"服务器日期"而非 7×24 小时比较
* 故断言全部对准边界与类型过滤。
* */
class GachaMaterialOpenWindowTest {

    private val gmt8 = ZoneOffset.ofHours(8)

    //构造一条卡池事件。from/to 用 "yyyy-MM-ddTHH:mm:ss+08:00" 形式
    private fun entry(
        type: Int,
        from: String,
        to: String = "2099-01-01T17:59:00+08:00",
        name: String = "测试池"
    ): GachaEventEntry {
        val data = GachaEventData(
            Name = name,
            Version = "1.0",
            Order = 1,
            Banner = "",
            Banner2 = "",
            From = from,
            To = to,
            Type = type,
            UpOrangeList = emptyList(),
            UpPurpleList = emptyList()
        )

        return GachaEventEntry(
            event = data,
            fromMillis = GachaEventData.parseEventTime(from)!!,
            toMillis = GachaEventData.parseEventTime(to)!!
        )
    }

    //2026-08-12 开池(实测元数据里 301 池的 From 就是这天 06:00)
    private val openDay = LocalDate.of(2026, 8, 12)

    @Test
    fun `开池当天在全开放窗口内`() {
        val events = listOf(entry(301, "2026-08-12T06:00:00+08:00"))

        assertTrue(GachaMaterialOpenWindow.isDateInOpenWindow(events, openDay, gmt8))
    }

    @Test
    fun `开池第7天仍在窗口内`() {
        val events = listOf(entry(301, "2026-08-12T06:00:00+08:00"))

        //8-12 起算第1天 => 第7天是 8-18
        assertTrue(GachaMaterialOpenWindow.isDateInOpenWindow(events, LocalDate.of(2026, 8, 18), gmt8))
    }

    @Test
    fun `开池第8天不在窗口内_左闭右开`() {
        val events = listOf(entry(301, "2026-08-12T06:00:00+08:00"))

        //8-19 已出窗口
        assertFalse(GachaMaterialOpenWindow.isDateInOpenWindow(events, LocalDate.of(2026, 8, 19), gmt8))
    }

    @Test
    fun `开池前一天不在窗口内`() {
        val events = listOf(entry(301, "2026-08-12T06:00:00+08:00"))

        assertFalse(GachaMaterialOpenWindow.isDateInOpenWindow(events, LocalDate.of(2026, 8, 11), gmt8))
    }

    @Test
    fun `武器池302不触发全开放窗口`() {
        //即便日期落在 302 池的 7 天内,也不该判定为开放
        val events = listOf(entry(302, "2026-08-12T06:00:00+08:00"))

        assertFalse(GachaMaterialOpenWindow.isDateInOpenWindow(events, openDay, gmt8))
    }

    @Test
    fun `集录500不触发全开放窗口`() {
        val events = listOf(entry(500, "2026-08-12T06:00:00+08:00"))

        assertFalse(GachaMaterialOpenWindow.isDateInOpenWindow(events, openDay, gmt8))
    }

    @Test
    fun `特殊角色活动祈愿400触发全开放窗口`() {
        val events = listOf(entry(400, "2026-08-12T06:00:00+08:00"))

        assertTrue(GachaMaterialOpenWindow.isDateInOpenWindow(events, openDay, gmt8))
    }

    @Test
    fun `无元数据时退化为不开放`() {
        //空列表必须返回 false(退化成原有的按周几行为),不能把整周都判成开放
        assertFalse(GachaMaterialOpenWindow.isDateInOpenWindow(emptyList(), openDay, gmt8))
    }

    @Test
    fun `窗口判定按服务器日期而非24小时`() {
        /*
        * From = 8-12 06:00。若按 7×24 小时算,窗口到 8-19 06:00 才结束,
        * 于是 8-19 凌晨会被误判为开放。按日期语义则 8-19 全天都不在窗口内。
        * */
        val events = listOf(entry(301, "2026-08-12T06:00:00+08:00"))

        assertFalse(GachaMaterialOpenWindow.isDateInOpenWindow(events, LocalDate.of(2026, 8, 19), gmt8))
    }

    @Test
    fun `叠池时取From最晚的那个作为窗口来源`() {
        val old = entry(301, "2026-08-12T06:00:00+08:00", name = "旧池")
        val recent = entry(400, "2026-08-15T18:00:00+08:00", name = "新池")

        //8-16 同时落在两个窗口内,应取新池
        val source = GachaMaterialOpenWindow.findActiveWindowSource(
            listOf(old, recent),
            LocalDate.of(2026, 8, 16),
            gmt8
        )

        assertEquals("新池", source?.Name)
    }

    @Test
    fun `非窗口期找不到窗口来源`() {
        val events = listOf(entry(301, "2026-08-12T06:00:00+08:00"))

        assertNull(
            GachaMaterialOpenWindow.findActiveWindowSource(events, LocalDate.of(2026, 9, 1), gmt8)
        )
    }

    @Test
    fun `窗口来源忽略武器池`() {
        val events = listOf(entry(302, "2026-08-12T06:00:00+08:00", name = "武器池"))

        assertNull(GachaMaterialOpenWindow.findActiveWindowSource(events, openDay, gmt8))
    }

    /*
    * 「一周七格」-> 真实日期的映射。
    *
    * ⚠️ 这是最容易**静默错位**的地方:格子是"星期几",而卡池窗口按日期算。
    *    若映射错了(例如把周日算到下周),整周的高亮都会偏 —— UI 上只表现为
    *    "某个格子颜色不对",极难发现。故单独钉住。
    * */
    @Test
    fun `周三所在周的七格映射到本周一到周日`() {
        //2026-08-12 是周三
        val wednesday = LocalDate.of(2026, 8, 12)
        assertEquals(java.time.DayOfWeek.WEDNESDAY, wednesday.dayOfWeek)

        //index 0..6 -> 8-10(周一) .. 8-16(周日)
        assertEquals(LocalDate.of(2026, 8, 10), GachaMaterialOpenWindow.dateOfDayCell(wednesday, 0))
        assertEquals(LocalDate.of(2026, 8, 12), GachaMaterialOpenWindow.dateOfDayCell(wednesday, 2))
        assertEquals(LocalDate.of(2026, 8, 16), GachaMaterialOpenWindow.dateOfDayCell(wednesday, 6))
    }

    /*
    * 周日必须**回退**到本周周一,而不是前进到下周 ——
    * 这是 ISO 周(周一起算)与"周日为一周之始"两种习惯的交界处,最易写错。
    * 若实现用 `plusDays` 直接从周日算,index 0 会得到下周一(8-17),整周错位。
    * */
    @Test
    fun `周日所在周的七格仍映射到本周而非下周`() {
        //2026-08-16 是周日
        val sunday = LocalDate.of(2026, 8, 16)
        assertEquals(java.time.DayOfWeek.SUNDAY, sunday.dayOfWeek)

        //index 0 应为本周一 8-10(不是下周 8-17)
        assertEquals(LocalDate.of(2026, 8, 10), GachaMaterialOpenWindow.dateOfDayCell(sunday, 0))
        //index 6 应回到它自己
        assertEquals(LocalDate.of(2026, 8, 16), GachaMaterialOpenWindow.dateOfDayCell(sunday, 6))
    }

    @Test
    fun `周一所在周的七格映射正确`() {
        val monday = LocalDate.of(2026, 8, 10)
        assertEquals(java.time.DayOfWeek.MONDAY, monday.dayOfWeek)

        assertEquals(monday, GachaMaterialOpenWindow.dateOfDayCell(monday, 0))
        assertEquals(LocalDate.of(2026, 8, 16), GachaMaterialOpenWindow.dateOfDayCell(monday, 6))
    }

    /*
    * 跨月边界:一周可能横跨两个月,映射不能把日期算丢。
    * */
    @Test
    fun `跨月的一周映射仍连续`() {
        //2026-08-31 是周一,该周跨到 9 月
        val monday = LocalDate.of(2026, 8, 31)
        assertEquals(java.time.DayOfWeek.MONDAY, monday.dayOfWeek)

        assertEquals(LocalDate.of(2026, 8, 31), GachaMaterialOpenWindow.dateOfDayCell(monday, 0))
        assertEquals(LocalDate.of(2026, 9, 1), GachaMaterialOpenWindow.dateOfDayCell(monday, 1))
        assertEquals(LocalDate.of(2026, 9, 6), GachaMaterialOpenWindow.dateOfDayCell(monday, 6))
    }
}
