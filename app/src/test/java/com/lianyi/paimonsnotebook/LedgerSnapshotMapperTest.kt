package com.lianyi.paimonsnotebook

import com.lianyi.paimonsnotebook.common.util.metadata.genshin.ledger.LedgerSnapshotMapper
import com.lianyi.paimonsnotebook.common.web.hoyolab.takumi.game_record.ledger.LedgerData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/*
* 札记月度快照构建的回归测试
*
* 重点钉住年份推导:接口的 month 只有 1~12 不带年份,
* 跨年翻看(今年 1 月看去年 12 月)时年份必须来自 date 字段,
* 否则历史快照会与今年的同月撞主键。
* */
class LedgerSnapshotMapperTest {

    private fun ledger(
        month: Int = 9,
        date: String = "2025-09-01 00:00:00",
        primogems: Int = 12345,
        mora: Int = 678901,
        groupBy: List<LedgerData.GroupBy> = emptyList()
    ) = LedgerData(
        uid = 100000001,
        region = "cn_gf01",
        nickname = "旅行者",
        date = date,
        month = month,
        optional_month = listOf(9, 8, 7, 6),
        day_data = LedgerData.DayData(100, 2000, 90, 1800),
        month_data = LedgerData.MonthData(
            current_primogems = primogems,
            current_mora = mora,
            last_primogems = primogems - 100,
            last_mora = mora - 200,
            current_primogems_level = 3,
            primogems_rate = 0,
            mora_rate = 0,
            group_by = groupBy
        )
    )

    @Test
    fun 年份从date字段推导() {
        val snapshot = LedgerSnapshotMapper.toSnapshot(
            ledger(month = 12, date = "2024-12-01 00:00:00"),
            savedAt = 1_700_000_000_000L
        )

        assertEquals(2024, snapshot.year)
        assertEquals(12, snapshot.month)
    }

    @Test
    fun 跨年月份不会撞主键() {
        //去年 12 月与今年 12 月是两条不同的记录
        val lastYear = LedgerSnapshotMapper.toSnapshot(
            ledger(month = 12, date = "2024-12-01 00:00:00"), 1L
        )
        val thisYear = LedgerSnapshotMapper.toSnapshot(
            ledger(month = 12, date = "2025-12-01 00:00:00"), 2L
        )

        assertTrue(
            "(year, month) 必须不同,否则按月覆盖会丢历史",
            lastYear.year != thisYear.year
        )
    }

    @Test
    fun date格式异常时回退本地年份() {
        //date 解析失败不应崩溃;回退到构建时的本地年份(测试环境为当前年)
        val snapshot = LedgerSnapshotMapper.toSnapshot(
            ledger(date = "bad-date"),
            savedAt = 1L
        )

        val expectedYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        assertEquals(expectedYear, snapshot.year)
    }

    @Test
    fun 空date时回退本地年份() {
        val snapshot = LedgerSnapshotMapper.toSnapshot(ledger(date = ""), 1L)
        val expectedYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        assertEquals(expectedYear, snapshot.year)
    }

    @Test
    fun 收支数值原样保留() {
        val snapshot = LedgerSnapshotMapper.toSnapshot(
            ledger(primogems = 1600, mora = 2000000),
            savedAt = 1L
        )

        assertEquals(1600, snapshot.current_primogems)
        assertEquals(2000000, snapshot.current_mora)
        assertEquals(1500, snapshot.last_primogems)
        assertEquals(1999800, snapshot.last_mora)
    }

    @Test
    fun groupBy序列化往返() {
        val groupBy = listOf(
            LedgerData.GroupBy(1, "每日委托", 60, 30),
            LedgerData.GroupBy(2, "深境螺旋", 100, 50)
        )

        val snapshot = LedgerSnapshotMapper.toSnapshot(
            ledger(groupBy = groupBy),
            savedAt = 1L
        )

        val restored = LedgerSnapshotMapper.deserializeGroupBy(snapshot.group_by)

        assertEquals(2, restored.size)
        assertEquals("每日委托", restored[0].action)
        assertEquals(60L, restored[0].num)
        assertEquals(30, restored[0].percent)
        assertEquals("深境螺旋", restored[1].action)
    }

    @Test
    fun 空groupBy往返为空() {
        val snapshot = LedgerSnapshotMapper.toSnapshot(ledger(), 1L)
        assertTrue(LedgerSnapshotMapper.deserializeGroupBy(snapshot.group_by).isEmpty())
    }

    @Test
    fun 损坏的groupBy序列不崩溃() {
        //手改过/损坏的持久化数据不能让读取崩溃
        val restored = LedgerSnapshotMapper.deserializeGroupBy("1,委托,abc,30|bad-entry|||")

        //第一段 num 解析失败 -> action_id=1, num=0 仍可恢复部分信息
        assertTrue(restored.all { it.percent >= 0 })
    }

    @Test
    fun uid与昵称保留() {
        val snapshot = LedgerSnapshotMapper.toSnapshot(ledger(), 1L)

        assertEquals("100000001", snapshot.uid)
        assertEquals("旅行者", snapshot.nickname)
        assertEquals("cn_gf01", snapshot.region)
    }

    @Test
    fun savedAt原样写入() {
        val snapshot = LedgerSnapshotMapper.toSnapshot(ledger(), 987654321L)
        assertEquals(987654321L, snapshot.saved_at)
    }
}
