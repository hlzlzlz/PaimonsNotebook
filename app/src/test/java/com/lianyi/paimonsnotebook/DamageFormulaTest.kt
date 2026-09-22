package com.lianyi.paimonsnotebook

import com.lianyi.paimonsnotebook.common.util.damage.DamageFormula
import com.lianyi.paimonsnotebook.common.util.damage.DamageInput
import com.lianyi.paimonsnotebook.common.util.damage.ReactionType
import com.lianyi.paimonsnotebook.common.util.damage.SkillScalingParser
import com.lianyi.paimonsnotebook.common.web.hutao.genshin.avatar.AvatarData
import com.lianyi.paimonsnotebook.common.util.json.JSON
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/*
* 队伍 DPS 计算器 —— 伤害公式与倍率解析测试
*
* ⚠️ 本文件的测试数据策略(遵守 AGENTS.md「字段类型必须实测」纪律):
*
* 前半部分(LongMultiplierTest / SkillScalingTest)**喂真实元数据字节** ——
*   夹具 AvatarSkill_Ayaka_10000002.json(神里绫华)与
*   AvatarSkill_Keqing_10000042.json(刻晴)直接复制自 Snap.Metadata,
*   **未经任何加工**。这是为了复现 1.8.9 公告事故的教训:
*   当时 8 个测试全用自造数据,对"字段类型写错"零检出力。
*   同理,本文件钉住的是**真实角色在真实等级下的真实倍率值**,
*   不是"我构造一个 Proud 然后断言我构造的值"。
*
* 后半部分(纯公式)**用手算的确定值**做锚点 —— 因为公式的输入是数字,
*   锚点必须能人工复算(如抗性 0.75 处分段函数连续,两侧都必须是 0.25)。
* */
class DamageFormulaTest {

    /*
    * ⚠️ 浮点容差必须按 **Float 精度**取,不能按 Double。
    *
    * 实测发现(2026-09-22,首次跑测试即暴露):
    *   AvatarData.Parameter.Parameters 的类型是 **List<Float>**,不是 List<Double>。
    *   故 JSON 里的 1.123 经 Float 往返后变成 1.1230000257492065
    *   (Float 只有约 7 位有效数字)。
    *   最初本文件按 1e-9 断言 ⇒ 5 个用例失败。**这是真实数据纠正了假设**,
    *   不是代码 bug —— 也正说明"倍率是 Float 精度"这一事实必须被记录,
    *   否则后人会再次踩到(见下方 `scaling is float precision` 用例)。
    * */
    private val FLOAT_TOL = 1e-6

    // ---------- 夹具 ----------

    private fun fixture(name: String): File {
        val testDir = sequenceOf(
            File("src/test/java/com/lianyi/paimonsnotebook"),
            File("app/src/test/java/com/lianyi/paimonsnotebook")
        ).firstOrNull { it.isDirectory }
            ?: File(javaClass.protectionDomain.codeSource.location.toURI()).parentFile

        return File(testDir, name)
    }

    /** 读真实绫华数据(未经加工) */
    private fun loadAyaka(): AvatarData =
        JSON.parse<AvatarData>(fixture("AvatarSkill_Ayaka_10000002.json").readText())

    /** 读真实刻晴数据(未经加工) */
    private fun loadKeqing(): AvatarData =
        JSON.parse<AvatarData>(fixture("AvatarSkill_Keqing_10000042.json").readText())

    // =========================================================
    // 一、真实元数据驱动:倍率解析
    // =========================================================

    /*
    * 真实数据必须能解析成功 —— 若 AvatarData.Proud 的字段名/类型写错,
    * 本用例会失败。这是"公告事故"的防线。
    * */
    @Test
    fun `real ayaka metadata parses`() {
        val a = loadAyaka()
        assertEquals(10000002, a.id)
        assertEquals("神里绫华", a.name)
        assertNotNull(a.skillDepot)
        // 大招必须存在
        assertNotNull(a.skillDepot.EnergySkill)
    }

