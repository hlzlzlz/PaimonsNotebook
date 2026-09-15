package com.lianyi.paimonsnotebook.common.util.reliquary

import com.lianyi.paimonsnotebook.common.web.hoyolab.takumi.game_record.character.CharacterDetailData

/*
* 圣遗物评分计算器(基于胡桃工具箱 ReliquaryScoreCalculator 自动模式公式调整)
*
* 只对副词条计分,主词条不参与;
* 权重:命中米游社推荐副词条记1.0,未命中记0.5(胡桃原版为清零,
* 会导致低练度角色圣遗物普遍0分,观感如同功能损坏);
* 固定值类(生命/攻击/防御小字)再打5折;双爆对非心海角色强制有效;
* 未推荐充能:普通角色0.2,特殊能量角色(玛薇卡/丝柯克)0.5;
* 得分 = Σ 数值 × 属性系数 × 权重
* */
object ReliquaryScoreCalculator {

    //属性Id,与米游社game_record及GIMetadata的FightProperty枚举一致
    private const val FIGHT_PROP_HP = 2
    private const val FIGHT_PROP_HP_PERCENT = 3
    private const val FIGHT_PROP_ATTACK = 5
    private const val FIGHT_PROP_ATTACK_PERCENT = 6
    private const val FIGHT_PROP_DEFENSE = 8
    private const val FIGHT_PROP_DEFENSE_PERCENT = 9
    private const val FIGHT_PROP_CRITICAL = 20
    private const val FIGHT_PROP_CRITICAL_HURT = 22
    private const val FIGHT_PROP_CHARGE_EFFICIENCY = 23
    private const val FIGHT_PROP_ELEMENT_MASTERY = 28

    //心海不吃双爆收益(对应胡桃 AvatarIds.IsCritEffective)
    private const val AVATAR_ID_KOKOMI = 10000054

    fun isCritEffective(avatarId: Int) = avatarId != AVATAR_ID_KOKOMI

    /**
     * isSpecialEnergy = 角色拥有特殊能量机制
     * (SkillDepot.EnergySkill.SpecialEnergyType非NONE,目前仅玛薇卡/丝柯克)
     *
     * customWeights非空时走手动权重模式(胡桃手动模式):权重直接查表,
     * 忽略推荐词条命中/双爆强制/充能特判;小字固定值等未列出的属性权重为0
     */
    fun calculate(
        recommendedSubProperties: List<Int>,
        subProperties: List<CharacterDetailData.SubProperty>,
        isSpecialEnergy: Boolean,
        isCritEffective: Boolean,
        customWeights: Map<Int, Double>? = null
    ): Double {
        val hasCritHurt =
            isCritEffective || recommendedSubProperties.contains(FIGHT_PROP_CRITICAL_HURT)

        var totalScore = 0.0

        subProperties.forEach { subProperty ->
            val weight = customWeights?.get(subProperty.property_type) ?: getWeight(
                subProperty.property_type,
                recommendedSubProperties,
                hasCritHurt,
                isSpecialEnergy,
                isCritEffective
            )

            if (weight <= 0) {
                return@forEach
            }

            val value = parseValue(subProperty.value) ?: return@forEach

            totalScore += scoreStat(subProperty.property_type, value, weight)
        }

        return totalScore
    }

    //一次角色详情的评分上下文
    data class Context(
        val recommendedSubProperties: List<Int>,
        val isSpecialEnergy: Boolean,
        val isCritEffective: Boolean
    )

    fun calculate(
        relic: CharacterDetailData.Relic,
        context: Context,
        customWeights: Map<Int, Double>? = null
    ): Double =
        calculate(
            recommendedSubProperties = context.recommendedSubProperties,
            subProperties = relic.sub_property_list,
            isSpecialEnergy = context.isSpecialEnergy,
            isCritEffective = context.isCritEffective,
            customWeights = customWeights
        )

    //手动权重模式:ReliquaryScoreWeight各字段映射到FightProperty
    fun customWeightMap(weight: ReliquaryScoreWeight): Map<Int, Double> = mapOf(
        FIGHT_PROP_CRITICAL to weight.critRate,
        FIGHT_PROP_CRITICAL_HURT to weight.critDmg,
        FIGHT_PROP_ATTACK_PERCENT to weight.atkPercent,
        FIGHT_PROP_HP_PERCENT to weight.hpPercent,
        FIGHT_PROP_DEFENSE_PERCENT to weight.defPercent,
        FIGHT_PROP_CHARGE_EFFICIENCY to weight.chargeEfficiency,
        FIGHT_PROP_ELEMENT_MASTERY to weight.elementMastery
    )

    private fun getWeight(
        propertyType: Int,
        recommendedSubProperties: List<Int>,
        hasCritHurt: Boolean,
        isSpecialEnergy: Boolean,
        isCritEffective: Boolean
    ): Double {
        //非心海角色双爆强制有效,避免米游社推荐副属性缺失双爆时评分失真
        if (isCritEffective &&
            (propertyType == FIGHT_PROP_CRITICAL || propertyType == FIGHT_PROP_CRITICAL_HURT)
        ) {
            return 1.0
        }

        val isRecommended = recommendedSubProperties.contains(propertyType)

        //未被推荐的充能按能量机制特判
        if (propertyType == FIGHT_PROP_CHARGE_EFFICIENCY && !isRecommended) {
            return getChargeEfficiencyWeight(isSpecialEnergy)
        }

        //胡桃原版未推荐清零,这里改为半权,避免未命中推荐的圣遗物普遍0分
        if (!isRecommended) {
            return 0.5
        }

        var weight = 1.0

        if (propertyType == FIGHT_PROP_HP ||
            propertyType == FIGHT_PROP_ATTACK ||
            propertyType == FIGHT_PROP_DEFENSE
        ) {
            weight *= 0.5
        }

        return weight
    }

    //特殊能量角色(玛薇卡/丝柯克)未推荐充能:0.5;普通角色未推荐充能:0.2
    private fun getChargeEfficiencyWeight(isSpecialEnergy: Boolean): Double =
        if (isSpecialEnergy) 0.5 else 0.2

    //各属性数值系数,固定值类再打折
    private fun scoreStat(propertyType: Int, value: Double, weight: Double): Double =
        when (propertyType) {
            FIGHT_PROP_CRITICAL -> value * 2.0 * weight
            FIGHT_PROP_CRITICAL_HURT -> value * 1.0 * weight
            FIGHT_PROP_ELEMENT_MASTERY -> value * 0.33 * weight
            FIGHT_PROP_CHARGE_EFFICIENCY -> value * 1.1979 * weight
            FIGHT_PROP_HP_PERCENT -> value * 1.33 * weight
            FIGHT_PROP_ATTACK_PERCENT -> value * 1.33 * weight
            FIGHT_PROP_DEFENSE_PERCENT -> value * 1.06 * weight
            FIGHT_PROP_ATTACK -> value * 0.398 * 0.5 * weight
            FIGHT_PROP_HP -> value * 0.026 * 0.66 * weight
            FIGHT_PROP_DEFENSE -> value * 0.335 * 0.66 * weight
            else -> 0.0
        }

    //米游社下发的value为已格式化字符串("3.5%"或"14"),统一去掉百分号后解析
    private fun parseValue(value: String): Double? =
        value.trimEnd('%').toDoubleOrNull()
}
