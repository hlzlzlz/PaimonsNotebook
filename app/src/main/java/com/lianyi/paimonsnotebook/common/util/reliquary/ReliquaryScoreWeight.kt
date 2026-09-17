package com.lianyi.paimonsnotebook.common.util.reliquary

import com.google.gson.JsonObject
import com.google.gson.JsonParser
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

        //字段声明顺序,也是Gson的序列化顺序
        private val FIELD_NAMES = listOf(
            "critRate",
            "critDmg",
            "atkPercent",
            "hpPercent",
            "defPercent",
            "chargeEfficiency",
            "elementMastery"
        )

        fun fromJson(json: String): ReliquaryScoreWeight? = runCatching {
            if (isLegacyFormat(json)) {
                fromLegacyJson(json)
            } else {
                JSON.parse<ReliquaryScoreWeight>(json)
            }
        }.getOrNull()

        /*
        * 判断是否为加@SerializedName之前写入的旧格式。
        *
        * 1.8.7及更早的版本中,本类字段被R8混淆成单字母(如a~g),Gson按字段名序列化,
        * 因此DataStore里存的是 {"a":1.5,"b":1.0,...}。仅靠@SerializedName无法读回这些数据
        * (键名对不上会静默套用默认值1.0),必须在读取时做一次迁移。
        * */
        fun isLegacyFormat(json: String): Boolean = runCatching {
            val obj = JsonParser.parseString(json).asJsonObject

            obj.size() > 0 && FIELD_NAMES.none { obj.has(it) }
        }.getOrDefault(false)

        /*
        * 解析旧格式:键名已不可知(R8每次构建分配的字母可能不同),
        * 但JsonObject保持插入顺序,与写入时的字段声明顺序一致,
        * 故按位置取值。缺失的位置退回默认值1.0。
        * */
        private fun fromLegacyJson(json: String): ReliquaryScoreWeight {
            val obj: JsonObject = JsonParser.parseString(json).asJsonObject

            val values = obj.entrySet().map { entry ->
                runCatching { entry.value.asDouble }.getOrNull()
            }

            fun at(index: Int) = values.getOrNull(index) ?: 1.0

            return ReliquaryScoreWeight(
                critRate = at(0),
                critDmg = at(1),
                atkPercent = at(2),
                hpPercent = at(3),
                defPercent = at(4),
                chargeEfficiency = at(5),
                elementMastery = at(6)
            )
        }
    }
}
