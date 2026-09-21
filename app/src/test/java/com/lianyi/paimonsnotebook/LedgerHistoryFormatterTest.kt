package com.lianyi.paimonsnotebook

import com.lianyi.paimonsnotebook.common.database.ledger.entity.LedgerMonthSnapshot
import com.lianyi.paimonsnotebook.common.util.metadata.genshin.ledger.LedgerHistoryFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/*
* 札记历史列表构建的回归测试
*
* 核心是把"月份不连续时不显示环比"钉死 ——
* 这是最容易被后人当成"少写了功能"而改坏的地方。
* */
class LedgerHistoryFormatterTest {

    private fun snapshot(
        year: Int,
        month: Int,
        primogems: Int = 1000,
        mora: Int = 10000
    ) = LedgerMonthSnapshot(
        uid = "100000001",
        month = month,
        year = year,
        nickname = "旅行者",
        region = "cn_gf01",
        current_primogems = primogems,
        current_mora = mora,
        last_primogems = 0,
        last_mora = 0,
        group_by = "",
        saved_at = 0L
    )

    @Test
    fun 月份标签格式正确() {
        assertEquals("2025年9月", LedgerHistoryFormatter.monthLabel(2025, 9))
        assertEquals("2026年12月", LedgerHistoryFormatter.monthLabel(2026, 12))
    }

    @Test
    fun 连续月份显示环比() {
        //倒序:9月在前,8月在后
        val rows = LedgerHistoryFormatter.buildRows(
            listOf(
                snapshot(2025, 9, primogems = 1200, mora = 20000),
                snapshot(2025, 8, primogems = 1000, mora = 15000)
            )
        )

        assertEquals(1200 - 1000, rows[0].primogemsDelta)
        assertEquals(20000 - 15000, rows[0].moraDelta)
    }

    @Test
    fun 跨年连续时显示环比() {
        //2026年1月 的上一月是 2025年12月
        val rows = LedgerHistoryFormatter.buildRows(
            listOf(
                snapshot(2026, 1, primogems = 900),
                snapshot(2025, 12, primogems = 1500)
            )
        )

        assertEquals(900 - 1500, rows[0].primogemsDelta)
    }

    @Test
    fun 月份不连续时不显示环比() {
        //8月 -> 11月 中间空档:不能把两端相减当成环比
        val rows = LedgerHistoryFormatter.buildRows(
            listOf(
                snapshot(2025, 11, primogems = 3000),
                snapshot(2025, 8, primogems = 12000)
            )
        )

        assertNull(
            "月份有缺口时必须留空,否则会显示误导性的暴跌",
            rows[0].primogemsDelta
        )
        assertNull(rows[0].moraDelta)
    }

    @Test
    fun 空档不显示但两端本身仍展示() {
        val rows = LedgerHistoryFormatter.buildRows(
            listOf(
                snapshot(2025, 11, primogems = 3000),
                snapshot(2025, 8, primogems = 12000)
            )
        )

        assertEquals(2, rows.size)
        assertEquals(3000, rows[0].primogems)
        assertEquals(12000, rows[1].primogems)
        assertNull(rows[1].primogemsDelta) //最老一条没有上一月
    }

    @Test
    fun 跨年缺口同样不显示环比() {
        //2026年3月 与 2025年11月 不连续
        val rows = LedgerHistoryFormatter.buildRows(
            listOf(
                snapshot(2026, 3, primogems = 500),
                snapshot(2025, 11, primogems = 5000)
            )
        )

        assertNull(rows[0].primogemsDelta)
    }

    @Test
    fun 最老一条没有环比() {
        val rows = LedgerHistoryFormatter.buildRows(listOf(snapshot(2025, 9)))

        assertEquals(1, rows.size)
        assertNull(rows[0].primogemsDelta)
        assertNull(rows[0].moraDelta)
    }

    @Test
    fun 空列表返回空() {
        assertTrue(LedgerHistoryFormatter.buildRows(emptyList()).isEmpty())
    }

    @Test
    fun 环比文案() {
        assertEquals("较上月 +200", LedgerHistoryFormatter.deltaText(200))
        assertEquals("较上月 -300", LedgerHistoryFormatter.deltaText(-300))
        assertEquals("较上月 持平", LedgerHistoryFormatter.deltaText(0))
        assertEquals("", LedgerHistoryFormatter.deltaText(null))
    }

    @Test
    fun 相邻月份判定() {
        assertTrue(LedgerHistoryFormatter.isPreviousMonth(2025, 8, 2025, 9))
        assertTrue(LedgerHistoryFormatter.isPreviousMonth(2025, 12, 2026, 1))
        assertTrue(!LedgerHistoryFormatter.isPreviousMonth(2025, 7, 2025, 9))
        assertTrue(!LedgerHistoryFormatter.isPreviousMonth(2025, 12, 2025, 1))
        assertTrue(!LedgerHistoryFormatter.isPreviousMonth(2024, 12, 2026, 1))
    }
}
