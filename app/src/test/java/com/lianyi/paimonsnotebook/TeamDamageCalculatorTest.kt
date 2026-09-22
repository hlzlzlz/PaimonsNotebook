package com.lianyi.paimonsnotebook

import com.lianyi.paimonsnotebook.common.util.damage.ActionDamage
import com.lianyi.paimonsnotebook.common.util.damage.AvatarPanel
import com.lianyi.paimonsnotebook.common.util.damage.MemberAction
import com.lianyi.paimonsnotebook.common.util.damage.PanelAdapter
import com.lianyi.paimonsnotebook.common.util.damage.ReactionType
import com.lianyi.paimonsnotebook.common.util.damage.TeamDamageCalculator
import com.lianyi.paimonsnotebook.common.util.damage.TeamMember
import com.lianyi.paimonsnotebook.common.util.json.JSON
import com.lianyi.paimonsnotebook.common.web.hoyolab.takumi.game_record.character.CharacterDetailData
import com.lianyi.paimonsnotebook.common.web.hutao.genshin.avatar.AvatarData
import com.lianyi.paimonsnotebook.common.web.hutao.genshin.intrinsic.FightProperty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/*
* 队伍 DPS 计算器 —— M3:面板适配 + 队伍编排 测试
*
* ⚠️ 数据策略(遵守 AGENTS.md「字段类型必须实测」):
*   - 技能倍率部分:喂**真实元数据**(绫华/刻晴夹具,与 M2 同一份)
*   - 面板部分:`character/detail` 需要真实凭证,本机**无法实测**该接口的
*     字符串格式。故此处**明确标注**哪些断言是"按服务端已知格式构造的样例",
*     而不是假装它是真实响应。这些样例的格式依据是:
*       ① 现有 UI `PlayerCharacterPropertyItem.kt:63` 直接 `Text(data.final)`,
*          说明 final 是**已格式化字符串**;
*       ② `FormatMethod.kt` 明确哪些属性是 Percent(带 %)、哪些是 Integer。
*     ⇒ 真实格式仍待真机/真实凭证确认,已在 UI 与记忆中标为未验证事项。
* */
class TeamDamageCalculatorTest {

    private fun fixture(name: String): File {
        val testDir = sequenceOf(
            File("src/test/java/com/lianyi/paimonsnotebook"),
            File("app/src/test/java/com/lianyi/paimonsnotebook")
        ).firstOrNull { it.isDirectory }
            ?: File(javaClass.protectionDomain.codeSource.location.toURI()).parentFile
        return File(testDir, name)
    }

    private fun loadAyaka(): AvatarData =
        JSON.parse<AvatarData>(fixture("AvatarSkill_Ayaka_10000002.json").readText())

    /** 构造一条接口形态的 Property */
    private fun prop(type: Int, finalValue: String) =
        CharacterDetailData.Property(add = "", base = "", `final` = finalValue, property_type = type)

    // =========================================================
    // 一、面板解析(PanelAdapter)
    // =========================================================

    /*
    * 百分比字符串必须被换算成小数: "61.6%" → 0.616
    * 这是最容易错的地方(忘了 /100 会让伤害虚高 100 倍)
    * */
    @Test
    fun `percent string is converted to decimal`() {
        val p = prop(FightProperty.FIGHT_PROP_CRITICAL, "61.6%")
        val v = PanelAdapter.parseProperty(p)
        assertNotNull(v)
        assertEquals(0.616, v!!, 1e-9)
    }

    /*
    * 整数属性(mastery / attack)不带 %,不应被 /100
    * */
    @Test
    fun `non percent string stays as absolute value`() {
        assertEquals(1234.0, PanelAdapter.parseProperty(prop(FightProperty.FIGHT_PROP_ATTACK, "1234"))!!, 1e-9)
        assertEquals(120.0, PanelAdapter.parseProperty(prop(FightProperty.FIGHT_PROP_ELEMENT_MASTERY, "120"))!!, 1e-9)
    }

    /*
    * 带千分位的数字要能解析(服务端大数字可能带逗号)
    * */
    @Test
    fun `thousands separator is tolerated`() {
        assertEquals(12345.0, PanelAdapter.parseProperty(prop(FightProperty.FIGHT_PROP_ATTACK, "12,345"))!!, 1e-9)
    }

