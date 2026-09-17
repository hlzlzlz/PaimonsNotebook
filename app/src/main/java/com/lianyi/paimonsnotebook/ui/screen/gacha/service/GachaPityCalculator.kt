package com.lianyi.paimonsnotebook.ui.screen.gacha.service

import com.lianyi.paimonsnotebook.common.database.gacha.entity.GachaItems
import com.lianyi.paimonsnotebook.common.util.metadata.genshin.uigf.UIGFHelper
import com.lianyi.paimonsnotebook.common.web.hutao.genshin.gacha_event.GachaEventData
import com.lianyi.paimonsnotebook.common.web.hutao.genshin.gacha_event.GachaEventEntry
import java.util.concurrent.TimeUnit
import kotlin.math.min

/*
* 祈愿保底统计与复刻倒计时
*
* 角色活动祈愿的301/400两种记录属于同一卡池,统计时合并
* isUp判定:记录时间落入对应池子类型的GachaEvent区间且itemId在UpOrangeList中
* 软保底概率为本地估算模型(非全服数据),UI需标注"估算"
* */
object GachaPityCalculator {

    enum class WishPool(
        val label: String,
        //属于该池的uigf祈愿类型
        val uigfTypes: List<String>,
        //出金阈值(抽)
        val orangeThreshold: Int,
        //软保底起始抽数
        val softPityStart: Int,
        //是否存在限定UP保底机制
        val hasGuarantee: Boolean
    ) {
        Character("角色活动祈愿", listOf("301", "400"), 90, 74, true),
        Weapon("武器活动祈愿", listOf("302"), 80, 65, true),
        Standard("常驻祈愿", listOf("200"), 90, 74, false),
        //集录祈愿的UIGF类型码是500(见UIGFHelper.CHRONICLED_WISH),不是305;
        //写成305会导致该池过滤结果恒为空,保底页永远不显示集录卡片
        Chronicled("集录祈愿", listOf(UIGFHelper.CHRONICLED_WISH), 90, 74, true),
        Beginner("初行者祈愿", listOf("100"), 90, 74, false)
    }

    data class PoolPity(
        val pool: WishPool,
        val totalPulls: Int,
        //距上个五星的抽数(当前五星保底进度)
        val pullsSinceOrange: Int,
        //距上个四星的抽数
        val pullsSincePurple: Int,
        //出金总数
        val totalOrange: Int,
        //上个五星信息
        val lastOrangeName: String,
        val lastOrangeTime: String,
        //上个五星是否UP,用于判定当前是否处于大保底
        val lastOrangeWasUp: Boolean,
        //下一抽出金概率(本地估算)
        val nextPullOrangeProbability: Double,
        //预计还需抽数出金(本地估算)
        val expectedPullsToOrange: Double
    ) {
        //大保底:上个五星非UP且该池有保底机制
        val isGuaranteed: Boolean
            get() = pool.hasGuarantee && totalOrange > 0 && !lastOrangeWasUp
    }

    fun calculate(records: List<GachaItems>, events: List<GachaEventEntry>): List<PoolPity> {
        //按记录id的数值升序稳定排序(id为数字串,字符串比较在位数变化时出错)
        val ordered = records.sortedWith(
            compareBy({ it.time }, { it.id.toLongOrNull() ?: 0L })
        )

        return WishPool.entries.mapNotNull { pool ->
            val poolRecords = ordered.filter { it.uigf_gacha_type in pool.uigfTypes }

            if (poolRecords.isEmpty()) {
                return@mapNotNull null
            }

            var pullsSinceOrange = 0
            var pullsSincePurple = 0
            var totalOrange = 0
            var lastOrangeName = ""
            var lastOrangeTime = ""
            var lastOrangeWasUp = false

            poolRecords.forEach { record ->
                pullsSinceOrange++
                pullsSincePurple++

                val rank = record.rank_type.toIntOrNull() ?: 3

                if (rank <= 4) {
                    pullsSincePurple = 0
                }

                if (rank == 5) {
                    totalOrange++
                    lastOrangeName = record.name
                    lastOrangeTime = record.time
                    lastOrangeWasUp = isUpOrange(record, pool, events)

                    pullsSinceOrange = 0
                }
            }

            val probability = softPityProbability(pool, pullsSinceOrange)
            val expected = expectedPullsToOrange(pool, pullsSinceOrange)

            PoolPity(
                pool = pool,
                totalPulls = poolRecords.size,
                pullsSinceOrange = pullsSinceOrange,
                pullsSincePurple = pullsSincePurple,
                totalOrange = totalOrange,
                lastOrangeName = lastOrangeName,
                lastOrangeTime = lastOrangeTime,
                lastOrangeWasUp = lastOrangeWasUp,
                nextPullOrangeProbability = probability,
                expectedPullsToOrange = expected
            )
        }
    }

