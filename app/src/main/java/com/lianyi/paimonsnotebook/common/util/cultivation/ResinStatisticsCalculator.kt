package com.lianyi.paimonsnotebook.common.util.cultivation

import com.lianyi.paimonsnotebook.common.web.hutao.genshin.item.Material
import kotlin.math.ceil

/*
* 培养计划树脂预估(移植胡桃工具箱树脂统计公式)
*
* 材料3:1合成折算(金=27绿/紫=9绿/蓝=3绿),高级材料可覆盖低级缺口;
* 副本次数期望取世界等级9的掉落分布(与胡桃默认一致);
* 智识之冕(不刷本获取)与元素晶石等胡桃同样排除的类型不参与统计
* */
object ResinStatisticsCalculator {

    //每天自然恢复180树脂(240s/8点)
    private const val RESIN_PER_DAY = 180.0

    private const val RESIN_PER_BLOSSOM = 20
    private const val RESIN_PER_NORMAL_BOSS = 40
    private const val RESIN_PER_WEEKLY_BOSS = 60

    //摩拉每点树脂折算
    private const val MORA_PER_RESIN = 50.0

    //世界等级9单次期望收益
    private const val WL9_BLOSSOM_OF_WEALTH = 61200.0   //60000+20*50
    private const val WL9_BLOSSOM_OF_REVELATION = 122500.0
    private const val WL9_TALENT_BOOKS = 10.12          //等效绿份数/次
    private const val WL9_WEAPON_ASCENSION = 16.708
    private const val WL9_NORMAL_BOSS = 3.0             //紫色BOSS材料个数/次
    private const val WL9_WEEKLY_BOSS = 2.4             //金色周本材料个数/次

    //经验书单本经验值
    private const val EXP_GREEN = 1000.0
    private const val EXP_BLUE = 5000.0
    private const val EXP_PURPLE = 20000.0

    data class ResinItem(
        val title: String,
        val runCount: Int,
        val totalResin: Int
    )

    data class ResinResult(
        val items: List<ResinItem>,
        val totalResin: Int
    ) {
        val days: Int get() = ceil(totalResin / RESIN_PER_DAY).toInt()
    }

    private class KindAccumulator(val title: String, val resinPerRun: Int, val expectation: Double) {
        var rawCount = 0.0

        fun toResinItem(): ResinItem {
            val runCount = ceil(rawCount / expectation).toInt()
            return ResinItem(title, runCount, runCount * resinPerRun)
        }
    }

    //缺料列表,单个材料携带其元数据与缺口数量
    fun calculate(items: List<Pair<Material, Int>>): ResinResult {
        val blossomOfWealth = KindAccumulator("摩拉", RESIN_PER_BLOSSOM, WL9_BLOSSOM_OF_WEALTH)
        val blossomOfRevelation = KindAccumulator("经验书", RESIN_PER_BLOSSOM, WL9_BLOSSOM_OF_REVELATION)
        val talentAscension = KindAccumulator("天赋本", RESIN_PER_BLOSSOM, WL9_TALENT_BOOKS)
        val weaponAscension = KindAccumulator("武器本", RESIN_PER_BLOSSOM, WL9_WEAPON_ASCENSION)
        val normalBoss = KindAccumulator("周本外BOSS", RESIN_PER_NORMAL_BOSS, WL9_NORMAL_BOSS)
        val weeklyBoss = KindAccumulator("周本", RESIN_PER_WEEKLY_BOSS, WL9_WEEKLY_BOSS)

        //天赋本/武器本按品质分桶(绿2/蓝3/紫4/金5),3:1合成折算
        val talentBuckets = doubleArrayOf(0.0, 0.0, 0.0, 0.0)
        val weaponBuckets = doubleArrayOf(0.0, 0.0, 0.0, 0.0)

        items.forEach { (material, lackCount) ->
            if (lackCount <= 0) {
                return@forEach
            }

            when {
                material.Id == 202 -> blossomOfWealth.rawCount += lackCount

                material.TypeDescription == "角色经验素材" -> {
                    val exp = when (material.RankLevel) {
                        2 -> EXP_GREEN
                        3 -> EXP_BLUE
                        else -> EXP_PURPLE
                    }

                    blossomOfRevelation.rawCount += lackCount * exp
                }

                material.TypeDescription == "角色培养素材" -> {
                    if (material.RankLevel == 4) {
                        normalBoss.rawCount += lackCount
                    } else {
                        weeklyBoss.rawCount += lackCount
                    }
                }

                //天赋本系列(排除智识之冕等非刷本材料)
                material.TypeDescription == "角色天赋素材" && material.RankLevel < 5 -> {
                    talentBuckets[material.RankLevel - 2] =
                        talentBuckets[material.RankLevel - 2] + lackCount
                }

                material.TypeDescription == "武器突破素材" -> {
                    weaponBuckets[material.RankLevel - 2] =
                        weaponBuckets[material.RankLevel - 2] + lackCount
                }
            }
        }

        talentAscension.rawCount = alchemyCraftingEquivalent(talentBuckets)
        weaponAscension.rawCount = alchemyCraftingEquivalent(weaponBuckets)

        val items2 = listOf(
            blossomOfWealth, blossomOfRevelation, talentAscension,
            weaponAscension, normalBoss, weeklyBoss
        ).filter { it.rawCount > 0 }.map { it.toResinItem() }

        return ResinResult(
            items = items2,
            totalResin = items2.sumOf { it.totalResin }
        )
    }

    /*
    * 胡桃AlchemyCrafting折算:金/紫/蓝按3:1逐级折算成等效绿色品质份数
    * */
    private fun alchemyCraftingEquivalent(buckets: DoubleArray): Double =
        buckets[3] * 27 + buckets[2] * 9 + buckets[1] * 3 + buckets[0]
}
