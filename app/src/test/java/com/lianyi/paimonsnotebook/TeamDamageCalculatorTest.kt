package com.lianyi.paimonsnotebook

import com.lianyi.paimonsnotebook.common.util.damage.ActionDamage
import com.lianyi.paimonsnotebook.common.util.damage.AvatarPanel
import com.lianyi.paimonsnotebook.common.util.damage.MemberAction
import com.lianyi.paimonsnotebook.common.util.damage.PanelAdapter
import com.lianyi.paimonsnotebook.common.util.damage.ReactionType
import com.lianyi.paimonsnotebook.common.util.damage.SkillScalingParser
import com.lianyi.paimonsnotebook.common.util.damage.TeamDamageCalculator
import com.lianyi.paimonsnotebook.common.util.damage.TeamMember
import com.lianyi.paimonsnotebook.common.util.json.JSON
import com.lianyi.paimonsnotebook.common.util.parameter.getParameterizedType
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
*   - 面板部分:**2026-09-23 起已用真实响应驱动**。
*     此前本文件顶部写着"接口需真实凭证、本机无法实测",并因此用**自造样例**断言 ——
*     结果 1.8.20 发布后**所有成员都被判为未参与计算**:真实 `selected_properties`
*     只有 2000 系列(当前生命/当前攻击/当前防御),而我查的是 `FIGHT_PROP_ATTACK`(5),
*     取不到 ⇒ 攻击力 null ⇒ `isUsable` false ⇒ 全员跳过。
*     这正是本项目记载过的事故模式:"自造数据对字段类型写错零检出力"。
*
*   ⇒ 现改为喂**真实响应字节** `CharacterDetail_{10000003,10000031,10000148}.json`
*     (由真实凭证从 `character/detail` 拉取后原样落盘)。
*     下面的 `prop(...)` 自造样例**仅保留用于边界用例**(空串、千分位等),
*     凡涉及"真实字段类型/真实语义"的断言一律用真实夹具。
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

    // =========================================================
    // 四、真实响应驱动(**1.8.20 全员被跳过的回归钉**)
    //
    // 这些用例**必须**喂真实 `character/detail` 响应字节。
    // 自造数据对本节要防的 bug 零检出力(详见文件头注释)。
    // =========================================================

    /** 解析真实响应(走真实调用链:ResultData 信封 + 泛型参数化类型) */
    private fun loadRealDetail(fileName: String): CharacterDetailData.DetailItem {
        val raw = fixture(fileName).readText()
        // ⚠️ 必须走参数化类型:直接 JSON.parse<CharacterDetailData>(raw) 会把
        //    整体当 T 解析(得到默认值),而 parse<ResultData<...>> 会因泛型擦除
        //    把 data 解析成 LinkedTreeMap ⇒ 两种错法都"看起来在跑"却零检出力。
        val type = getParameterizedType(
            com.lianyi.paimonsnotebook.common.data.ResultData::class.java,
            CharacterDetailData::class.java
        )
        val envelope: com.lianyi.paimonsnotebook.common.data.ResultData<CharacterDetailData> =
            JSON.parse(raw, type)
        return envelope.data.list.first()
    }

    /*
    * 🔴🔴 本用例直接钉住 1.8.20 的线上缺陷:
    * 真实 `selected_properties` 用 **2001**(当前攻击力)而非 5。
    * 若 PanelAdapter 只查 5,攻击力会是 null、`isUsable` 为 false,
    * **所有成员被静默跳过**(用户实测:"我不管选谁都是未参与计算")。
    * */
    @Test
    fun `real response attack uses current-attack property type`() {
        val detail = loadRealDetail("CharacterDetail_10000003.json")

        // 先确认夹具本身确实是"2001 口径"——否则这个用例会失去意义
        val types = detail.selected_properties.map { it.property_type }.toSet()
        assertTrue("真实响应应含当前攻击力 2001(夹具失效?)", types.contains(FightProperty.FIGHT_PROP_CUR_ATTACK))
        assertFalse("真实响应不应含基础攻击力 5(夹具失效?)", types.contains(FightProperty.FIGHT_PROP_ATTACK))

        val panel = PanelAdapter.adapt(detail.selected_properties)
        assertNotNull("攻击力必须能取到 —— 否则全员被跳过", panel.attack)
        assertEquals("琴 Lv.20 的当前攻击力", 88.0, panel.attack!!, 1e-9)
        assertTrue("面板必须判定为可用", panel.isUsable)
    }

    /*
    * 🔴 真实面板必须能算出非零伤害(这是"未参与计算"最直接的护栏)
    * */
    @Test
    fun `real panel produces non-zero damage`() {
        val detail = loadRealDetail("CharacterDetail_10000031.json") // 菲谢尔
        val panel = PanelAdapter.adapt(
            properties = detail.selected_properties,
            bonusPropertyTypes = setOf(FightProperty.FIGHT_PROP_ELEC_ADD_HURT)
        )
        assertTrue("真实面板应可用", panel.isUsable)
        assertEquals("菲谢尔当前攻击力", 182.0, panel.attack!!, 1e-9)
        assertEquals("菲谢尔暴击率", 0.17, panel.critRate!!, 1e-9)
        assertEquals("菲谢尔暴击伤害", 0.556, panel.critDamage!!, 1e-9)

        val member = TeamMember(
            avatarId = detail.base.id, name = detail.base.name, element = 4,
            panel = panel, actions = listOf(MemberAction("测试", 1.0)), level = detail.base.level
        )
        val r = TeamDamageCalculator.calculate(listOf(member))
        assertEquals("不应有成员被跳过", 0, r.skippedMembers.size)
        assertTrue("伤害必须 > 0", r.teamExpected > 0)
    }

    /*
    * 🔴 三个真实角色的面板都必须可用(防止"只对某个角色有效"的假修复)
    * */
    @Test
    fun `all real characters have usable panels`() {
        listOf(
            "CharacterDetail_10000003.json",
            "CharacterDetail_10000031.json",
            "CharacterDetail_10000148.json"
        ).forEach { f ->
            val detail = loadRealDetail(f)
            val panel = PanelAdapter.adapt(detail.selected_properties)
            assertTrue("${detail.base.name} 的面板应可用", panel.isUsable)
            assertNotNull("${detail.base.name} 的攻击力不应为 null", panel.attack)
            assertNotNull("${detail.base.name} 的暴击率不应为 null", panel.critRate)
        }
    }

    /*
    * 🔴 技能等级必须能按 **skill_id ↔ 元数据 Id** 匹配上。
    *
    * 我原先按 skill_type 猜(1=普攻/2=战技/3=爆发),实测被推翻:
    * skill_type==1 同时含普攻/战技/爆发,==2 是被动天赋。
    * 本用例钉住"用 skill_id 匹配 Id"(照搬胡桃 SummaryAvatarFactory 的做法)。
    * */
    @Test
    fun `skill levels match metadata by skill id`() {
        // 琴:API skill_id 10031/10033/10034 ↔ 元数据 Skills[].Id 与 EnergySkill.Id
        val detail = loadRealDetail("CharacterDetail_10000003.json")
        // ⚠️ 用**夹具副本**而不是 D:/0000/Snap.Metadata/... 绝对路径 ——
        //    本项目曾因 `ExampleUnitTest` 硬编码作者机器路径导致测试恒红、
        //    并掩盖了全部新测试。夹具已复制为 AvatarMeta_10000003.json。
        val meta = JSON.parse<AvatarData>(fixture("AvatarMeta_10000003.json").readText())
        val levelBySkillId = detail.skills.associate { it.skill_id to it.level }
        val depot = meta.skillDepot

        val normal = depot.Skills[0]
        val skill = depot.Skills[1]
        val burst = depot.EnergySkill

        assertNotNull("普攻等级应能按 Id 取到(${normal.Name} id=${normal.Id})", levelBySkillId[normal.Id])
        assertNotNull("战技等级应能按 Id 取到(${skill.Name} id=${skill.Id})", levelBySkillId[skill.Id])
        assertNotNull("爆发等级应能按 Id 取到(${burst.Name} id=${burst.Id})", levelBySkillId[burst.Id])

        // 三个技能都能取到倍率 ⇒ buildActions 才可能产出动作
        assertNotNull(SkillScalingParser.multiplierAt(normal.Proud, levelBySkillId[normal.Id]!!, 0))
        assertNotNull(SkillScalingParser.multiplierAt(skill.Proud, levelBySkillId[skill.Id]!!, 0))
        assertNotNull(SkillScalingParser.multiplierAt(burst.Proud, levelBySkillId[burst.Id]!!, 0))
    }

    /*
    * ⚠️ 反向钉:GroupId **不能**用来匹配(实测 skill_id ∩ GroupId 恒为空集)。
    * 若后人"顺手"改成 GroupId,本用例会失败并解释原因。
    * */
    @Test
    fun `skill id does not match group id`() {
        val detail = loadRealDetail("CharacterDetail_10000003.json")
        val meta = JSON.parse<AvatarData>(fixture("AvatarMeta_10000003.json").readText())
        val apiIds = detail.skills.map { it.skill_id }.toSet()
        val groupIds = (meta.skillDepot.Skills.map { it.GroupId } + meta.skillDepot.EnergySkill.GroupId).toSet()
        assertTrue(
            "实测 API skill_id 与元数据 GroupId 无交集(应改用 Id):交集=${apiIds intersect groupIds}",
            (apiIds intersect groupIds).isEmpty()
        )
    }

    /*
    * 🔴 防御区必须按**成员各自等级**算:真实等级因人而异(菲谢尔 29 / 琴 20)。
    * 统一用一个等级会让低等级成员伤害虚高。
    * */
    @Test
    fun `defense factor uses per-member real level`() {
        val mk = { lv: Int ->
            TeamMember(
                avatarId = 1, name = "x", element = 4,
                panel = AvatarPanel(
                    attack = 1000.0, critRate = 0.0, critDamage = 0.0,
                    elementMastery = 0.0, bonusByPropertyType = emptyMap()
                ),
                actions = listOf(MemberAction("a", 1.0)),
                level = lv
            )
        }
        val low = TeamDamageCalculator.calculate(listOf(mk(20))).teamNonCrit
        val high = TeamDamageCalculator.calculate(listOf(mk(90))).teamNonCrit
        assertTrue("等级高的成员伤害应更高(说明用了成员自身等级)", high > low)

        // level=0 时才回退到 attackerLevel
        val fallback = TeamDamageCalculator.calculate(
            listOf(mk(0)), attackerLevel = 20
        ).teamNonCrit
        assertEquals("level=0 应回退到 attackerLevel", low, fallback, 1e-9)
    }

    /*
    * 真实角色等级应被读出(实测有值,不再是"一律假定 90")
    * */
    @Test
    fun `real character levels are read from response`() {
        val qin = loadRealDetail("CharacterDetail_10000003.json")
        assertEquals("琴的等级(真实响应)", 20, qin.base.level)
        val fischl = loadRealDetail("CharacterDetail_10000031.json")
        assertEquals("菲谢尔的等级(真实响应)", 29, fischl.base.level)
    }
}