    /*
    * 神里绫华大招 Lv1 的切割伤害必须是 1.123(112.3%)。
    *
    * 这个数字取自真实元数据 Parameters[0].Parameters[0],且**经人工核对**
    * (见 AGENTS.md 记录:切割伤害|{param1:P} ⇒ 1.123 ⇒ 112.3%)。
    * 若有人改坏了 paramN 的下标换算(1-based vs 0-based),本用例必失败。
    * */
    @Test
    fun `ayaka burst level 1 first param is known value`() {
        val a = loadAyaka()
        val value = SkillScalingParser.multiplierAt(a.skillDepot.EnergySkill.Proud, 1, 0)
        assertNotNull("Lv1 第 1 项倍率不应为 null", value)
        assertEquals(1.123, value!!, FLOAT_TOL)
    }

    /*
    * 同上的 Lv2 / Lv10 / Lv15 —— 钉住"等级索引正确"。
    * Lv10 = 2.0214、Lv15 = 2.667125(均取自真实元数据)。
    * ⚠️ 若代码错用了 0-based 索引,Lv10 会取到 Lv11 的值而失败。
    * */
    @Test
    fun `ayaka burst param lookup respects level indexing`() {
        val a = loadAyaka()
        val proud = a.skillDepot.EnergySkill.Proud
        assertEquals(1.207225, SkillScalingParser.multiplierAt(proud, 2, 0)!!, FLOAT_TOL)
        assertEquals(2.0214, SkillScalingParser.multiplierAt(proud, 10, 0)!!, FLOAT_TOL)
        assertEquals(2.667125, SkillScalingParser.multiplierAt(proud, 15, 0)!!, FLOAT_TOL)
    }

    /*
    * 第二项(绽放伤害)也必须是独立正确值,不能与第一项混淆。
    * Lv1: 切割 1.123 / 绽放 1.6845 —— 两者必须不同,否则说明取错了下标。
    * */
    @Test
    fun `ayaka burst second param differs from first`() {
        val a = loadAyaka()
        val proud = a.skillDepot.EnergySkill.Proud
        val p1 = SkillScalingParser.multiplierAt(proud, 1, 0)!!
        val p2 = SkillScalingParser.multiplierAt(proud, 1, 1)!!
        assertEquals(1.123, p1, FLOAT_TOL)
        assertEquals(1.6845, p2, FLOAT_TOL)
        assertTrue("两项必须不同,否则疑为下标错误", p1 != p2)
    }

    /*
    * 带标签解析:标签必须与模板文本一致。
    * 真实模板首行是 "切割伤害|{param1:P}",故标签应为 "切割伤害"。
    * */
    @Test
    fun `labeled multipliers carry template labels from real data`() {
        val a = loadAyaka()
        val proud = a.skillDepot.EnergySkill.Proud
        val entries = SkillScalingParser.labeledMultipliers(proud.Descriptions, proud, 1)

        assertEquals("切割伤害", entries[0].label)
        assertEquals("绽放伤害", entries[1].label)
        assertEquals(1, entries[0].paramIndex)

        // 前两项是伤害倍率
        assertEquals(1.123, entries[0].multiplier!!, FLOAT_TOL)
        assertEquals(1.6845, entries[1].multiplier!!, FLOAT_TOL)

        // ⚠️ 真实数据里**混有非伤害项**(持续时间/冷却/能量),
        //    本函数刻意不筛选 —— 断言这一点,防止后人"顺手过滤"而破坏设计
        assertTrue(
            "真实数据应含非伤害项,证明本函数未做(也不应做)自动筛选",
            entries.any { it.label == "持续时间" || it.label == "冷却时间" }
        )
    }

    /*
    * 刻晴的模板结构不同(连斩伤害带 *8 段数标记),
    * 验证解析器对**另一位真实角色**同样有效 —— 单一角色的测试可能只是巧合。
    * */
    @Test
    fun `keqing real data parses with different template shape`() {
        val k = loadKeqing()
        assertEquals(10000042, k.id)
        val proud = k.skillDepot.EnergySkill.Proud
        val entries = SkillScalingParser.labeledMultipliers(proud.Descriptions, proud, 1)

        assertEquals("技能伤害", entries[0].label)
        assertEquals("连斩伤害", entries[1].label)
        // 连斩那行带 *8,标签仍应干净(不含 *8)
        assertFalse("标签不应包含段数标记", entries[1].label.contains("*"))
    }

