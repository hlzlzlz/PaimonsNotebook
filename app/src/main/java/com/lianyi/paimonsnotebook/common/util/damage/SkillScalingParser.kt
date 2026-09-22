package com.lianyi.paimonsnotebook.common.util.damage

import com.lianyi.paimonsnotebook.common.web.hutao.genshin.avatar.AvatarData

/*
* 技能倍率数值解析(纯逻辑层)
*
* 背景:本地元数据 Snap.Metadata 的角色 json 里,SkillDepot.Proud 长这样:
*
*   Descriptions = ["切割伤害|{param1:P}", "绽放伤害|{param2:P}", ...]
*   Parameters   = [ {Level=1, Parameters=[1.123, 1.6845, 5, 20, 80]}, ... 共 15 级 ]
*
* 即"模板 + 逐等级数值"。PN 侧 AvatarData.Proud 已完整建模这两者,
* 且已有一个 descriptions(level) 函数 —— 但它**只产出格式化后的字符串**
* (用于展示技能文案),拿不到**数值**,而伤害计算需要的是 Double。
*
* 本文件的作用:从同一份数据里取出**数值**,供伤害公式使用。
* 不重复实现模板渲染,直接按 paramN 下标取 Parameters[N-1]。
*
* ⚠️ 与 UI 展示的区别(容易搞混):
*   - 展示用:descriptions(level) → ["切割伤害" to "112.3%"]
*   - 计算用:multipliers(level)  → [1.123, 1.6845, ...]
*   两者必须取自**同一个 level**,否则会出现"显示 112.3% 但按 168.45% 算"的错位。
* */
object SkillScalingParser {

    /**
     * 解析某个技能在指定等级的**全部数值**。
     *
     * @param proud  技能倍率数据(来自 AvatarData.Skill.Proud)
     * @param level  技能等级(1..15)。超出范围时返回空列表,由调用方决定降级策略
     * @return 该等级下的参数值列表(与 Descriptions 里的 paramN 下标对应)
     */
    fun multipliers(proud: AvatarData.Proud, level: Int): List<Double> {
        val param = proud.Parameters.firstOrNull { it.Level == level } ?: return emptyList()
        return param.Parameters.map { it.toDouble() }
    }

    /**
     * 解析"带标签的倍率项" —— 把模板与数值配对,便于 UI 展示与用户选择具体哪一段。
     *
     * 例:神里绫华大招 Lv1 ⇒
     *   [ScalingEntry(label="切割伤害", multiplier=1.123),
     *    ScalingEntry(label="绽放伤害", multiplier=1.6845),
     *    ScalingEntry(label="持续时间", multiplier=5.0),   ← 注意:非伤害项也在内
     *    ScalingEntry(label="冷却时间", multiplier=20.0),
     *    ScalingEntry(label="元素能量", multiplier=80.0)]
     *
     * ⚠️ **返回值里混有非伤害项**(持续时间/冷却/能量等),这是数据本身的结构,
     * 不是 bug。调用方若只想要"伤害倍率",必须自行筛选 —— 但**本函数刻意不做筛选**,
     * 因为"哪一项算伤害"无法从数据里可靠推断(如"一段伤害"是,"持续时间"不是,
     * 而二者结构完全相同)。UI 上应让用户自己勾选,而不是由代码猜。
     *
     * @param template 模板字符串列表(来自 Proud.Descriptions)
     * @param level    技能等级
     */
    fun labeledMultipliers(
        template: List<String>,
        proud: AvatarData.Proud,
        level: Int
    ): List<ScalingEntry> {
        val values = multipliers(proud, level)
        return template.mapIndexed { index, line ->
            val label = line.substringBefore("|")
            // 模板里 {paramN:P} 的 N 是**1-based**,故取下标 N-1
            val paramIndex = PARAM_REGEX.find(line)
                ?.groupValues?.getOrNull(1)?.toIntOrNull()
            val value = paramIndex?.let { values.getOrNull(it - 1) }
            ScalingEntry(
                label = label,
                multiplier = value,
                paramIndex = paramIndex ?: (index + 1)
            )
        }
    }

    /**
     * 便捷入口:直接从技能取"第 index 项"的倍率。
     *
     * 用于"用户已明确选定某一段"的场景(如绫华大招的切割伤害 = 第 1 项)。
     *
     * @return 取不到时返回 null(而非 0.0)—— 0.0 会被当成"合法倍率"算出 0 伤害,
     *         掩盖"数据缺失"这一事实。调用方必须显式处理 null。
     */
    fun multiplierAt(proud: AvatarData.Proud, level: Int, index: Int): Double? =
        multipliers(proud, level).getOrNull(index)

    /** 该技能是否有可用倍率(用于 UI 判断能否参与计算) */
    fun hasScaling(proud: AvatarData.Proud, level: Int): Boolean =
        multipliers(proud, level).isNotEmpty()

    /** 匹配 {param1:P} / {param12:F1} 中的数字部分 */
    private val PARAM_REGEX = Regex("\\{param(\\d+)")
}

/**
 * 一个带标签的倍率项。
 *
 * @param label      标签(如 "切割伤害"、"持续时间")
 * @param multiplier 数值;**为 null 表示该等级下取不到值**(数据缺失),
 *                   调用方必须处理 null,不可当作 0.0
 * @param paramIndex 模板里的 paramN 下标(1-based),便于与模板对照排查
 */
data class ScalingEntry(
    val label: String,
    val multiplier: Double?,
    val paramIndex: Int
)
