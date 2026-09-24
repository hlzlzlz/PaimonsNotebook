package com.lianyi.paimonsnotebook

import com.lianyi.paimonsnotebook.common.util.damage.ScalingEntry
import com.lianyi.paimonsnotebook.common.util.damage.SkillScalingParser
import com.lianyi.paimonsnotebook.common.util.damage.SkillSlot
import com.lianyi.paimonsnotebook.common.util.damage.SkillSlotResolver
import com.lianyi.paimonsnotebook.common.util.json.JSON
import com.lianyi.paimonsnotebook.common.web.hutao.genshin.avatar.AvatarData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/*
* 技能槽位判别 + 出伤倍率项选择 —— 真实元数据驱动测试
*
* ⚠️ 为什么必须新建这个文件(而不是补进 TeamDamageCalculatorTest):
*
* 2026-09-23 对 `buildActions()` 做了 **118 个角色全量实测审计**,查出两处
* **编译通过、单测全绿、UI 上看不出**的静默算错(详见 memory/dps-calculator.md §12):
*
*   ① 槽位靠下标猜(`Skills[0]`/`Skills[1]`)⇒ **26/118 角色**动作错误或缺失
*   ② 倍率硬取参数下标 0 ⇒ **19 条非伤害项**被当倍率(含**负倍率**与 1172 这种量级)
*
* ⚠️⚠️ **原有两个夹具(`AvatarMeta_10000003.json` 琴、`CharacterDetail_*`)对上述
* 两处缺陷**零检出力**:它们全部满足"`Skills.length == 2` ∧ 三槽 `Descriptions[0]`
* 都是 param1 ∧ 都是伤害标签"—— 26 个受损角色一个都不在里面。
* **这与 1.8.20 事故同源**:夹具选得太"正常",于是缺陷全部漏过。
*
* ⇒ 本文件的夹具**专门挑选受损角色**(欧洛伦/茜特菈莉/凝光/米卡/莉奈娅/兹白/七七/阿蕾奇诺),
*    且**全部是 `Snap.Metadata` 原样复制、未经加工**的真实文件。
* */

class SkillSlotResolverTest {

    private val FLOAT_TOL = 1e-6

    /** 与既有测试同款的夹具定位方式(兼容从 app/ 或仓库根运行) */
    private fun fixture(name: String): File {
        val testDir = sequenceOf(
            File("src/test/java/com/lianyi/paimonsnotebook"),
            File("app/src/test/java/com/lianyi/paimonsnotebook")
        ).firstOrNull { it.isDirectory }
            ?: File(javaClass.protectionDomain.codeSource.location.toURI()).parentFile
        return File(testDir, name)
    }

    private fun meta(id: String): AvatarData =
        JSON.parse<AvatarData>(fixture("AvatarMeta_$id.json").readText())

    /** 三个技能等级都按 1 计(真实元数据 Parameters 含 Level=1) */
    private fun levelsOf(meta: AvatarData, level: Int = 1): Map<Int, Int> =
        (meta.skillDepot.Skills.map { it.Id } + meta.skillDepot.EnergySkill.Id)
            .associateWith { level }

    // =========================================================
    // 一、槽位判别:过滤式规则(缺陷一)
    // =========================================================

