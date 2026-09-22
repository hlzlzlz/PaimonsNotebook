package com.lianyi.paimonsnotebook.common.util.damage

/*
* 队伍 DPS 计算 —— 队伍模型与编排(纯逻辑层,无 Android 依赖)
*
* ⚠️ 本功能的形态约束(实测得出,勿擅自"增强"):
*
* 1. **队伍由用户手动拼**,不是读游戏里的队伍编成。
*    实测:米游社接口**不返回玩家的队伍编成**;项目全树也无 team/party 概念
*    (47 处命中全是"全服配队统计"与"战斗记录只读展示")。故"选 4 人"是唯一可行形态。
*
* 2. **不算时序,只算"一轮循环的伤害总量"**。
*    实测:本地元数据**没有攻击速度、动画帧、前后摇**,普攻只有倍率与 `*3` 段数标记。
*    缺"一次普攻占几秒"这一必需输入 ⇒ 无法算真正的"每秒"伤害。
*    故本模块输出的是**每次循环的伤害**(以及用户可选地除以自己填的循环耗时)。
*    ⚠️ 若 UI 要显示"DPS",必须让用户提供循环耗时,并明示"耗时由用户提供,非游戏真实值"。
*    绝不可内置一个编造的攻速常数 —— 那是伪造数据。
*
* 3. 只做直伤 + 增幅反应(蒸发/融化)。剧变反应见 DamageFormula.UNSUPPORTED_REACTIONS。
* */

/**
 * 队伍中的一个成员及其"这一轮要打什么"。
 *
 * @param avatarId    角色 id(用于查元数据取名字/图标)
 * @param name        角色名(便于 UI 与调试;计算本身不需要)
 * @param element     角色元素(用 ElementType 常量;用于推默认增幅反应)
 * @param panel       面板快照(来自 [PanelAdapter])
 * @param actions     该成员这一轮的所有出伤动作
 */
data class TeamMember(
    val avatarId: Int,
    val name: String,
    val element: Int,
    val panel: AvatarPanel,
    val actions: List<MemberAction>
)

/**
 * 一次"出伤动作" —— 例如"绫华大招第 1 段(切割)"打 1 次。
 *
 * @param label         动作标签(如 "神里流·霜灭 · 切割伤害"),用于 UI 明细
 * @param multiplier    技能倍率(小数,如 1.123)
 * @param count         该动作在本轮内的次数(如绫华大招的切割实际会命中多次)
 * @param damageBonus   本条动作的增伤(小数)。默认由调用方从 panel 取对应元素增伤;
 *                      单独放在动作上是为了支持"不同动作吃不同增伤"的情况
 * @param reaction      增幅反应倍率。默认取 [ReactionType.NONE](1.0),
 *                      **不自动推断** —— 见下方 [TeamDamageCalculator] 的说明
 * @param unsupportedReaction 若该动作涉及剧变反应,填其名称;
 *                      计算时会被**跳过**并记入 [TeamDamageResult.skippedReactions],
 *                      **而不是**悄悄按 1.0 算出一个错的数
 */
data class MemberAction(
    val label: String,
    val multiplier: Double,
    val count: Int = 1,
    val damageBonus: Double = 0.0,
    val reaction: ReactionType = ReactionType.NONE,
    val unsupportedReaction: String? = null
)

/**
 * 单个成员的计算结果。
 *
 * ⚠️ [expectedTotal] 与 [nonCritTotal] / [critTotal] 必须满足:
 *   expectedTotal = nonCritTotal × (1 - 暴击率) + critTotal × 暴击率
 * 三者由同一份逐动作明细汇总而来,**不可各自独立算**(否则自相矛盾,已钉住)。
 */
data class MemberDamageResult(
    val avatarId: Int,
    val name: String,
    val nonCritTotal: Double,
    val critTotal: Double,
    val expectedTotal: Double,
    val perAction: List<ActionDamage>
)

/** 单条动作的伤害明细(供 UI 展开查看"这个数是怎么来的") */
data class ActionDamage(
    val label: String,
    val multiplier: Double,
    val count: Int,
    val nonCrit: Double,
    val crit: Double,
    val expected: Double
)

/**
 * 整个队伍的结果。
 *
 * @param members            每个成员的结果(顺序与输入一致)
 * @param skippedReactions   被跳过的剧变反应(名称 + 所属成员),
 *                           **必须展示给用户**,否则用户会以为这些伤害被算进去了
 * @param skippedMembers     因面板不可用([AvatarPanel.isUsable] 为 false)而无参与计算的成员名
 */