    /*
    * 空串/垃圾串必须返回 null 而不是抛异常,也不能返回 0
    * ⚠️ 返回 0 会把"数据缺失"伪装成"属性为 0",与本项目纪律冲突
    * */
    @Test
    fun `unparsable value yields null not zero`() {
        assertNull(PanelAdapter.parseProperty(prop(FightProperty.FIGHT_PROP_ATTACK, "")))
        assertNull(PanelAdapter.parseProperty(prop(FightProperty.FIGHT_PROP_ATTACK, "N/A")))
        assertNull(PanelAdapter.parseProperty(prop(FightProperty.FIGHT_PROP_ATTACK, "--")))
    }

    /*
    * final 为空时应回退到 base(接口在某些情况下 base 有值而 final 为空)
    * */
    @Test
    fun `falls back to base when final is blank`() {
        val p = CharacterDetailData.Property(
            add = "", base = "888", `final` = "", property_type = FightProperty.FIGHT_PROP_ATTACK
        )
        assertEquals(888.0, PanelAdapter.parseProperty(p)!!, 1e-9)
    }

    /*
    * 完整面板适配:必需项齐备时 isUsable = true
    * */
    @Test
    fun `complete panel is usable`() {
        val panel = PanelAdapter.adapt(
            listOf(
                prop(FightProperty.FIGHT_PROP_ATTACK, "2000"),
                prop(FightProperty.FIGHT_PROP_CRITICAL, "70.0%"),
                prop(FightProperty.FIGHT_PROP_CRITICAL_HURT, "140.0%"),
                prop(FightProperty.FIGHT_PROP_ELEMENT_MASTERY, "100")
            ),
            bonusPropertyTypes = setOf(FightProperty.FIGHT_PROP_ICE_ADD_HURT)
        )
        assertTrue(panel.isUsable)
        assertEquals(2000.0, panel.attack!!, 1e-9)
        assertEquals(0.70, panel.critRate!!, 1e-9)
        assertEquals(1.40, panel.critDamage!!, 1e-9)
        assertEquals(100.0, panel.elementMastery!!, 1e-9)
    }

    /*
    * 缺攻击力 ⇒ 不可用(否则必然算出 0 伤害)
    * */
    @Test
    fun `panel without attack is not usable`() {
        val panel = PanelAdapter.adapt(
            listOf(prop(FightProperty.FIGHT_PROP_CRITICAL, "70.0%"))
        )
        assertFalse("缺攻击力应判定为不可用", panel.isUsable)
        assertNull(panel.attack)
    }

    /*
    * 缺爆伤时应**仍可用**(宽容):不暴击口径依然正确,期望值按爆伤 0 处理
    * 这钉住"次要属性缺失不该导致整个成员不可用"的设计
    * */
    @Test
    fun `panel without crit damage is still usable`() {
        val panel = PanelAdapter.adapt(
            listOf(
                prop(FightProperty.FIGHT_PROP_ATTACK, "2000"),
                prop(FightProperty.FIGHT_PROP_CRITICAL, "50.0%")
            )
        )
        assertTrue("缺爆伤仍应可用(期望值按爆伤 0 算)", panel.isUsable)
        assertNull(panel.critDamage)
    }

    /*
    * 请求收集的增伤类型若在面板里不存在,必须记入 missingProperties
    * —— 供 UI 诚实标注"该属性未取到",而不是当 0 静默处理
    * */
    @Test
    fun `missing bonus property is recorded`() {
        val iceBonus = FightProperty.FIGHT_PROP_ICE_ADD_HURT
        val physBonus = FightProperty.FIGHT_PROP_PHYSICAL_ADD_HURT
        val panel = PanelAdapter.adapt(
            listOf(
                prop(FightProperty.FIGHT_PROP_ATTACK, "2000"),
                prop(FightProperty.FIGHT_PROP_CRITICAL, "50.0%"),
                prop(iceBonus, "46.6%")
            ),
            bonusPropertyTypes = setOf(iceBonus, physBonus)
        )
        assertEquals(0.466, panel.bonusFor(iceBonus)!!, 1e-9)
        assertNull(panel.bonusFor(physBonus))
        assertTrue("缺失的增伤类型应被记录", panel.missingProperties.contains(physBonus))
    }