    /*
    * 🔴 本组用例钉住"槽位必须靠**过滤**而不是靠下标"。
    *
    * 欧洛伦 / 茜特菈莉 的 `Skills[0]` 是**参数全空的伪技能「特殊跳跃」**
    * (`GroupId=0`、`Descriptions` 与 `Parameters` 均为空数组),
    * 若照旧按下标取:`Skills[0]`=伪技能(取不到倍率,普攻**静默丢失**)、
    * `Skills[1]`=实际是普攻却被当成战技、真战技在 `Skills[2]` **从未被读**。
    * */
    @Test
    fun `ororon pseudo-skill is filtered out of calculable slots`() {
        val m = meta("10000105") // 欧洛伦
        assertEquals("原始 Skills 应为 3 条", 3, m.skillDepot.Skills.size)

        val kept = SkillSlotResolver.calculableSkills(m.skillDepot)
        assertEquals("过滤后应恰好剩 2 个可计算技能", 2, kept.size)

        // 过滤掉的那条必须是参数全空的伪技能
        val pseudo = m.skillDepot.Skills.first { it.Proud.Parameters.isEmpty() }
        assertTrue("伪技能名应含'特殊跳跃'", pseudo.Name.contains("特殊跳跃"))
        assertFalse("伪技能不得进入可计算槽位", kept.any { it.Id == pseudo.Id })

        // 下标 0 的**真实**普攻是「宿灵闪箭」,不是伪技能
        assertEquals("宿灵闪箭", kept[0].Name)
        assertEquals("暝色缒索", kept[1].Name)
    }

    @Test
    fun `citlali pseudo-skill is filtered out of calculable slots`() {
        val m = meta("10000107") // 茜特菈莉
        assertEquals(3, m.skillDepot.Skills.size)

        val kept = SkillSlotResolver.calculableSkills(m.skillDepot)
        assertEquals(2, kept.size)
        assertEquals("宿灵捕影", kept[0].Name)
        assertEquals("霜昼黑星", kept[1].Name)
    }

    /*
    * 🔴 反向钉:过滤规则对**全部**夹具角色都应恰好得到 2 个。
    * 实测 118/118 角色成立,这里是它的一个子集回归。
    * */
    @Test
    fun `filter rule yields exactly two skills for audited avatars`() {
        listOf(
            "10000105", "10000107", "10000027", "10000080",
            "10000130", "10000126", "10000035", "10000096", "10000003"
        ).forEach { id ->
            val m = meta(id)
            assertEquals(
                "${m.name}($id) 过滤后应恰好 2 个可计算技能",
                2, SkillSlotResolver.calculableSkills(m.skillDepot).size
            )
        }
    }

    /*
    * 🔴 欧洛伦的**真战技**「暝色缒索」必须真的被读出来 ——
    * 它原先在 `Skills[2]`,旧实现从未读过(这是"缺失"而非"错值")。
    * */
    @Test
    fun `ororon real elemental skill is actually read`() {
        val m = meta("10000105")
        val r = SkillSlotResolver.resolve(m.skillDepot, levelsOf(m))

        val e = r.actions.firstOrNull { it.slot == SkillSlot.ELEMENTAL_SKILL }
        assertNotNull("战技必须被解析出来(旧实现在 Skills[2] 从未读)", e)
        assertEquals("暝色缒索", e!!.skillName)
        assertEquals("宿灵球伤害", e.label)
        assertEquals("欧洛伦战技 Lv1 倍率", 1.976, e.multiplier, FLOAT_TOL)
    }

    // =========================================================
    // 二、倍率项选择:不得把非伤害项当倍率(缺陷二)
    // =========================================================

    /*
    * 🔴🔴 凝光战技:旧实现取到 **-0.499(负倍率)**。
    *
    * 真实数据:
    *   Descriptions = ["继承生命|{param3:F1P}", "技能伤害|{param2:P}", "冷却时间|{param4:F1}秒"]
    *   Lv1 params   = [-0.499, 2.304, 0.501, 12]
    * ⇒ 下标 0 是 -0.499(param3 是"继承生命"的百分比),而正确倍率"技能伤害"= param2 = 2.304。
    * **负倍率会算出负伤害** —— 这是最恶劣的一类。
    * */
    @Test
    fun `ningguang skill never yields negative multiplier`() {
        val m = meta("10000027") // 凝光
        val r = SkillSlotResolver.resolve(m.skillDepot, levelsOf(m))

        r.actions.forEach {
            assertTrue("${it.skillName}/${it.label} 倍率不得为负:${it.multiplier}", it.multiplier > 0)
        }

        val e = r.actions.first { it.slot == SkillSlot.ELEMENTAL_SKILL }
        assertEquals("技能伤害", e.label)
        assertEquals("凝光战技 Lv1 应为 2.304(param2),而非 -0.499(param3)", 2.304, e.multiplier, FLOAT_TOL)
    }

