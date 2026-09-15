package com.lianyi.paimonsnotebook.common.util.reliquary

import com.lianyi.paimonsnotebook.common.util.json.JSON

/*
* 圣遗物评分手动权重(对应胡桃工具箱的手动权重模式)
* 每项0~N的乘数,作用于"数值×属性系数";未列出的属性(小字固定值等)权重为0不参与计分
* */
data class ReliquaryScoreWeight(
    val critRate: Double = 1.0,
    val critDmg: Double = 1.0,
    val atkPercent: Double = 1.0,
    val hpPercent: Double = 1.0,
    val defPercent: Double = 1.0,
    val chargeEfficiency: Double = 1.0,
    val elementMastery: Double = 1.0
) {

    fun stringify() = JSON.stringify(this)

    companion object {
        fun fromJson(json: String): ReliquaryScoreWeight? =
            runCatching { JSON.parse<ReliquaryScoreWeight>(json) }.getOrNull()
    }
}