data class TeamDamageResult(
    val members: List<MemberDamageResult>,
    val skippedReactions: List<SkippedReaction>,
    val skippedMembers: List<String>
) {
    /** 队伍期望伤害合计 */
    val teamExpected: Double get() = members.sumOf { it.expectedTotal }

    /** 队伍非暴击伤害合计 */
    val teamNonCrit: Double get() = members.sumOf { it.nonCritTotal }

    /** 队伍暴击伤害合计 */
    val teamCrit: Double get() = members.sumOf { it.critTotal }
}

/** 被跳过(未计算)的剧变反应记录 */
data class SkippedReaction(val memberName: String, val reactionName: String, val actionLabel: String)

object TeamDamageCalculator {

    /**
     * 计算一个队伍在一个循环内的伤害。
     *
     * @param members       队伍成员(1~4 人;不强制 4 人 —— 单人也要能算)
     * @param attackerLevel 攻击者等级(用于防御区)
     * @param defenderLevel 目标等级
     * @param resistance    目标抗性(小数)
     * @param defenseReduction 减防(小数,默认 0)
     */
    fun calculate(
        members: List<TeamMember>,
        attackerLevel: Int = 90,
        defenderLevel: Int = 90,
        resistance: Double = 0.1,
        defenseReduction: Double = 0.0
    ): TeamDamageResult {
        val defenseFactor = DamageFormula.defenseFactor(attackerLevel, defenderLevel, defenseReduction)

        val skippedMembers = mutableListOf<String>()
        val skippedReactions = mutableListOf<SkippedReaction>()
        val memberResults = mutableListOf<MemberDamageResult>()

        members.forEach { member ->
            // 面板不可用的成员:跳过并**记名**。不静默丢弃 —— 否则用户看到
            // "算了 3 个人"却以为是 4 个,数字会莫名其妙偏低。
            if (!member.panel.isUsable) {
                skippedMembers += member.name
                return@forEach
            }

            val attack = member.panel.attack!!
            val critRate = member.panel.critRate!!
            val critDamage = member.panel.critDamage ?: 0.0

            val perAction = mutableListOf<ActionDamage>()

            member.actions.forEach { action ->
                // 剧变反应:跳过并记录,不给错数
                if (action.unsupportedReaction != null) {
                    skippedReactions += SkippedReaction(
                        memberName = member.name,
                        reactionName = action.unsupportedReaction!!,
                        actionLabel = action.label
                    )
                    return@forEach
                }

                val input = DamageInput(
                    multiplier = action.multiplier,
                    attack = attack,
                    critRate = critRate,
                    critDamage = critDamage,
                    bonus = action.damageBonus,
                    resistance = resistance,
                    levelDiffDefense = defenseFactor,
                    reaction = action.reaction.factor
                )
                val r = DamageFormula.calculate(input)
                val n = action.count.toDouble()

                perAction += ActionDamage(
                    label = action.label,
                    multiplier = action.multiplier,
                    count = action.count,
                    nonCrit = r.nonCrit * n,
                    crit = r.crit * n,
                    expected = r.expected * n
                )
            }

            // ⚠️ 三人合计必须由**逐动作明细汇总**得出,不能另起一套算式,
            //    否则会出现"合计 ≠ 明细之和"的矛盾(已钉住)
            memberResults += MemberDamageResult(
                avatarId = member.avatarId,
                name = member.name,
                nonCritTotal = perAction.sumOf { it.nonCrit },
                critTotal = perAction.sumOf { it.crit },
                expectedTotal = perAction.sumOf { it.expected },
                perAction = perAction
            )
        }

        return TeamDamageResult(
            members = memberResults,
            skippedReactions = skippedReactions,
            skippedMembers = skippedMembers
        )
    }

    /**
     * 把"每循环伤害"换算成 DPS。
     *
     * ⚠️ **必须由调用方提供循环耗时**,本函数**不提供默认值** ——
     * 因为本机元数据里没有攻速/帧数数据,任何默认值都等于**编造**。
     * 若调用方拿不到可信耗时,应展示"每循环伤害"而不是 DPS。
     *
     * @param damagePerRotation 一轮循环的伤害
     * @param rotationSeconds   一轮循环的耗时(秒),必须 > 0
     * @return DPS;耗时非法时返回 null(而非 0,避免"0 DPS"被当成真实结果)
     */
    fun toDps(damagePerRotation: Double, rotationSeconds: Double): Double? {
        if (rotationSeconds <= 0.0 || rotationSeconds.isNaN() || rotationSeconds.isInfinite()) return null
        return damagePerRotation / rotationSeconds
    }
}