    //记录对应池子类型的当期事件中是否存在该五星
    private fun isUpOrange(
        record: GachaItems,
        pool: WishPool,
        events: List<GachaEventEntry>
    ): Boolean {
        val itemId = record.item_id.toIntOrNull() ?: return false
        val time = GachaEventData.parseCommonTime(record.time) ?: return false

        //池子的主祈愿类型,与GachaEvent.Type对应
        val type = pool.uigfTypes.first().toIntOrNull() ?: return false

        val event = events.firstOrNull {
            it.event.Type == type && it.fromMillis <= time && time <= it.toMillis
        }

        return event?.event?.UpOrangeList?.contains(itemId) == true
    }

    //第n抽的单抽出金概率(软保底线性增长模型)
    private fun orangeRateForPull(pool: WishPool, n: Int): Double = when {
        n >= pool.orangeThreshold -> 1.0
        n < pool.softPityStart -> 0.00575
        else -> 0.00575 + 0.06 * (n - pool.softPityStart + 1)
    }

    //在当前保底进度下,下一抽出金的条件概率
    private fun softPityProbability(pool: WishPool, pullsSinceOrange: Int): Double {
        val n = pullsSinceOrange + 1

        if (n > pool.orangeThreshold) {
            return 1.0
        }

        return min(orangeRateForPull(pool, n), 1.0)
    }

    //在当前保底进度下,预计出金还需的抽数(按条件分布求期望)
    private fun expectedPullsToOrange(pool: WishPool, pullsSinceOrange: Int): Double {
        var survived = 1.0
        var expected = 0.0
        var mass = 0.0

        var k = 1
        while (pullsSinceOrange + k <= pool.orangeThreshold) {
            val rate = min(orangeRateForPull(pool, pullsSinceOrange + k), 1.0)
            val probability = rate * survived

            expected += k * probability
            mass += probability
            survived *= 1.0 - rate

            k++
        }

        return if (mass <= 0.0) k.toDouble() else expected / mass
    }

    /*
    * 复刻倒计时条目
    * 距每个UP物品上次出现在UP池结束的天数,当前UP中的显示"本期"
    * */
    data class CountdownEntry(
        val itemId: Int,
        //头像还是武器(id>=10000000为角色)
        val isAvatar: Boolean,
        //五星还是四星
        val isOrange: Boolean,
        //距上次UP结束天数,本期为0
        val daysSinceLast: Int,
        //是否处于本期UP池
        val isCurrent: Boolean,
        //上次出现的版本期数文案
        val versionText: String,
        //上次UP结束日期
        val lastTime: String,
        //历史出现次数
        val appearances: Int
    )

    //构建四组复刻倒计时:五星角色/四星角色/五星武器/四星武器
    fun buildCountdown(
        events: List<GachaEventEntry>,
        now: Long = System.currentTimeMillis()
    ): Map<String, List<CountdownEntry>> {
        val entries = mutableMapOf<Int, CountdownEntry>()

        //从最近的池子往回遍历,保证每个物品首个遇到的即最近一次UP
        events.sortedByDescending { it.fromMillis }.forEach { entry ->
            //未开始的池子跳过
            if (entry.fromMillis > now) {
                return@forEach
            }

            val isCurrent = now in entry.fromMillis..entry.toMillis

            listOf(true to entry.event.UpOrangeList, false to entry.event.UpPurpleList)
                .forEach { (isOrange, list) ->
                    list.forEach { itemId ->
                        val existing = entries[itemId]

                        if (existing == null) {
                            entries[itemId] = CountdownEntry(
                                itemId = itemId,
                                isAvatar = itemId >= 10000000,
                                isOrange = isOrange,
                                daysSinceLast = TimeUnit.MILLISECONDS.toDays(
                                    (now - entry.toMillis).coerceAtLeast(0)
                                ).toInt(),
                                isCurrent = isCurrent,
                                versionText = GachaEventData.versionText(entry.event),
                                lastTime = entry.event.To.substringBefore("T"),
                                appearances = 1
                            )
                        } else {
                            entries[itemId] = existing.copy(appearances = existing.appearances + 1)
                        }
                    }
                }
        }

        return entries.values.groupBy {
            when {
                it.isAvatar && it.isOrange -> "五星角色"
                it.isAvatar -> "四星角色"
                it.isOrange -> "五星武器"
                else -> "四星武器"
            }
        }.mapValues { (_, list) ->
            list.sortedWith(
                compareByDescending<CountdownEntry> { it.isCurrent }
                    .thenBy { it.daysSinceLast }
            )
        }
    }
}