    /*
    * ⚠️ 这条用例专门验证"逻辑层能否在 JVM 单测里使用 FightProperty / FormatMethod"。
    *
    * 背景:AGENTS.md 记载 `ElementType` 引用 R.drawable,故伤害逻辑层不 import 它。
    * 但 PanelAdapter **必须**按 property_type 取值,依赖 FightProperty 的常量;
    * 而 FormatMethod 的 Percent 判定是"哪些属性带 %"的单一真相来源。
    *
    * 本用例在 JVM 单测中真实调用它们:若这些类在测试环境不可用
    * (如静态初始化触碰 Android API),本用例会失败 —— 那就证明
    * 逻辑层不能用它们,必须改成自持常量表。
    * 这是"用实测决定设计"而非"靠推理假设"。
    * */
    @Test
    fun `fight property and format method are usable in jvm unit tests`() {
        // 常量本身可用
        assertEquals(5, FightProperty.FIGHT_PROP_ATTACK)
        assertEquals(20, FightProperty.FIGHT_PROP_CRITICAL)
        assertEquals(22, FightProperty.FIGHT_PROP_CRITICAL_HURT)

        // FormatMethod 的百分比判定可用,且与"服务端会带 %"一致
        assertTrue("暴击率应为百分比", PanelAdapter.isPercentProperty(FightProperty.FIGHT_PROP_CRITICAL))
        assertTrue("爆伤应为百分比", PanelAdapter.isPercentProperty(FightProperty.FIGHT_PROP_CRITICAL_HURT))
        assertTrue("元素增伤应为百分比", PanelAdapter.isPercentProperty(FightProperty.FIGHT_PROP_ICE_ADD_HURT))
        assertFalse("攻击力应为整数(非百分比)", PanelAdapter.isPercentProperty(FightProperty.FIGHT_PROP_ATTACK))
        assertFalse("元素精通应为整数", PanelAdapter.isPercentProperty(FightProperty.FIGHT_PROP_ELEMENT_MASTERY))
    }

    // =========================================================
    // 二、队伍编排(TeamDamageCalculator)
    // =========================================================

    private fun ayakaPanel() = AvatarPanel(
        attack = 2000.0,
        critRate = 0.70,
        critDamage = 1.40,
        elementMastery = 100.0,
        bonusByPropertyType = mapOf(FightProperty.FIGHT_PROP_ICE_ADD_HURT to 0.466)
    )

    /*
    * 用**真实倍率**驱动一次单人计算,并手算核对。
    *
    * 绫华大招 Lv1 第 1 项(切割伤害)= 1.123,取 1 次:
    *   不暴击 = 1.123 × 2000 × (1 + 0.466) × 抗性区 × 防御区 × 1.0
    *   抗性 0.1 ⇒ 0.9;等级 90v90 ⇒ 0.5
    *         = 1.123 × 2000 × 1.466 × 0.9 × 0.5
    *         = 1.123 × 2000 = 2246;× 1.466 = 3292.636;× 0.9 = 2963.3724;× 0.5 = 1481.6862
    *   暴击 = 1481.6862 × (1 + 1.40) = 3556.04688
    *   期望 = 1481.6862 × 0.3 + 3556.04688 × 0.7 = 444.50586 + 2489.232816 = 2933.738676
    * */
    @Test
    fun `single member damage matches hand calculation with real multiplier`() {
        val ayaka = loadAyaka()
        val m = com.lianyi.paimonsnotebook.common.util.damage.SkillScalingParser
            .multiplierAt(ayaka.skillDepot.EnergySkill.Proud, 1, 0)!!
        assertEquals(1.123, m, 1e-6)

        val team = listOf(
            TeamMember(
                avatarId = 10000002, name = "神里绫华",
                element = 5, // 冰
                panel = ayakaPanel(),
                actions = listOf(
                    MemberAction(
                        label = "神里流·霜灭 · 切割伤害",
                        multiplier = m,
                        count = 1,
                        damageBonus = 0.466,
                        reaction = ReactionType.NONE
                    )
                )
            )
        )

        val result = TeamDamageCalculator.calculate(team, attackerLevel = 90, defenderLevel = 90, resistance = 0.1)
        assertEquals(1, result.members.size)
        val member = result.members.first()

        assertEquals(1481.6862, member.nonCritTotal, 1e-4)
        assertEquals(3556.04688, member.critTotal, 1e-4)
        assertEquals(2933.738676, member.expectedTotal, 1e-4)
    }

