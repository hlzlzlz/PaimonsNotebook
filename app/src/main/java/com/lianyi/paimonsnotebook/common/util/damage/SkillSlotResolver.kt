package com.lianyi.paimonsnotebook.common.util.damage

import com.lianyi.paimonsnotebook.common.web.hutao.genshin.avatar.AvatarData

/*
* 技能槽位判别 + 出伤倍率项选择(纯逻辑层,无 Android 依赖)
*
* ⚠️ 本文件解决 2026-09-23 审计出的两个**静默算错**缺陷(详见 memory/dps-calculator.md §12):
*
* 【缺陷一】槽位靠数组下标猜 ⇒ 26/118 角色动作错误或缺失
*   原实现假定 `Skills[0]`=普攻、`Skills[1]`=战技。**实测不成立**:
*   Skills 长度分布 {2:114, 3:4},且元数据里**没有任何类型判别字段**
*   (358 条技能对象的键集固定,无 type;`Proud.Display` 全 0;
*    `SpecialEnergyType` 只标玛薇卡/丝柯克)。
*
*   明确破裂的例子 —— `Skills[0]` 是参数全空的伪技能「特殊跳跃」:
*     欧洛伦 10000105: [0]30506 特殊跳跃(desc=0/params=0) [1]30501 宿灵闪箭(其实是普攻) [2]30502 暝色缒索(真战技)
*     茜特菈莉 10000107: [0]11076 特殊跳跃(desc=0/params=0) [1]11071 宿灵捕影(其实是普攻) [2]11072 霜昼黑星(真战技)
*   ⇒ 这两人的普攻**静默丢失**,战技取成了普攻的值,真战技从未被读。
*
*   ✅ 正确判别(= 胡桃的原规则,`SkillDepot.cs:25`
*      `CompositeSkillsNoInherents => [.. Skills.Where(s => s.Proud.Parameters.Count > 1), EnergySkill]`):
*      先滤掉 `Proud.Parameters.size <= 1` 的伪技能/被动,再取下标 0/1。
*   **实测 118/118 角色过滤后恰好剩 2 个**,且两条独立判据**完全一致**:
*      kept[0] 全部无 `CdTime`(118/118)、kept[1] 全部有 `CdTime`(118/118)。
*
* 【缺陷二】倍率硬取参数下标 0 ⇒ 19 条非伤害项被当成倍率
*   `Descriptions` 里混着"持续时间/冷却/治疗量/护盾吸收量/元素能量"等**非伤害项**,
*   结构与伤害项完全相同,且 `{paramN}` 是 **1-based 且不保证连续**
*   ⇒ "数组下标 0" 与 "param1" **不是一回事**。
*   实测最离谱的几条:米卡 Q`施放治疗量`=1172.0355、莉奈娅 Q`首次治疗量`=770.3755、
*   凝光 E`继承生命`=**-0.499(负倍率)**。
*   ⚠️ 原实现还**自造标签** `"${name} · 技能伤害"`,UI 上完全看不出取错项。
*
*   ✅ 正确做法:`SkillScalingParser.labeledMultipliers()` 按 `{paramN}` 正确取值,
*      再用下方 [isDamageEntry] 保守筛选。
*
* ⚠️⚠️ 与项目纪律的关系(务必理解,别"顺手增强"):
*   - **不用关键字自动判定"哪些项是伤害"** —— 实测关键字规则有 **32 条假阳性**
*     (`红死之宴提升`/`攻击力提高`/`伤害值提升`…)与 **42 条假阴性**
*     (`瞄准射击`×21/`满蓄力瞄准射击`×17 —— 是伤害实例却不含"伤害"二字)。
*     故本层只做**保守的单条选择**,并把**真实标签原样交给 UI** —— 用户才是最终裁判。
*   - 该保守规则经 118 角色全量实测:选出的值 **0 条离谱(>50 或 <=0)**,
*     且修正了 20 处下标(含凝光 -0.499 → 2.304、七七 0.1056 → 0.96)。
*   - 筛不出伤害项时**返回 null 并记名**,由调用方展示"该技能无可用的伤害倍率项"
*     —— **绝不退回 index 0**(那正是缺陷二要消灭的行为)。
* */

/** 技能槽位。用于 UI 展示与结果归因。 */
enum class SkillSlot(val displayName: String) {
    NORMAL_ATTACK("普通攻击"),
    ELEMENTAL_SKILL("元素战技"),
    ELEMENTAL_BURST("元素爆发")
}

/**
 * 一个已解析的出伤动作。
 *
 * @param slot       来自哪个槽位
 * @param skillName  技能名(元数据原文)
 * @param label      倍率项标签(**元数据原文**,如"一段伤害"、"技能伤害")。
 *                   ⚠️ 必须原样展示给用户 —— 这是用户发现"选错项"的唯一途径,
 *                   不要在 UI 层自造标签(那正是原实现的缺陷之一)。
 * @param multiplier 该等级下的倍率(小数)
 */
data class ResolvedAction(
    val slot: SkillSlot,
    val skillName: String,
    val label: String,
    val multiplier: Double
)

/**
 * 一个"参与不了计算"的技能,以及原因。
 *
 * ⚠️ **必须记名暴露给 UI**,不能静默丢弃 —— 否则用户看到"算了 3 个技能"
 * 却以为是 4 个,数字会莫名其妙偏低(与本项目既有的 skippedMembers 同一原则)。
 */
data class SkippedSkill(
    val slot: SkillSlot,
    val skillName: String,
    val reason: String
)