    /*
    * 🔴🔴 米卡爆发:旧实现取到 **1172.0355**(那是"施放治疗量"的固定值,不是倍率)。
    *
    * 真实数据:米卡 Q 只有 `施放治疗量`/`鹰翎治疗量`/`鹰翎治疗间隔`/`持续时间`/`冷却`/`元素能量`
    * ⇒ **整条爆发没有任何伤害倍率**。正确行为是**记名跳过**,而不是编一个数。
    * */
    @Test
    fun `mika burst is skipped instead of using healing value`() {
        val m = meta("10000080") // 米卡
        val r = SkillSlotResolver.resolve(m.skillDepot, levelsOf(m))

        assertFalse(
            "米卡爆发不得产出任何动作(旧实现会取到治疗量 1172.0355)",
            r.actions.any { it.slot == SkillSlot.ELEMENTAL_BURST }
        )
        // 而且必须**记名**,不能静默消失
        val skipped = r.skipped.firstOrNull { it.slot == SkillSlot.ELEMENTAL_BURST }
        assertNotNull("米卡爆发必须记名跳过", skipped)
        assertEquals("苍翎的颂愿", skipped!!.skillName)

        // 米卡的普攻/战技仍应正常产出
        assertEquals("普攻与战技应各产出 1 条", 2, r.actions.size)
    }

    @Test
    fun `liney burst is skipped instead of using healing value`() {
        val m = meta("10000130") // 莉奈娅
        val r = SkillSlotResolver.resolve(m.skillDepot, levelsOf(m))

        assertFalse("莉奈娅爆发不得产出动作(旧实现取到 770.3755)", r.actions.any { it.slot == SkillSlot.ELEMENTAL_BURST })
        assertNotNull(r.skipped.firstOrNull { it.slot == SkillSlot.ELEMENTAL_BURST })
        assertEquals(2, r.actions.size)
    }

    /*
    * 🔴 兹白战技:`{param6}` 与下标 0 差 **3.05 倍**。
    * 真实数据 desc[0] = `月转时隙一段伤害|{param6:F1P}` ⇒ 正确倍率 = params[5] = 0.565792
    * (旧实现取 params[0] = 1.72528)。
    * */
    @Test
    fun `zibai skill uses paramN not array index`() {
        val m = meta("10000126") // 兹白
        val r = SkillSlotResolver.resolve(m.skillDepot, levelsOf(m))

        val e = r.actions.first { it.slot == SkillSlot.ELEMENTAL_SKILL }
        assertEquals("月转时隙一段伤害", e.label)
        assertEquals("兹白战技应按 {param6} 取 0.565792", 0.565792, e.multiplier, FLOAT_TOL)
        assertFalse("不得取到下标 0 的 1.72528", kotlin.math.abs(e.multiplier - 1.72528) < 1e-4)
    }

    /*
    * 🔴 七七战技:`{param8}` 与下标 0 差约 **9 倍**(旧实现偏低,等于把伤害算小)。
    * */
    @Test
    fun `qiqi skill uses paramN not array index`() {
        val m = meta("10000035") // 七七
        val r = SkillSlotResolver.resolve(m.skillDepot, levelsOf(m))

        val e = r.actions.first { it.slot == SkillSlot.ELEMENTAL_SKILL }
        assertEquals("仙法·寒病鬼差", e.skillName)
        assertEquals("七七战技应按 paramN 取值", 0.96, e.multiplier, FLOAT_TOL)
    }