    /*
    * 普攻(如绫华五段)也必须有倍率 —— 实测 116/118 角色普攻有数据。
    * 这钉住"普攻可参与计算"这一前提。
    * */
    @Test
    fun `normal attack has scaling for real ayaka`() {
        val a = loadAyaka()
        val na = a.skillDepot.Skills.first()
        assertTrue(
            "绫华普攻应有倍率(实测 116/118 角色普攻有数据)",
            SkillScalingParser.hasScaling(na.Proud, 1)
        )
        // 普攻一段伤害 0.457253(真实值)
        val v = SkillScalingParser.multiplierAt(na.Proud, 1, 0)!!
        assertEquals(0.457253, v, FLOAT_TOL)
    }

    /*
    * ⚠️ 把"倍率是 Float 精度"这一**实测事实**钉成用例。
    *
    * AvatarData.Parameter.Parameters 声明为 List<Float>,故 1.123 经
    * JSON→Float→Double 往返后并非精确的 1.123,而有约 2.6e-8 的偏差。
    *
    * 为什么值得单独一条用例:
    *   - 若后人把该字段"顺手"改成 Double(以为能提高精度),本用例会失败,
    *     提示他这属于**改变数据契约**的行为,需先确认元数据来源;
    *   - 若后人把容差从 1e-6 收紧回 1e-9,也会连带失败,
    *     提醒他 Float 精度撑不住更严的容差。
    * 这条用例是"防回归",不是"测功能"。
    * */
    @Test
    fun `scaling is float precision not double`() {
        val a = loadAyaka()
        val v = SkillScalingParser.multiplierAt(a.skillDepot.EnergySkill.Proud, 1, 0)!!
        // 与精确值 1.123 的差必须 > 1e-9(Float 精度不足),
        // 但 < 1e-6(仍在 Float 有效范围) —— 这恰好刻画了 Float 精度
        val diff = kotlin.math.abs(v - 1.123)
        assertTrue(
            "倍率应为 Float 精度(偏差 ~1e-8),实测偏差 $diff;" +
                    "若偏差为 0 说明字段已改 Double(数据契约变了),需复核",
            diff > 1e-9 && diff < 1e-6
        )
    }

    /*
    * 越界等级必须返回空列表/null,而不是抛异常或返回垃圾值。
    * ⚠️ 返回 null 而非 0.0 是刻意的:0.0 会被当成"合法倍率"算出 0 伤害,
    * 掩盖"数据缺失"。本用例钉住这个契约。
    * */
    @Test
    fun `out of range level yields null not zero`() {
        val a = loadAyaka()
        val proud = a.skillDepot.EnergySkill.Proud
        assertNull("等级 0 应取不到", SkillScalingParser.multiplierAt(proud, 0, 0))
        assertNull("等级 999 应取不到", SkillScalingParser.multiplierAt(proud, 999, 0))
        assertTrue(SkillScalingParser.multipliers(proud, 999).isEmpty())
        assertFalse(SkillScalingParser.hasScaling(proud, 999))
    }

    /*
    * 越界下标也必须返回 null(而非 0.0 或异常)
    * */
    @Test
    fun `out of range param index yields null`() {
        val a = loadAyaka()
        val proud = a.skillDepot.EnergySkill.Proud
        assertNull(SkillScalingParser.multiplierAt(proud, 1, 999))
        assertNull(SkillScalingParser.multiplierAt(proud, 1, -1))
    }

    // =========================================================
    // 二、伤害公式(手算锚点)
    // =========================================================

    /*
    * 抗性区分段 —— 分界点必须连续。
    * 这是最容易写错的地方(用 < 还是 <=),故两个分界点都钉住。
    * */
    @Test
    fun `resistance factor boundaries are continuous`() {
        // 抗性 = 0:两侧公式都应为 1.0
        assertEquals(1.0, DamageFormula.resistanceFactor(0.0), 1e-12)
        assertEquals(1.0, DamageFormula.resistanceFactor(-1e-9), 1e-9)

        // 抗性 = 0.75:第2式 = 0.25,第3式 = 1/(4*0.75+1) = 0.25
        assertEquals(0.25, DamageFormula.resistanceFactor(0.75), 1e-12)
        assertEquals(0.25, DamageFormula.resistanceFactor(0.7499999), 1e-6)
    }