    /*
    * 合计必须等于逐动作明细之和(防"两套算式"导致对不上)
    * */
    @Test
    fun `member totals equal sum of action details`() {
        val team = listOf(
            TeamMember(
                avatarId = 1, name = "测试角色", element = 5,
                panel = ayakaPanel(),
                actions = listOf(
                    MemberAction("动作A", 0.5, count = 3),
                    MemberAction("动作B", 1.2, count = 1),
                    MemberAction("动作C", 0.8, count = 5)
                )
            )
        )
        val r = TeamDamageCalculator.calculate(team).members.first()
        assertEquals(r.perAction.sumOf { it.nonCrit }, r.nonCritTotal, 1e-9)
        assertEquals(r.perAction.sumOf { it.crit }, r.critTotal, 1e-9)
        assertEquals(r.perAction.sumOf { it.expected }, r.expectedTotal, 1e-9)
    }

    /*
    * count 必须真的做乘法(多段攻击)
    * */
    @Test
    fun `action count multiplies damage`() {
        fun run(count: Int) = TeamDamageCalculator.calculate(
            listOf(
                TeamMember(1, "A", 5, ayakaPanel(), listOf(MemberAction("x", 1.0, count = count)))
            )
        ).members.first().expectedTotal

        val once = run(1)
        val thrice = run(3)
        assertEquals(once * 3, thrice, 1e-6)
    }

    /*
    * 队伍合计 = 各成员之和
    * */
    @Test
    fun `team total equals sum of members`() {
        val mk = { id: Int, name: String ->
            TeamMember(id, name, 5, ayakaPanel(), listOf(MemberAction("x", 1.0)))
        }
        val r = TeamDamageCalculator.calculate(listOf(mk(1, "A"), mk(2, "B"), mk(3, "C")))
        assertEquals(3, r.members.size)
        assertEquals(r.members.sumOf { it.expectedTotal }, r.teamExpected, 1e-9)
        assertEquals(r.members.sumOf { it.nonCritTotal }, r.teamNonCrit, 1e-9)
        assertEquals(r.members.sumOf { it.critTotal }, r.teamCrit, 1e-9)
    }

    /*
    * 期望值必须落在非暴击与暴击之间(三者口径一致性,队伍级也要成立)
    * */
    @Test
    fun `team expected lies between noncrit and crit`() {
        val team = listOf(
            TeamMember(1, "A", 5, ayakaPanel(), listOf(MemberAction("x", 1.5, count = 2)))
        )
        val r = TeamDamageCalculator.calculate(team)
        assertTrue(r.teamExpected >= r.teamNonCrit)
        assertTrue(r.teamExpected <= r.teamCrit)
    }

    /*
    * 🔴 剧变反应必须被**跳过并记名**,绝不能按 1.0 静默算进去
    * */
    @Test
    fun `unsupported reaction is skipped and recorded`() {
        val team = listOf(
            TeamMember(
                1, "雷角色", 4, ayakaPanel(),
                listOf(
                    MemberAction("普通伤害", 1.0),
                    MemberAction("超载伤害", 2.0, unsupportedReaction = "超载")
                )
            )
        )
        val r = TeamDamageCalculator.calculate(team)

        // 只算了普通伤害那一条
        assertEquals(1, r.members.first().perAction.size)
        assertEquals("普通伤害", r.members.first().perAction.first().label)

        // 被跳过的必须记名(UI 要展示)
        assertEquals(1, r.skippedReactions.size)
        assertEquals("超载", r.skippedReactions.first().reactionName)
        assertEquals("雷角色", r.skippedReactions.first().memberName)
        assertEquals("超载伤害", r.skippedReactions.first().actionLabel)
    }

    /*
    * 面板不可用的成员必须被**记名跳过**,而不是静默丢弃
    * (否则用户看到少了一个人的伤害却不知道原因)
    * */
    @Test
    fun `unusable member is skipped and named`() {
        val brokenPanel = AvatarPanel(
            attack = null, critRate = null, critDamage = null,
            elementMastery = null, bonusByPropertyType = emptyMap()
        )
        val team = listOf(
            TeamMember(1, "正常角色", 5, ayakaPanel(), listOf(MemberAction("x", 1.0))),
            TeamMember(2, "面板缺失角色", 5, brokenPanel, listOf(MemberAction("y", 1.0)))
        )
        val r = TeamDamageCalculator.calculate(team)

        assertEquals("只有 1 人参与计算", 1, r.members.size)
        assertEquals("正常角色", r.members.first().name)
        assertEquals(listOf("面板缺失角色"), r.skippedMembers)
    }