    /*
    * 🔴 全量护栏:**任何**夹具角色解析出的倍率都必须是有限正数。
    * 这一条是对"取错项"最直接的兜底(旧实现会产出 -0.499 与 1172)。
    * */
    @Test
    fun `all audited avatars produce only finite positive multipliers`() {
        listOf(
            "10000105", "10000107", "10000027", "10000080",
            "10000130", "10000126", "10000035", "10000096", "10000003"
        ).forEach { id ->
            val m = meta(id)
            val r = SkillSlotResolver.resolve(m.skillDepot, levelsOf(m))
            assertTrue("${m.name} 应至少产出 1 个动作", r.actions.isNotEmpty())
            r.actions.forEach {
                assertTrue(
                    "${m.name} ${it.slot.displayName}「${it.skillName}」${it.label} 倍率非法:${it.multiplier}",
                    it.multiplier.isFinite() && it.multiplier > 0.0
                )
                // 实测全量 118 角色里，真实倍率不超过 ~10；超过 50 必然是取到了非倍率项
                assertTrue(
                    "${m.name}「${it.skillName}」${it.label} 倍率 ${it.multiplier} 量级异常(疑似取到非伤害项)",
                    it.multiplier < 50.0
                )
            }
        }
    }

    /*
    * 🔴 标签必须是**元数据原文** —— 这是用户发现"选错项"的唯一途径。
    * 旧实现自造 `"${name} · 技能伤害"`,于是"米卡 · 技能伤害 1172"在 UI 上完全看不出错。
    * */
    @Test
    fun `label comes from metadata not fabricated`() {
        val m = meta("10000027") // 凝光
        val r = SkillSlotResolver.resolve(m.skillDepot, levelsOf(m))
        val labels = r.actions.map { it.label }.toSet()

        // 这些是元数据里真实存在的标签原文
        assertTrue("应出现真实的『技能伤害』标签", labels.contains("技能伤害") || labels.contains("一段伤害"))
        // 绝不能出现旧实现那种自造后缀
        labels.forEach {
            assertFalse("标签不得含自造的『· 技能伤害』: $it", it.contains("· 技能伤害"))
        }
    }

    // =========================================================
    // 三、isDamageEntry 的边界
    // =========================================================

    private fun entry(label: String, value: Double?): ScalingEntry =
        ScalingEntry(label = label, multiplier = value, paramIndex = 1)

    @Test
    fun `non-damage labels are rejected`() {
        listOf(
            "继承生命", "施放治疗量", "首次治疗量", "治疗量", "护盾吸收量",
            "持续时间", "冷却时间", "元素能量", "技能消耗", "攻击力提高",
            "普通攻击/重击/下落攻击伤害提升", "伤害值提升", "伤害加成",
            "绽放、超绽放、烈绽放反应伤害提升", "生命流失"
        ).forEach {
            assertFalse("『$it』不应被判为伤害项", SkillSlotResolver.isDamageEntry(entry(it, 1.0)))
        }
    }

    @Test
    fun `real damage labels are accepted`() {
        listOf("一段伤害", "技能伤害", "爆发伤害", "点按伤害", "持续伤害", "泡影破裂伤害")
            .forEach {
                assertTrue("『$it』应被判为伤害项", SkillSlotResolver.isDamageEntry(entry(it, 1.0)))
            }
    }

    @Test
    fun `non-positive or missing values are rejected`() {
        assertFalse("负值必须拒绝(凝光旧值为 -0.499)", SkillSlotResolver.isDamageEntry(entry("技能伤害", -0.499)))
        assertFalse("零值必须拒绝", SkillSlotResolver.isDamageEntry(entry("技能伤害", 0.0)))
        assertFalse("取不到值必须拒绝", SkillSlotResolver.isDamageEntry(entry("技能伤害", null)))
    }