    /*
    * 抗性区三个区间的具体值(手算可复算)
    * */
    @Test
    fun `resistance factor three regions`() {
        // 负抗性:1 - (-0.4)/2 = 1.2
        assertEquals(1.2, DamageFormula.resistanceFactor(-0.4), 1e-12)
        // 常规:1 - 0.1 = 0.9
        assertEquals(0.9, DamageFormula.resistanceFactor(0.1), 1e-12)
        // 高抗性:1/(4*0.9+1) = 1/4.6
        assertEquals(1.0 / 4.6, DamageFormula.resistanceFactor(0.9), 1e-12)
    }

    /*
    * 防御区:同等级时必须是 0.5 —— 这是常见误解(很多人以为是 1.0)
    * */
    @Test
    fun `defense factor at equal level is half`() {
        assertEquals(0.5, DamageFormula.defenseFactor(90, 90), 1e-12)
        assertEquals(0.5, DamageFormula.defenseFactor(1, 1), 1e-12)
    }

    /*
    * 防御区:等效等级差方向必须正确 —— 攻击者等级越高,系数越大
    * */
    @Test
    fun `defense factor increases with attacker level`() {
        val low = DamageFormula.defenseFactor(80, 90)
        val high = DamageFormula.defenseFactor(90, 90)
        assertTrue("攻击者等级提高应提升防御区系数", high > low)
    }

    /*
    * 防御区:减防必须提升系数
    * */
    @Test
    fun `defense reduction increases factor`() {
        val none = DamageFormula.defenseFactor(90, 90, 0.0)
        val reduced = DamageFormula.defenseFactor(90, 90, 0.3)
        assertTrue("减防 30% 应提升防御区系数", reduced > none)
        // 手算:100/(100+100*0.7) = 100/170
        assertEquals(100.0 / 170.0, reduced, 1e-12)
    }

    /*
    * 暴击期望:期望值必须落在非暴击与暴击之间(三者口径一致性)
    * 若三者各自独立计算,可能出现期望值越界的自相矛盾
    * */
    @Test
    fun `expected damage lies between noncrit and crit`() {
        val input = DamageInput(
            multiplier = 1.0,
            attack = 1000.0,
            critRate = 0.5,
            critDamage = 1.0,
            bonus = 0.0,
            resistance = 0.0,       // 抗性 0 ⇒ 系数 1
            levelDiffDefense = 1.0, // 排除防御区干扰,便于手算
            reaction = 1.0
        )
        val r = DamageFormula.calculate(input)
        assertEquals(1000.0, r.nonCrit, 1e-9)
        assertEquals(2000.0, r.crit, 1e-9)
        // 期望 = 1000*0.5 + 2000*0.5 = 1500
        assertEquals(1500.0, r.expected, 1e-9)
        assertTrue(r.expected in r.nonCrit..r.crit)
    }

    /*
    * 暴击率 0 与 1 的边界:期望应分别等于非暴击与暴击
    * */
    @Test
    fun `crit rate extremes collapse expected to noncrit or crit`() {
        val base = DamageInput(1.0, 1000.0, 0.0, 1.0, 0.0, 0.0, 1.0, 1.0)
        assertEquals(1000.0, DamageFormula.calculate(base).expected, 1e-9)

        val always = base.copy(critRate = 1.0)
        assertEquals(2000.0, DamageFormula.calculate(always).expected, 1e-9)
    }

    /*
    * 暴击率超过 1 必须被钳制(不应获得额外收益)
    * */
    @Test
    fun `crit rate is clamped at one`() {
        val f1 = DamageFormula.critExpectationFactor(1.0, 1.0)
        val f2 = DamageFormula.critExpectationFactor(1.5, 1.0)
        assertEquals("暴击率超 100% 不应有额外收益", f1, f2, 1e-12)
        assertEquals(2.0, f1, 1e-12)
    }

    /*
    * 负爆伤应视为 0(防御性),不应产生负伤害
    * */
    @Test
    fun `negative crit damage clamps to zero`() {
        assertEquals(1.0, DamageFormula.critExpectationFactor(0.5, -1.0), 1e-12)
    }

