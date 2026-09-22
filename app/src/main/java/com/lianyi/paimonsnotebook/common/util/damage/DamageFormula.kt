package com.lianyi.paimonsnotebook.common.util.damage

/*
* 伤害公式(纯逻辑层,无 Android 依赖)
*
* ⚠️ 设计约束(务必遵守,原因见 AGENTS.md「真机分支 vs 单测分支」约束):
*   本文件**不得引用任何 Android 平台 API**(android.* 一律不许出现)。
*   1.8.13 的 <t> 标签修复就是因为写在"只有真机才走的分支"里,
*   导致**单测全绿而真机依然坏**。本层据此设计成纯函数,
*   使其在 JVM 单测与真机上走**完全相同的代码路径**。
*
* 公式口径(第一版):
*   单次伤害 = 倍率 × 攻击力 × (1 + 爆伤 × 暴击率) × 增伤区 × 抗性区 × 防御区 × 反应区
*   其中 (1 + 爆伤 × 暴击率) 是**暴击期望**系数,
*   故本计算器输出的是"期望伤害",不是"暴击时的伤害"。
*
* ⚠️ 明确不支持(第一版,不给错数):
*   - 剧变反应(超载/感电/冻结/碎冰/扩散/结晶/绽放/燃烧/激化/超激化/蔓激化):
*     需要"元素精通 → 反应加成"的等级系数曲线,而本项目本地元数据
*     (Snap.Metadata)里**没有这张表**。硬做等于**编造数据**,违背本项目纪律。
*   - 元素附着与 ICD(内部冷却)、附着量、多段挂载时序。
*   - 抗性降低(减抗 debuff)、无视防御、易伤(部分已通过"增伤区"近似)。
* */

/**
 * 一次伤害计算的输入。
 *
 * @param multiplier   技能倍率(小数,如 1.123 表示 112.3%)
 * @param attack       攻击力面板(已含武器与圣遗物加成后的最终值)
 * @param critRate     暴击率(小数,0.5 = 50%)
 * @param critDamage   暴击伤害(小数,1.0 = 100% 爆伤,**不含**基础 50%)
 * @param bonus        增伤区(小数,元素伤害加成/物理伤害加成之和)
 * @param resistance   目标抗性(小数,0.1 = 10% 抗性)
 * @param levelDiffDefense 防御区系数(由 [DamageFormula.defenseFactor] 算好传入)
 * @param reaction     反应区倍率(见 [ReactionType],无反应传 1.0)
 */
data class DamageInput(
    val multiplier: Double,
    val attack: Double,
    val critRate: Double,
    val critDamage: Double,
    val bonus: Double = 0.0,
    val resistance: Double = 0.1,
    val levelDiffDefense: Double = 1.0,
    val reaction: Double = 1.0
)

/**
 * 一次伤害计算的结果。
 *
 * ⚠️ [expected] 与 [nonCrit] / [crit] 的关系必须保持:
 *   expected = nonCrit × (1 - critRate) + crit × critRate
 *   三者是**同一公式的三种口径**,不可各自独立计算,否则会出现
 *   "期望值不在暴击/非暴击之间"这种自相矛盾的结果(已有用例钉住)。
 */
data class DamageResult(
    /** 不暴击时的伤害 */
    val nonCrit: Double,
    /** 暴击时的伤害 */
    val crit: Double,
    /** 期望伤害(含暴击期望),等于 nonCrit × (1-critRate) + crit × critRate */
    val expected: Double
)

/**
 * 增幅反应类型。
 *
 * ⚠️ 第一版只支持这两种。剧变反应(超载/感电/绽放/激化等)**明确不支持**,
 * 因为需要本地元数据里不存在的精通系数表。参见 [DamageFormula.isUnsupportedReaction]。
 *
 * 倍率取值(社区共识,官方未公开完整公式):
 *   - 蒸发(水打火 / 火打水): 2.0 / 1.5
 *   - 融化(火打冰 / 冰打火): 2.0 / 1.5
 * 即"克制方向"2.0,"被克制方向"1.5。
 */
enum class ReactionType(val factor: Double) {
    NONE(1.0),

    /** 水元素攻击火附着目标:2.0 */
    VAPORIZE_WATER_ON_FIRE(2.0),

    /** 火元素攻击水附着目标:1.5 */
    VAPORIZE_FIRE_ON_WATER(1.5),

    /** 火元素攻击冰附着目标:2.0 */
    MELT_FIRE_ON_ICE(2.0),

    /** 冰元素攻击火附着目标:1.5 */
    MELT_ICE_ON_FIRE(1.5)
}

object DamageFormula {

    /**
     * 抗性区系数。
     *
     * 分段规则(社区共识):
     *   - 抗性 < 0      : 1 - 抗性 / 2      (负抗性收益减半)
     *   - 0 ≤ 抗性 < 0.75: 1 - 抗性
     *   - 抗性 ≥ 0.75   : 1 / (4 × 抗性 + 1) (高抗性区急剧衰减)
     *
     * ⚠️ 三段在**分段点上连续**:
     *   抗性 = 0    ⇒ 第1式=1.0,第2式=1.0
     *   抗性 = 0.75 ⇒ 第2式=0.25,第3式=1/(4×0.75+1)=0.25
     *   已有用例钉住这两个分界点(边界错误是最容易漏的 bug)。
     */
    fun resistanceFactor(resistance: Double): Double = when {
        resistance < 0.0 -> 1.0 - resistance / 2.0
        resistance < 0.75 -> 1.0 - resistance
        else -> 1.0 / (4.0 * resistance + 1.0)
    }