    /*
    * 单人也要能算(不强制 4 人)
    * */
    @Test
    fun `single member team works`() {
        val r = TeamDamageCalculator.calculate(
            listOf(TeamMember(1, "独狼", 5, ayakaPanel(), listOf(MemberAction("x", 1.0))))
        )
        assertEquals(1, r.members.size)
        assertTrue(r.teamExpected > 0)
    }

    /*
    * 空队伍不应抛异常,合计为 0
    * */
    @Test
    fun `empty team yields zero without crashing`() {
        val r = TeamDamageCalculator.calculate(emptyList())
        assertTrue(r.members.isEmpty())
        assertEquals(0.0, r.teamExpected, 1e-9)
    }

    /*
    * 增幅反应倍率必须真的生效(蒸发 2.0 应使伤害翻倍)
    * */
    @Test
    fun `amplify reaction doubles damage`() {
        fun run(reaction: ReactionType) = TeamDamageCalculator.calculate(
            listOf(TeamMember(1, "A", 2, ayakaPanel(), listOf(MemberAction("x", 1.0, reaction = reaction))))
        ).teamNonCrit

        val none = run(ReactionType.NONE)
        val vaporize = run(ReactionType.VAPORIZE_WATER_ON_FIRE)
        assertEquals(none * 2.0, vaporize, 1e-6)
    }

    /*
    * 增伤必须真的生效(动作级 damageBonus)
    * */
    @Test
    fun `damage bonus is applied`() {
        fun run(bonus: Double) = TeamDamageCalculator.calculate(
            listOf(TeamMember(1, "A", 5, ayakaPanel(), listOf(MemberAction("x", 1.0, damageBonus = bonus))))
        ).teamNonCrit

        val zero = run(0.0)
        val plus50 = run(0.5)
        assertEquals(zero * 1.5, plus50, 1e-6)
    }

    /*
    * 防御区:攻击者等级影响结果(等级 90 打 90 与 80 打 90 应不同)
    * */
    @Test
    fun `attacker level affects damage through defense factor`() {
        val team = listOf(TeamMember(1, "A", 5, ayakaPanel(), listOf(MemberAction("x", 1.0))))
        val lv90 = TeamDamageCalculator.calculate(team, attackerLevel = 90, defenderLevel = 90).teamNonCrit
        val lv80 = TeamDamageCalculator.calculate(team, attackerLevel = 80, defenderLevel = 90).teamNonCrit
        assertTrue("高等级打同目标应伤害更高", lv90 > lv80)
    }

    // =========================================================
    // 三、DPS 换算(循环耗时必须由调用方提供)
    // =========================================================

    /*
    * 正常换算
    * */
    @Test
    fun `toDps divides by rotation seconds`() {
        assertEquals(1000.0, TeamDamageCalculator.toDps(20000.0, 20.0)!!, 1e-9)
    }

    /*
    * 🔴 耗时非法必须返回 null,不能返回 0 ——
    * 0 会被当成"真实算出的 0 DPS",掩盖"没提供耗时"这一事实
    * */
    @Test
    fun `toDps returns null for invalid duration`() {
        assertNull(TeamDamageCalculator.toDps(1000.0, 0.0))
        assertNull(TeamDamageCalculator.toDps(1000.0, -5.0))
        assertNull(TeamDamageCalculator.toDps(1000.0, Double.NaN))
        assertNull(TeamDamageCalculator.toDps(1000.0, Double.POSITIVE_INFINITY))
    }

    /*
    * 明细字段应完整保留(UI 要展示"这个数怎么来的")
    * */
    @Test
    fun `action detail preserves inputs`() {
        val d = ActionDamage(label = "L", multiplier = 1.5, count = 3, nonCrit = 1.0, crit = 2.0, expected = 1.5)
        assertEquals("L", d.label)
        assertEquals(1.5, d.multiplier, 1e-9)
        assertEquals(3, d.count)
    }
}