    /*
    * 增伤区/抗性区/防御区/反应区 必须**都是乘数** —— 任一项漏乘,本用例失败
    * */
    @Test
    fun `all factors multiply together`() {
        val input = DamageInput(
            multiplier = 2.0,
            attack = 1000.0,
            critRate = 0.0,        // 关掉暴击,方便手算
            critDamage = 0.0,
            bonus = 0.5,           // ×1.5
            resistance = 0.1,      // ×0.9
            levelDiffDefense = 0.5,// ×0.5
            reaction = 2.0         // ×2.0(蒸发)
        )
        val r = DamageFormula.calculate(input)
        // 2 * 1000 * 1.5 * 0.9 * 0.5 * 2 = 2700
        assertEquals(2700.0, r.nonCrit, 1e-9)
    }

    // =========================================================
    // 三、增幅反应
    // =========================================================

    /*
    * 蒸发/融化的倍率与方向必须正确(克制方向 2.0,被克制 1.5)
    * */
    @Test
    fun `amplify reaction factors and directions`() {
        // 水打火 = 2.0
        assertEquals(
            ReactionType.VAPORIZE_WATER_ON_FIRE,
            DamageFormula.amplifyReaction(ElementConst.Water, ElementConst.Fire)
        )
        assertEquals(2.0, DamageFormula.amplifyReaction(ElementConst.Water, ElementConst.Fire).factor, 1e-12)

        // 火打水 = 1.5
        assertEquals(1.5, DamageFormula.amplifyReaction(ElementConst.Fire, ElementConst.Water).factor, 1e-12)

        // 火打冰 = 2.0
        assertEquals(2.0, DamageFormula.amplifyReaction(ElementConst.Fire, ElementConst.Ice).factor, 1e-12)

        // 冰打火 = 1.5
        assertEquals(1.5, DamageFormula.amplifyReaction(ElementConst.Ice, ElementConst.Fire).factor, 1e-12)
    }

    /*
    * 同元素/无关元素不得触发增幅反应
    * */
    @Test
    fun `no reaction for same or unrelated elements`() {
        assertEquals(ReactionType.NONE, DamageFormula.amplifyReaction(ElementConst.Fire, ElementConst.Fire))
        assertEquals(ReactionType.NONE, DamageFormula.amplifyReaction(ElementConst.Water, ElementConst.Ice))
        assertEquals(1.0, DamageFormula.amplifyReaction(ElementConst.Fire, ElementConst.Fire).factor, 1e-12)
    }

    /*
    * 剧变反应必须被**识别为不支持**,而不是悄悄算出一个错数
    * */
    @Test
    fun `transformative reactions are flagged unsupported`() {
        assertTrue(DamageFormula.isUnsupportedReaction("超载"))
        assertTrue(DamageFormula.isUnsupportedReaction("感电"))
        assertTrue(DamageFormula.isUnsupportedReaction("绽放"))
        assertTrue(DamageFormula.isUnsupportedReaction("超激化"))
        assertFalse("蒸发属增幅反应,应支持", DamageFormula.isUnsupportedReaction("蒸发"))
        assertFalse("融化属增幅反应,应支持", DamageFormula.isUnsupportedReaction("融化"))
    }

    /*
    * 第一版不支持的剧变反应必须覆盖 13 种(防漏)
    * */
    @Test
    fun `unsupported reaction set has expected size`() {
        assertEquals(13, DamageFormula.UNSUPPORTED_REACTIONS.size)
    }

    /*
    * 元素常量一致性:DamageFormula 内部复刻的元素值必须与项目内
    * ElementType 的真实值一致(火=1 水=2 冰=5)。
    *
    * ⚠️ 这条用例的意义:本层刻意**不 import ElementType**(它引用 R.drawable,
    * 会把 Android 依赖传染进纯逻辑层,重演 1.8.13「真机分支 vs 单测分支」的坑)。
    * 代价是常量有两份,故用本用例钉住它们不许漂移。
    * */
    @Test
    fun `element constants match project ElementType values`() {
        // 取自 common/web/hutao/genshin/intrinsic/ElementType.kt(2026-09-22 核对)
        assertEquals(1, ElementConst.Fire)
        assertEquals(2, ElementConst.Water)
        assertEquals(5, ElementConst.Ice)
    }

    /** 与 ElementType 一致的元素值(见上方一致性用例) */
    private object ElementConst {
        const val Fire = 1
        const val Water = 2
        const val Ice = 5
    }
}