    /**
     * 防御区系数。
     *
     * 规则: (角色等级 + 100) / ((角色等级 + 100) + (怪物等级 + 100) × (1 - 减防))
     *
     * ⚠️ 同等等级时该系数 = 0.5(而非 1.0)—— 这是常见误解,
     * 已有用例钉住"同等级 = 0.5"。
     *
     * @param attackerLevel 攻击者等级
     * @param defenderLevel 目标等级
     * @param defenseReduction 减防比例(小数,0.3 = 减防 30%),第一版默认 0
     */
    fun defenseFactor(
        attackerLevel: Int,
        defenderLevel: Int,
        defenseReduction: Double = 0.0
    ): Double {
        val attacker = attackerLevel + 100.0
        val defender = (defenderLevel + 100.0) * (1.0 - defenseReduction)
        return attacker / (attacker + defender)
    }

    /**
     * 计算一次伤害。
     *
     * ⚠️ [DamageInput.reaction] 若传入剧变反应的倍率,本函数**无法识别**,
     * 会按普通乘数处理 —— 故调用方必须先用 [isUnsupportedReaction] 拦截。
     */
    fun calculate(input: DamageInput): DamageResult {
        // 基础伤害:倍率 × 攻击力 × 增伤区 × 抗性区 × 防御区 × 反应区
        val base = input.multiplier *
                input.attack *
                (1.0 + input.bonus) *
                resistanceFactor(input.resistance) *
                input.levelDiffDefense *
                input.reaction

        val nonCrit = base
        val crit = base * (1.0 + input.critDamage)
        val expected = nonCrit * (1.0 - input.critRate) + crit * input.critRate

        return DamageResult(nonCrit = nonCrit, crit = crit, expected = expected)
    }

    /**
     * 暴击期望系数:(1 + 爆伤 × 暴击率)。
     *
     * 单独暴露以便 UI 展示"期望系数",也便于单测直接钉住。
     * ⚠️ 暴击率与爆伤都会被**钳制**:暴击率上限 1.0(超出无收益),
     * 爆伤无上限但负值视为 0(面板不应为负,防御性处理)。
     */
    fun critExpectationFactor(critRate: Double, critDamage: Double): Double {
        val rate = critRate.coerceIn(0.0, 1.0)
        val dmg = critDamage.coerceAtLeast(0.0)
        return 1.0 + dmg * rate
    }

    /**
     * 剧变反应判定 —— 用于**拦截**而不是计算。
     *
     * 第一版对剧变反应**不提供数值**,只提供"是否属于不支持范围"的判断,
     * 让 UI 能明确提示用户"该反应本次不参与计算",
     * 而不是悄悄算出一个错的数。
     *
     * @param reactionName 反应中文名(如 "超载"、"感电"、"绽放")
     */
    fun isUnsupportedReaction(reactionName: String): Boolean =
        reactionName in UNSUPPORTED_REACTIONS

    /** 第一版明确不支持的剧变反应名称集合(含激化类) */
    val UNSUPPORTED_REACTIONS: Set<String> = setOf(
        "超载", "感电", "冻结", "碎冰", "扩散",
        "结晶", "绽放", "超绽放", "烈绽放", "燃烧",
        "原激化", "超激化", "蔓激化"
    )

    /**
     * 增幅反应倍率查询。
     *
     * @param attackerElement 攻击元素(用 ElementType 常量)
     * @param auraElement     目标附着元素(用 ElementType 常量)
     * @return 对应反应;不属于增幅反应时返回 [ReactionType.NONE](倍率 1.0)
     */
    fun amplifyReaction(attackerElement: Int, auraElement: Int): ReactionType = when {
        // 蒸发:水打火 2.0 / 火打水 1.5
        attackerElement == ElementType.Water && auraElement == ElementType.Fire ->
            ReactionType.VAPORIZE_WATER_ON_FIRE

        attackerElement == ElementType.Fire && auraElement == ElementType.Water ->
            ReactionType.VAPORIZE_FIRE_ON_WATER

        // 融化:火打冰 2.0 / 冰打火 1.5
        attackerElement == ElementType.Fire && auraElement == ElementType.Ice ->
            ReactionType.MELT_FIRE_ON_ICE

        attackerElement == ElementType.Ice && auraElement == ElementType.Fire ->
            ReactionType.MELT_ICE_ON_FIRE

        else -> ReactionType.NONE
    }
}

/*
* 元素常量在本层内部复刻一份,而不是 import ElementType。
*
* 理由:ElementType 位于 common/web/hutao/genshin/intrinsic,虽然它本身
* 也是纯常量类,但把"伤害公式"与"网络层包"解耦能避免以后有人在
* ElementType 里加 Android 依赖(如 drawable 资源 id)时,
* 把 Android 依赖**传染**到本纯逻辑层 —— 那会直接踩中
* 「真机分支 vs 单测分支」的坑。
* 若两侧数值不一致,已有用例钉住(见 DamageFormulaTest 的元素一致性用例)。
* */
private object ElementType {
    const val Fire = 1
    const val Water = 2
    const val Ice = 5
}
