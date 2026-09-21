package com.lianyi.paimonsnotebook

import com.lianyi.paimonsnotebook.common.database.gacha.entity.GachaItems
import com.lianyi.paimonsnotebook.ui.screen.gacha.service.GachaPityCalculator
import com.lianyi.paimonsnotebook.ui.screen.gacha.service.GachaWishHistory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/*
* 祈愿出金历史的回归测试
*
* 核心是"抽数口径"与"记录完整性"两条:
* 出金消耗抽数算错会让整个欧非展示失真,而完整性判断决定了
* UI 该不该把数字当精确值展示。
* */
class GachaWishHistoryTest {

    private var idSeed = 1

    private fun item(
        rank: Int,
        name: String = "物品",
        type: String = "301",
        time: String = "2025-09-01 12:00:00"
    ) = GachaItems(
        id = (idSeed++).toString(),
        uid = "100000001",
        gacha_type = type,
        item_id = "10000$rank",
        count = "1",
        time = time,
        name = name,
        lang = "zh-cn",
        item_type = "角色",
        rank_type = rank.toString(),
        uigf_gacha_type = type
    )

    private fun history(records: List<GachaItems>) =
        GachaWishHistory.build(records, emptyList())

    @Test
    fun 按出金次数统计消耗抽数() {
        // 3 抽后出金 -> 5 抽后出金
        val records = listOf(
            item(3), item(3), item(3), item(5, "金1"),
            item(3), item(3), item(3), item(3), item(3), item(5, "金2")
        )

        val pool = history(records).first { it.pool == GachaPityCalculator.WishPool.Character }

        assertEquals(2, pool.entries.size)
        assertEquals(4, pool.entries[0].pullsUsed)
        assertEquals(6, pool.entries[1].pullsUsed)
    }

    @Test
    fun 每件五星都记一条() {
        val records = listOf(
            item(5, "金1"), item(3), item(5, "金2"), item(5, "金3")
        )

        val pool = history(records).first()

        assertEquals(3, pool.entries.size)
        assertEquals(listOf(1, 2, 3), pool.entries.map { it.index })
        assertEquals(listOf("金1", "金2", "金3"), pool.entries.map { it.name })
    }

    @Test
    fun 平均与最非最欧() {
        // 出金消耗:2 抽、6 抽、4 抽
        val records = listOf(
            item(3), item(5, "金1"),
            item(3), item(3), item(3), item(3), item(3), item(5, "金2"),
            item(3), item(3), item(3), item(5, "金3")
        )

        val pool = history(records).first()

        assertEquals(2, pool.bestPulls)
        assertEquals(6, pool.worstPulls)
        assertEquals(4.0, pool.averagePulls, 0.001)
    }

    @Test
    fun 相对均值偏差回填正确() {
        // 消耗 2 与 6,均值 4 -> 偏差 -2 与 +2
        val records = listOf(
            item(3), item(5, "金1"),
            item(3), item(3), item(3), item(3), item(3), item(5, "金2")
        )

        val pool = history(records).first()

        assertEquals(-2, pool.entries[0].deltaFromAverage)
        assertEquals(2, pool.entries[1].deltaFromAverage)
    }

    @Test
    fun 首个五星即首抽才算完整() {
        // 第一条就是五星 -> 完整
        val complete = history(listOf(item(5, "金1"), item(3), item(5, "金2"))).first()
        assertTrue(complete.isComplete)
    }

    @Test
    fun 首个五星前有记录则标记不完整() {
        // 前 3 抽是三星,说明用户从中间开始记录,首条的消耗抽数不可信
        val incomplete = history(listOf(item(3), item(3), item(3), item(5, "金1"))).first()

        assertFalse(
            "从中间开始记录时不能声称统计完整",
            incomplete.isComplete
        )
    }

    @Test
    fun 各池分开统计() {
        val records = listOf(
            item(5, "角色金", type = "301"),
            item(5, "武器金", type = "302"),
            item(5, "常驻金", type = "200")
        )

        val pools = history(records)

        assertEquals(3, pools.size)
        assertEquals(
            setOf("角色金", "武器金", "常驻金"),
            pools.flatMap { it.entries }.map { it.name }.toSet()
        )
    }

    @Test
    fun 空记录返回空列表() {
        assertTrue(history(emptyList()).isEmpty())
    }

    @Test
    fun 只有三星的池不产生历史() {
        // 无五星 -> 该池不出现在结果里
        assertTrue(history(listOf(item(3), item(3))).isEmpty())
    }

    @Test
    fun 常驻池不判定UP() {
        // 常驻无 UP 概念,isUp 必须为 null 而不是谎报 false
        val pool = history(listOf(item(5, "常驻金", type = "200"))).first()

        assertNull(
            "无 UP 机制的池不能报 false(=歪了)",
            pool.entries[0].isUp
        )
    }

    @Test
    fun 无事件数据时不判定UP() {
        // events 为空 -> 无法判定,应为 null
        val pool = history(listOf(item(5, "角色金", type = "301"))).first()

        assertNull(pool.entries[0].isUp)
    }

    @Test
    fun 四星不影响出金计数() {
        val records = listOf(
            item(3), item(4), item(3), item(5, "金1")
        )

        val pool = history(records).first()

        assertEquals(1, pool.entries.size)
        assertEquals(4, pool.entries[0].pullsUsed)
    }

    @Test
    fun 欧非评价按保底阈值分档() {
        // 角色池阈值 90
        assertEquals("欧皇", GachaWishHistory.luckLabel(10, 90))
        assertEquals("很欧", GachaWishHistory.luckLabel(40, 90))
        assertEquals("不错", GachaWishHistory.luckLabel(60, 90))
        assertEquals("偏非", GachaWishHistory.luckLabel(80, 90))
        assertEquals("吃满保底", GachaWishHistory.luckLabel(90, 90))
    }

    @Test
    fun 欧非评价对非法阈值返回空() {
        assertEquals("", GachaWishHistory.luckLabel(10, 0))
        assertEquals("", GachaWishHistory.luckLabel(10, -1))
    }

    @Test
    fun 与保底计算器口径一致() {
        /*
        * 同一份记录喂给两个统计,总出金数必须相等 ——
        * 否则用户在保底页与历史页会看到互相矛盾的数字。
        * */
        val records = listOf(
            item(3), item(5, "金1"), item(3), item(3), item(5, "金2")
        )

        val pityOrange = GachaPityCalculator.calculate(records, emptyList())
            .sumOf { it.totalOrange }
        val historyOrange = history(records).sumOf { it.entries.size }

        assertEquals(pityOrange, historyOrange)
    }
}