    /*
    * ⚠️ 反向钉:**筛不出伤害项时返回 null,绝不退回 index 0**。
    * 退回 index 0 正是缺陷二本身。
    * */
    @Test
    fun `pickPrimaryDamageEntry returns null rather than falling back to index zero`() {
        val noDamage = listOf(
            entry("施放治疗量", 1172.0355),
            entry("鹰翎治疗量", 233.95428),
            entry("冷却时间", 15.0),
            entry("元素能量", 70.0)
        )
        assertNull(
            "全是非伤害项时必须返回 null(不得退回 index 0 的 1172.0355)",
            SkillSlotResolver.pickPrimaryDamageEntry(noDamage)
        )
        assertNull("空列表返回 null", SkillSlotResolver.pickPrimaryDamageEntry(emptyList()))
    }

    @Test
    fun `pickPrimaryDamageEntry returns the first accepted entry`() {
        val entries = listOf(
            entry("继承生命", 0.501),
            entry("技能伤害", 2.304),
            entry("冷却时间", 12.0)
        )
        val picked = SkillSlotResolver.pickPrimaryDamageEntry(entries)
        assertNotNull(picked)
        assertEquals("技能伤害", picked!!.label)
        assertEquals(2.304, picked.multiplier!!, FLOAT_TOL)
    }

    // =========================================================
    // 四、缺等级 / 缺数据时必须**记名跳过**,不得静默
    // =========================================================

    @Test
    fun `missing skill level is recorded as skipped not silently dropped`() {
        val m = meta("10000027") // 凝光
        // 只给普攻等级,战技/爆发都不给
        val onlyNormal = mapOf(m.skillDepot.Skills[0].Id to 1)
        val r = SkillSlotResolver.resolve(m.skillDepot, onlyNormal)

        assertEquals("只有普攻能算", 1, r.actions.size)
        assertEquals("另两槽必须记名跳过", 2, r.skipped.size)
        assertEquals(
            setOf(SkillSlot.ELEMENTAL_SKILL, SkillSlot.ELEMENTAL_BURST),
            r.skipped.map { it.slot }.toSet()
        )
    }

    @Test
    fun `empty level map skips everything but still records all three slots`() {
        val m = meta("10000027")
        val r = SkillSlotResolver.resolve(m.skillDepot, emptyMap())
        assertTrue("无等级时不得产出动作", r.actions.isEmpty())
        assertEquals("三个槽位都要记名", 3, r.skipped.size)
    }

    /*
    * 🔴 阿蕾奇诺:战技有 `{paramN}+{paramN}` 形式的多段模板,
    * 必须能正常解析(不因模板复杂而取不到值)。
    * */
    @Test
    fun `arlecchino skill resolves despite multi-param template`() {
        val m = meta("10000096") // 阿蕾奇诺
        val r = SkillSlotResolver.resolve(m.skillDepot, levelsOf(m))
        assertTrue("阿蕾奇诺应产出动作", r.actions.isNotEmpty())
        r.actions.forEach {
            assertTrue("倍率应为正:${it.multiplier}", it.multiplier > 0.0)
        }
    }

    /*
    * ⚠️ 反向钉:确认 `SkillScalingParser.labeledMultipliers` 的 `{paramN}` 取值语义。
    * 若有人把 paramN 改成数组下标,本用例会失败。
    * */
    @Test
    fun `labeledMultipliers maps paramN one-based`() {
        val m = meta("10000027") // 凝光战技
        val e = SkillSlotResolver.calculableSkills(m.skillDepot)[1]
        assertEquals("璇玑屏", e.Name)

        val entries = SkillScalingParser.labeledMultipliers(e.Proud.Descriptions, e.Proud, 1)
        val inherit = entries.first { it.label == "继承生命" }
        val damage = entries.first { it.label == "技能伤害" }

        assertEquals("{param3:F1P} ⇒ paramIndex 应为 3", 3, inherit.paramIndex)
        assertEquals("{param2:P} ⇒ paramIndex 应为 2", 2, damage.paramIndex)
        // 值必须按 paramIndex-1 取,而不是按数组下标
        assertEquals(0.501, inherit.multiplier!!, FLOAT_TOL)
        assertEquals(2.304, damage.multiplier!!, FLOAT_TOL)
    }
}
