package com.lianyi.paimonsnotebook.common.util.reliquary

import com.google.gson.annotations.SerializedName
import com.lianyi.paimonsnotebook.common.util.json.JSON

/*
* 圣遗物评分手动权重(对应胡桃工具箱的手动权重模式)
* 每项0~N的乘数,作用于"数值×属性系数";未列出的属性(小字固定值等)权重为0不参与计分
*
* 注意:本类通过Gson序列化后存入DataStore,必须为每个字段声明@SerializedName。
* 本包(common.util.reliquary)不在proguard的 -keep class com.lianyi.**.data.** 覆盖范围内,
* 若依赖字段名,R8每次构建会重新分配混淆名(如a~g),导致升级后旧权重数据读不回来,
* 且fromJson用runCatching静默吞掉异常,表现为"用户设置的权重升级后自己还原成默认值"
* */
data class ReliquaryScoreWeight(
    @SerializedName("critRate")
    val critRate: Double = 1.0,
    @SerializedName("critDmg")
    val critDmg: Double = 1.0,
    @SerializedName("atkPercent")
    val atkPercent: Double = 1.0,
    @SerializedName("hpPercent")
    val hpPercent: Double = 1.0,
    @SerializedName("defPercent")
    val defPercent: Double = 1.0,
    @SerializedName("chargeEfficiency")
    val chargeEfficiency: Double = 1.0,
    @SerializedName("elementMastery")
    val elementMastery: Double = 1.0
) {

    fun stringify() = JSON.stringify(this)

    companion object {
        fun fromJson(json: String): ReliquaryScoreWeight? =
            runCatching { JSON.parse<ReliquaryScoreWeight>(json) }.getOrNull()
    }
}