/** 槽位解析结果 */
data class SkillSlotResolution(
    val actions: List<ResolvedAction>,
    val skipped: List<SkippedSkill>
)

object SkillSlotResolver {

    /**
     * 判定某个倍率项是否**可能是**伤害项。
     *
     * 保守策略三重条件(全部满足才接受):
     *   1. 该等级下能取到正数值(取不到 / <=0 一律不要)
     *   2. 标签含"伤害"
     *   3. 标签不含非伤害词(提升/加成/治疗/护盾/消耗/持续时间/元素能量…)
     *
     * ⚠️ 这是**筛选**而非**判定真相** —— 命中的项仍可能不是玩家想要的那一段。
     * 故标签必须原样展示,且后续版本应支持用户自行勾选(见 memory §12.6)。
     */
    fun isDamageEntry(entry: ScalingEntry): Boolean {
        val value = entry.multiplier ?: return false
        if (value <= 0.0) return false
        val label = entry.label
        if (!label.contains("伤害")) return false
        return NON_DAMAGE_WORDS.none { label.contains(it) }
    }

    /**
     * 从带标签的倍率项里挑出**第一条**保守判定为伤害的项。
     *
     * @return 取不到时返回 null —— ⚠️ **不退回 index 0**(见文件头「缺陷二」)
     */
    fun pickPrimaryDamageEntry(entries: List<ScalingEntry>): ScalingEntry? =
        entries.firstOrNull { isDamageEntry(it) }

    /**
     * 取出三个可计算技能的槽位(普攻 / 战技 / 爆发)。
     *
     * ⚠️ 规则来自胡桃 `SkillDepot.cs:25`:先滤掉 `Proud.Parameters.size <= 1`
     * 的伪技能与被动,再取前两个;爆发固定取 `EnergySkill`。
     * 实测 118/118 角色的过滤结果恰好为 2 个。
     *
     * @return 长度 2 的列表(可能不足:极端脏数据下返回已有的部分)
     */
    fun calculableSkills(depot: AvatarData.SkillDepot): List<AvatarData.Skill> =
        depot.Skills.filter { it.Proud.Parameters.size > 1 }

    /**
     * 解析一个角色的全部出伤动作。
     *
     * @param depot          角色元数据
     * @param levelBySkillId 接口返回的 `skills[]`(skill_id → level)。
     *                       ⚠️ 键是**元数据的 `Id`**,不是 `GroupId`
     *                       (实测 `skill_id ∩ GroupId` 恒为空集)
     */
    fun resolve(
        depot: AvatarData.SkillDepot,
        levelBySkillId: Map<Int, Int>
    ): SkillSlotResolution {
        val actions = mutableListOf<ResolvedAction>()
        val skipped = mutableListOf<SkippedSkill>()

        val kept = calculableSkills(depot)

        // 槽位 → 技能。索引不足时记名跳过(不静默)
        val slotSkills = listOf(
            SkillSlot.NORMAL_ATTACK to kept.getOrNull(0),
            SkillSlot.ELEMENTAL_SKILL to kept.getOrNull(1),
            SkillSlot.ELEMENTAL_BURST to depot.EnergySkill
        )

        slotSkills.forEach { (slot, skill) ->
            if (skill == null) {
                // 理论上不会发生(实测 118/118 都够),但脏数据下要诚实说明
                skipped += SkippedSkill(slot, "(缺失)", "元数据里找不到该槽位的技能")
                return@forEach
            }

            val name = skill.Name

            val level = levelBySkillId[skill.Id]
            if (level == null || level <= 0) {
                // 不猜等级:猜等于编造
                skipped += SkippedSkill(slot, name, "角色详情未返回该技能等级")
                return@forEach
            }

            val entries = SkillScalingParser.labeledMultipliers(
                template = skill.Proud.Descriptions,
                proud = skill.Proud,
                level = level
            )

            if (entries.isEmpty()) {
                skipped += SkippedSkill(slot, name, "元数据里没有该技能的倍率数据")
                return@forEach
            }

            val picked = pickPrimaryDamageEntry(entries)
            if (picked == null) {
                // ⚠️ 关键:这里**不退回 index 0**。例如:
                //   芭芭拉/魈/荒泷一斗/纳西妲/米卡/赛索斯/菈乌玛/莉奈娅 的爆发本身
                //   就只有治疗量/增益/能量,没有任何伤害倍率(实测 8 个)
                skipped += SkippedSkill(slot, name, "该技能没有可用的伤害倍率项")
                return@forEach
            }

            actions += ResolvedAction(
                slot = slot,
                skillName = name,
                // 元数据原文标签,不做任何加工
                label = picked.label,
                multiplier = picked.multiplier!!
            )
        }

        return SkillSlotResolution(actions, skipped)
    }

    /**
     * 非伤害项标签里的特征词。
     *
     * ⚠️ 刻意**只用于排除**,不用于确认 —— 因为这类词本身也不完备
     * (实测"伤害值提升"含"提升"会被排除,正确;而"瞄准射击"不含"伤害"
     *  会被"含伤害"这一条排除,属**假阴性**,已由"筛不出就记名跳过"兜住)。
     */
    private val NON_DAMAGE_WORDS = listOf(
        "提升", "加成", "增加", "提高", "比例", "转化",
        "每点", "每层", "每100点", "百分比",
        "暴击", "治疗", "护盾", "消耗", "持续时间", "冷却", "元素能量",
        "继承生命", "次数", "上限", "降低", "抗性", "速度", "回复", "吸收",
        "流失", "能量"
    )
}
