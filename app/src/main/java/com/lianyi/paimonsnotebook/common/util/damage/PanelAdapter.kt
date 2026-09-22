package com.lianyi.paimonsnotebook.common.util.damage

import com.lianyi.paimonsnotebook.common.web.hoyolab.takumi.game_record.character.CharacterDetailData
import com.lianyi.paimonsnotebook.common.web.hutao.genshin.intrinsic.FightProperty

/*
* 角色面板 → 伤害公式输入 的适配层(纯逻辑层)
*
* ⚠️ 为什么需要这一层(实测得到的约束,务必先读):
*
* `character/detail` 接口返回的 `CharacterDetailData.Property` 值是**字符串**:
*
*   data class Property(val add: String, val base: String, val `final`: String, val property_type: Int)
*
* 且服务端**已经把它格式化好了** —— 现有 UI(`PlayerCharacterPropertyItem.kt:63`)直接
* `Text(text = data.final)` 显示,即字符串里**自带百分号**(如 "61.6%")。
* 详见 `FormatMethod.kt`:暴击/爆伤/各类增伤是 Percent,攻击力/精通是 Integer。
*
* 因此本层的职责是:
*   1. 把 "61.6%" / "1234" 这类字符串解析成可计算的 Double
*   2. 按百分比语义换算(Percent 类要 /100,因为公式里用的是小数)
*   3. 把 `List<Property>` 按 property_type 索引成 Map,供公式取值
*
* ⚠️ 解析必须**宽松**:服务端格式可能随版本变化(带不带 %,带不带千分位,
 *   小数位数),任何一个属性解析失败都不应让整个计算崩掉 —— 取不到就返回 null,
 *   由上层决定"该属性缺失"如何处理(通常是按 0 计并在 UI 标注)。
* 这与本项目「无法判定就用 null,不要谎报」的纪律一致。
* */

/**
 * 一次伤害计算所需的**角色面板快照**。
 *
 * ⚠️ 所有数值都是"公式可用"的形式:
 *   - [critRate] / [critDamage] / [bonusXxx] 是**小数**(0.616 = 61.6%)
 *   - [attack] 是**绝对值**(如 1234.0)
 *
 * @param attack      攻击力(已含武器/圣遗物加成,取接口的 final)
 * @param critRate    暴击率(小数)
 * @param critDamage  暴击伤害(小数,**不含**基础 50%)
 * @param elementMastery 元素精通(整数)
 * @param bonusByPropertyType 各增伤类型 → 小数,键用 `FIGHT_PROP_*_ADD_HURT`
 * @param missingProperties 解析失败的属性类型列表(**用于 UI 诚实标注**)
 */
data class AvatarPanel(
    val attack: Double?,
    val critRate: Double?,
    val critDamage: Double?,
    val elementMastery: Double?,
    val bonusByPropertyType: Map<Int, Double>,
    val missingProperties: Set<Int> = emptySet()
) {
    /** 某个增伤类型是否可用(UI 用于判断能否算该元素伤害) */
    fun bonusFor(propertyType: Int): Double? = bonusByPropertyType[propertyType]

    /**
     * 该面板是否足以参与计算。
     *
     * ⚠️ 攻击力与暴击率是**必需**的:缺攻击力算出来必然是 0,
     * 缺暴击率则无法给期望值。爆伤缺失可以按 0 处理(不暴击口径仍正确),
     * 故**不**列入必需项 —— 这是刻意的宽容,避免因一个次要属性缺失就整个不可用。
     */
    val isUsable: Boolean get() = attack != null && critRate != null
}

object PanelAdapter {

    /**
     * 把接口的 Property 列表适配成 [AvatarPanel]。
     *
     * @param properties `selected_properties`(或 `base_properties`,取含最终值的那份)
     * @param bonusPropertyTypes 需要收集的增伤类型(如该角色的元素增伤 + 物理增伤)
     */
    fun adapt(
        properties: List<CharacterDetailData.Property>,
        bonusPropertyTypes: Set<Int> = emptySet()
    ): AvatarPanel {
        val byType = properties.associateBy { it.property_type }

        val attackValue = byType[FightProperty.FIGHT_PROP_ATTACK]?.let { parseProperty(it) }
        val critRateValue = byType[FightProperty.FIGHT_PROP_CRITICAL]?.let { parseProperty(it) }
        val critDamageValue = byType[FightProperty.FIGHT_PROP_CRITICAL_HURT]?.let { parseProperty(it) }
        val masteryValue = byType[FightProperty.FIGHT_PROP_ELEMENT_MASTERY]?.let { parseProperty(it) }

        val bonuses = mutableMapOf<Int, Double>()
        bonusPropertyTypes.forEach { type ->
            byType[type]?.let { parseProperty(it) }?.let { bonuses[type] = it }
        }

        // 记录"出现在 bonusPropertyTypes 里但没解析出来"的类型,供 UI 标注
        val missing = bonusPropertyTypes.filter { it !in bonuses }.toSet()

        return AvatarPanel(
            attack = attackValue,
            critRate = critRateValue,
            critDamage = critDamageValue,
            elementMastery = masteryValue,
            bonusByPropertyType = bonuses,
            missingProperties = missing
        )
    }

    /**
     * 解析单条属性。
     *
     * 取值优先级:`final` > `base`。
     * ⚠️ 只取 `final` 而**不叠加** `base`/`add` —— 因为 `final` 按接口语义
     * 已是"最终值"(通常等于 base + add)。若三者相加会**重复计算**。
     * 这一点无法从本机元数据实测确认(接口需真实凭证),故采用最保守的
     * "只信 final"策略,并在注释中标明未验证。
     *
     * @return 公式可用的小数/绝对值;解析失败返回 null
     */
    fun parseProperty(property: CharacterDetailData.Property): Double? {
        val raw = property.`final`.takeIf { it.isNotBlank() }
            ?: property.base.takeIf { it.isNotBlank() }
            ?: return null

        val hasPercent = raw.contains('%')
        val number = raw.replace("%", "").replace(",", "").trim().toDoubleOrNull()
            ?: return null

        // Percent 类属性要换算成小数(公式里用的是 0.616 而非 61.6)
        return if (hasPercent) number / 100.0 else number
    }

    /**
     * 按 [FormatMethod] 的语义判断某属性是否应为百分比。
     *
     * ⚠️ 这是**辅助**判断,**不替代**对字符串里 `%` 的检测:
     * 真源是服务端返回的字符串本身。本函数只用于:
     *   ① 单测里断言"该是百分比的属性确实带了 %"
     *   ② UI 在解析失败时决定用百分号还是整数格式提示
     * 之所以复用 FormatMethod 而不是自己再写一份表,是为了**避免两处真相**。
     */
    fun isPercentProperty(propertyType: Int): Boolean =
        com.lianyi.paimonsnotebook.common.web.hutao.genshin.intrinsic.format.FormatMethod
            .getFormatMethod(propertyType) ==
                com.lianyi.paimonsnotebook.common.web.hutao.genshin.intrinsic.format.FormatMethod.Method.Percent
}
