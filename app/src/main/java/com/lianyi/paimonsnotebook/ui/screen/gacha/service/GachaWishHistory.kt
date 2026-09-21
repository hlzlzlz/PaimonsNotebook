package com.lianyi.paimonsnotebook.ui.screen.gacha.service

import com.lianyi.paimonsnotebook.common.database.gacha.entity.GachaItems

/*
* 祈愿出金历史统计
*
* 现有 GachaPityCalculator 只给"汇总"(总抽数/出金总数/距上个五星),
* 无法回答"我这次出金花了多少抽""最近是不是越来越非" —— 也就是欧非走势。
*
* 数据完全在本地 gacha_items 表里,不需要任何新接口。
*
* ⚠️ 抽数口径:每件**五星**记录算一个"金",它在池内的序号即消耗抽数。
*    第一个五星前面若没有记录(用户从中间开始导入),其消耗抽数会偏小 ——
*    这是数据本身的限制,无法从已有记录推断,故保留原值不做修正,
*    但由调用方通过 isComplete 判断是否提示用户。
* */
object GachaWishHistory {

    data class OrangeEntry(
        //该五星名称
        val name: String,
        val itemId: Int,
        //第几个五星(从 1 开始)
        val index: Int,
        //本次出金消耗的抽数(含这一抽)
        val pullsUsed: Int,
        //时间
        val time: String,
        //是否UP(局部池无法判定时给 null)
        val isUp: Boolean?,
        //是否处于大保底状态出的(上个五星非UP)
        val wasGuaranteed: Boolean,
        //相对该池均值:正数表示比平均更非
        val deltaFromAverage: Int
    )

    data class PoolHistory(
        val pool: GachaPityCalculator.WishPool,
        val entries: List<OrangeEntry>,
        //平均出金抽数(用于判断欧非)
        val averagePulls: Double,
        //最非的一次(抽数最多)
        val worstPulls: Int,
        //最欧的一次
        val bestPulls: Int,
        /*
        * 记录是否完整
        * 数据不完整(首个五星前缺记录、或记录总数为 0)时,统计口径会有偏差,
        * UI 应据此提示而不是当作精确值展示。
        * */
        val isComplete: Boolean
    )

    /*
    * 构建各池的出金历史
    *
    * 与 GachaPityCalculator.calculate 使用**同一套**排序与池划分规则,
    * 避免两处统计口径不一致(那会让用户看到互相矛盾的数字)。
    * */
    fun build(
        records: List<GachaItems>,
        events: List<com.lianyi.paimonsnotebook.common.web.hutao.genshin.gacha_event.GachaEventEntry>
    ): List<PoolHistory> {
        //排序规则与 GachaPityCalculator 保持一致
        val ordered = records.sortedWith(
            compareBy({ it.time }, { it.id.toLongOrNull() ?: 0L })
        )

        return GachaPityCalculator.WishPool.entries.mapNotNull { pool ->
            val poolRecords = ordered.filter { it.uigf_gacha_type in pool.uigfTypes }

            if (poolRecords.isEmpty()) {
                return@mapNotNull null
            }

            val entries = mutableListOf<OrangeEntry>()
            var counter = 0
            var isUpLast = true //首个五星之前无信息,按"非大保底"起算
            var firstOrangeIndexInRecords: Int? = null

            poolRecords.forEachIndexed { recordIndex, record ->
                counter++

                val rank = record.rank_type.toIntOrNull() ?: 3
                if (rank != 5) return@forEachIndexed

                firstOrangeIndexInRecords = firstOrangeIndexInRecords ?: recordIndex

                val isUp = isUpOrange(record, pool, events)

                entries += OrangeEntry(
                    name = record.name,
                    itemId = record.item_id.toIntOrNull() ?: 0,
                    index = entries.size + 1,
                    pullsUsed = counter,
                    time = record.time,
                    isUp = isUp,
                    wasGuaranteed = !isUpLast,
                    deltaFromAverage = 0 //占位,算完均值后填充
                )

                //大保底判定:下个五星是否处于保底,取决于本次是否歪
                isUpLast = isUp == true

                counter = 0
            }

            if (entries.isEmpty()) {
                return@mapNotNull null
            }

            val average = entries.map { it.pullsUsed }.average()

            //回填相对均值的偏差(需要先算出均值)
            val filled = entries.map {
                it.copy(deltaFromAverage = (it.pullsUsed - average).toInt())
            }

            PoolHistory(
                pool = pool,
                entries = filled,
                averagePulls = average,
                worstPulls = entries.maxOf { it.pullsUsed },
                bestPulls = entries.minOf { it.pullsUsed },
                /*
                * 完整性:首个五星之前有记录被跳过(即首个五星不是第 1 抽)
                * 就说明用户是从中间开始记录的,首条的 pullsUsed 不可信。
                * */
                isComplete = firstOrangeIndexInRecords == 0
            )
        }
    }

    /*
    * UP 判定
    * 与 GachaPityCalculator 相同的规则:记录时间落入该池事件区间且 itemId 在 UpOrangeList
    * 无该池事件数据时返回 null(无法判定),而不是谎报 false(=歪了)。
    * */
    private fun isUpOrange(
        record: GachaItems,
        pool: GachaPityCalculator.WishPool,
        events: List<com.lianyi.paimonsnotebook.common.web.hutao.genshin.gacha_event.GachaEventEntry>
    ): Boolean? {
        val itemId = record.item_id.toIntOrNull() ?: return null
        val time = com.lianyi.paimonsnotebook.common.web.hutao.genshin.gacha_event.GachaEventData
            .parseCommonTime(record.time) ?: return null

        val type = pool.uigfTypes.first().toIntOrNull() ?: return null

        //不限定的池(常驻/新手)没有 UP 概念
        if (!pool.hasGuarantee) return null

        val event = events.firstOrNull {
            it.event.Type == type && it.fromMillis <= time && time <= it.toMillis
        } ?: return null

        return event.event.UpOrangeList?.contains(itemId)
    }

    /*
    * 欧非评价
    * 以池的官方保底阈值为基准:消耗抽数越低越欧。
    * 阈值用 pool.orangeThreshold(角色 90 / 武器 80)而非平均值 ——
    * 平均值受个人运气影响,拿它当基准会自我循环。
    * */
    fun luckLabel(pullsUsed: Int, threshold: Int): String {
        if (threshold <= 0) return ""

        val ratio = pullsUsed.toDouble() / threshold

        return when {
            ratio <= 0.3 -> "欧皇"
            ratio <= 0.5 -> "很欧"
            ratio <= 0.75 -> "不错"
            ratio < 1.0 -> "偏非"
            else -> "吃满保底"
        }
    }
}
