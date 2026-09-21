package com.lianyi.paimonsnotebook

import com.lianyi.paimonsnotebook.common.database.gacha.entity.GachaItems
import com.lianyi.paimonsnotebook.ui.screen.gacha.service.GachaPityCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/*
* 祈愿保底统计的回归测试
*
* 背景:该计算器此前**零测试覆盖**,因此下面这个缺陷长期存在:
*
*   GachaPityCalculator 里四星保底计数写成了 `rank <= 4`,
*   即"三星及以下才重置"。而三星是绝大多数,导致「距上个四星」
*   几乎每抽都被清零、该行恒为 0~1;同时出五星时**不**重置。
*
* 该缺陷是 2026-09-21 词典笔移植过程中发现的
* (见 memory/dictpen-port-plan.md),本文件把它锁死。
* */
class GachaPityCalculatorTest {

    /*
    * 造一条记录
    *
    * rank:星级字符串("3"/"4"/"5")
    * gachaType:uigf 卡池类型(默认 301 = 角色活动祈愿)
    * index:用于生成递增 id 与时间,保证排序稳定
    * */
    private fun record(
        rank: String,
        index: Int,
        gachaType: String = "301",
        name: String = "物品$index"
    ) = GachaItems(
        count = "1",
        gacha_type = gachaType,
        id = "${1000000000000000000L + index}",
        item_id = "1000000$index",
        item_type = "角色",
        lang = "zh-cn",
        name = name,
        rank_type = rank,
        //时间必须递增,calculate 会按 (time, id) 排序
        time = "2026-09-%02d %02d:00:00".format((index % 28) + 1, index % 24),
        uid = "338131141",
        uigf_gacha_type = gachaType
    )

    //取角色活动祈愿的保底结果
    private fun characterPity(records: List<GachaItems>) =
        GachaPityCalculator.calculate(records, emptyList())
            .first { it.pool == GachaPityCalculator.WishPool.Character }

    /*
    * 核心用例:四星计数必须在"出四星或五星"时重置
    *
    * 序列 3,3,3,4,3,3,3,3,3,3 => 距上个四星应为 6(最后 6 抽没出四星以上)。
    * 修复前(rank <= 4)会得到 0,因为每次三星都把它清零了。
    * */
    @Test
    fun `四星计数在出四星后累加`() {
        val ranks = listOf("3", "3", "3", "4", "3", "3", "3", "3", "3", "3")
        val records = ranks.mapIndexed { i, r -> record(r, i) }

        val pity = characterPity(records)

        assertEquals(
            "第4抽出四星后,又抽了6次,距上个四星应为 6",
            6,
            pity.pullsSincePurple
        )
    }

    /*
    * 出五星也必须重置四星计数
    *
    * 五星比四星更"高",当然也要重置。修复前 `rank <= 4` 不会重置。
    * */
    @Test
    fun `四星计数在出五星后重置`() {
        val ranks = listOf("3", "3", "5", "3", "3")
        val records = ranks.mapIndexed { i, r -> record(r, i) }

        val pity = characterPity(records)

        assertEquals("五星后应重置,之后抽了2次,应为 2", 2, pity.pullsSincePurple)
    }

    /*
    * 三星**不**重置四星计数(这是原 bug 的直接表现)
    * */
    @Test
    fun `三星不重置四星计数`() {
        val ranks = listOf("4", "3", "3", "3")
        val records = ranks.mapIndexed { i, r -> record(r, i) }

        val pity = characterPity(records)

        assertEquals("四星后连抽3个三星,应为 3", 3, pity.pullsSincePurple)
    }

    /*
    * 连续三星应一直累加,直到出四星以上
    * */
    @Test
    fun `连续三星持续累加`() {
        val records = List(9) { record("3", it) }

        val pity = characterPity(records)

        assertEquals("9 个三星之间没有四星,应为 9", 9, pity.pullsSincePurple)
    }

    /*
    * 五星计数:出五星重置,四星/三星都不重置
    * */
    @Test
    fun `五星计数只在出五星时重置`() {
        val ranks = listOf("4", "3", "5", "3", "4")
        val records = ranks.mapIndexed { i, r -> record(r, i) }

        val pity = characterPity(records)

        assertEquals("第3抽出五星后又抽2次,应为 2", 2, pity.pullsSinceOrange)
        assertEquals("五星总数应为 1", 1, pity.totalOrange)
        assertEquals("四星计数:五星后抽了 3,4 => 第5抽出四星,应为 0", 0, pity.pullsSincePurple)
    }

    /*
    * 只统计本卡池的记录(301/400 合并,不混入其它池)
    * */
    @Test
    fun `只统计对应卡池的记录`() {
        val records = listOf(
            record("3", 0, gachaType = "301"),
            record("3", 1, gachaType = "302"), // 武器池,不应计入角色池
            record("3", 2, gachaType = "301")
        )

        val pity = characterPity(records)

        assertEquals("角色池应只含 2 条", 2, pity.totalPulls)
        assertEquals(2, pity.pullsSincePurple)
    }

    /*
    * 空记录不应崩(边界)
    * */
    @Test
    fun `空记录不抛异常`() {
        val result = GachaPityCalculator.calculate(emptyList(), emptyList())
        assertTrue("空输入应返回空结果", result.isEmpty())
    }

    /*
    * rank_type 非法时按三星处理,不能崩
    * */
    @Test
    fun `非法星级按三星处理`() {
        val records = listOf(record("x", 0), record("4", 1), record("x", 2))

        val pity = characterPity(records)

        assertEquals("非法值当三星,不重置;第2抽出四星后抽了1次 => 1", 1, pity.pullsSincePurple)
    }
}
