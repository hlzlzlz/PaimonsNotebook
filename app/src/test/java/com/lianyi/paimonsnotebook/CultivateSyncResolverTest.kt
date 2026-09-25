package com.lianyi.paimonsnotebook

import com.lianyi.paimonsnotebook.common.data.ResultData
import com.lianyi.paimonsnotebook.common.util.cultivation.CultivateSyncResolver
import com.lianyi.paimonsnotebook.common.util.json.JSON
import com.lianyi.paimonsnotebook.common.util.parameter.getParameterizedType
import com.lianyi.paimonsnotebook.common.web.hutao.genshin.avatar.AvatarData
import com.lianyi.paimonsnotebook.common.web.hoyolab.takumi.game_record.character.CharacterDetailData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/*
* 养成计划「从米游社同步角色等级与天赋」的映射层
*
* ⚠️ 本测试**必须喂真实夹具**,不能自造数据 —— 本文件要钉住的正是
*    "两套技能 id 体系不同"这个**只看名字绝对看不出来**的坑:
*
*      游戏记录 skills[].skill_id = 10031 / 10033 / 10034
*      元数据 SkillDepot.Id      = 10031 / 10033 / 10034   ← 一致
*      元数据 SkillDepot.GroupId = 331 / 332 / 339         ← 养成计划用的是这个
*
*    自造数据(随手写 1/2/3)会让"直接拿 skill_id 当 GroupId"这个错误实现
*    照样通过 —— 那正是 1.8.20 DPS 事故的成因(见 memory/dps-calculator.md)。
*
* 夹具:CharacterDetail_10000003.json(琴,真实接口响应)
*       AvatarMeta_10000003.json(琴,真实元数据)
* */
class CultivateSyncResolverTest {

    //琴(10000003)的真实元数据
    private fun loadAvatar(): AvatarData =
        JSON.parse<AvatarData>(fixture("AvatarMeta_10000003.json").readText())

    //琴的真实角色详情响应
    private fun loadDetail(): CharacterDetailData.DetailItem {
        val raw = fixture("CharacterDetail_10000003.json").readText()
        //走真实信封解析(与项目既有做法一致,避免泛型擦除)
        val type = getParameterizedType(
            ResultData::class.java,
            CharacterDetailData::class.java
        )
        val envelope: ResultData<CharacterDetailData> = JSON.parse(raw, type)
        return envelope.data.list.first()
    }

    //与项目既有测试一致:从工作目录定位夹具目录
    private fun fixture(name: String): File {
        val testDir = sequenceOf(
            File("src/test/java/com/lianyi/paimonsnotebook"),
            File("app/src/test/java/com/lianyi/paimonsnotebook")
        ).firstOrNull { it.isDirectory }!!

        return File(testDir, name)
    }

    /*
    * 🔴 核心用例:技能 id 必须从接口的 skill_id 换成元数据的 GroupId
    *
    * 若实现直接拿 skill_id 当养成计划 id,断言会得到 10031/10033/10034,
    * 与本用例期望的 331/332/339 不符 —— 即被检出。
    * */
    @Test
    fun `技能id必须换算为元数据GroupId而不是直接用接口skill_id`() {
        val result = CultivateSyncResolver.buildAvatarSyncInfo(
            avatar = loadAvatar(),
            detail = loadDetail(),
            targetAvatarLevel = 90,
            targetSkillLevels = mapOf(331 to 9, 332 to 9, 339 to 9)
        )

        assertNotNull("元数据与真实详情应能匹配成功", result)

        val groupIds = result!!.skills.map { it.groupId }.sorted()
        //琴:普攻331 战技332 爆发339
        assertEquals(listOf(331, 332, 339), groupIds)

        //反向钉:绝不能出现接口的 skill_id
        assertTrue(
            "技能 id 不应包含接口的 skill_id(10031/10033/10034):${result.skills.map { it.groupId }}",
            result.skills.none { it.groupId in listOf(10031, 10033, 10034) }
        )
    }

    @Test
    fun `等级取自接口真实值而非元数据推算`() {
        val result = CultivateSyncResolver.buildAvatarSyncInfo(
            avatar = loadAvatar(),
            detail = loadDetail(),
            targetAvatarLevel = 90,
            targetSkillLevels = mapOf(331 to 9, 332 to 9, 339 to 9)
        )!!

        //真实夹具里琴是 20 级、三天赋各 1 级
        assertEquals(20, result.avatarLevelCurrent)
        assertEquals(90, result.avatarLevelTarget)
        assertTrue("技能当前等级应为接口返回的 1", result.skills.all { it.levelCurrent == 1 })
        assertTrue("技能目标等级应为传入的 9", result.skills.all { it.levelTarget == 9 })
    }

    /*
    * 被动天赋(skill_type == 2)必须被滤掉。
    * 真实数据里琴带 3 个被动(321/322/323),它们不可升级、不进养成计划。
    * */
    @Test
    fun `被动天赋不参与同步`() {
        val detail = loadDetail()

        //先确认夹具里确实有被动,否则本用例无意义
        assertTrue(
            "夹具应包含 skill_type=2 的被动天赋",
            detail.skills.any { it.skill_type == 2 }
        )

        val result = CultivateSyncResolver.buildAvatarSyncInfo(
            loadAvatar(), detail, 90, mapOf(331 to 9, 332 to 9, 339 to 9)
        )!!

        //只应有 3 个可升级技能
        assertEquals(3, result.skills.size)
        //且不含被动天赋的 id(321/322/323)
        assertTrue(result.skills.none { it.groupId in listOf(321, 322, 323) })
    }

    @Test
    fun `当前等级高于目标时夹到目标避免负数材料`() {
        //把目标设成比实际(20级/1级)更低,验证 min 生效
        val result = CultivateSyncResolver.buildAvatarSyncInfo(
            avatar = loadAvatar(),
            detail = loadDetail(),
            targetAvatarLevel = 10,
            targetSkillLevels = mapOf(331 to 1, 332 to 1, 339 to 1)
        )!!

        assertEquals("当前等级应被夹到目标 10 而不是真实的 20", 10, result.avatarLevelCurrent)
        assertTrue("技能当前等级不应超过目标", result.skills.all { it.levelCurrent <= it.levelTarget })
    }

    /*
    * 养成计划里没设过该技能(缺失目标等级)时整体放弃,而不是给个默认值 ——
    * 给默认值等于编造目标,会算出用户没要的材料。
    * */
    @Test
    fun `缺少某个技能目标等级时整体放弃`() {
        val result = CultivateSyncResolver.buildAvatarSyncInfo(
            avatar = loadAvatar(),
            detail = loadDetail(),
            targetAvatarLevel = 90,
            //故意少给一个(缺爆发 339)
            targetSkillLevels = mapOf(331 to 9, 332 to 9)
        )

        assertNull("任一技能取不到目标等级时应返回 null,不做部分同步", result)
    }

    //
    // 反向验证记录(2026-09-25 实测):
    //   把 buildAvatarSyncInfo 里的 `val groupId = skill.GroupId` 改成
    //   `val groupId = detailSkill.skill_id` 后重跑 ⇒ **5 个用例里 4 个 FAILED**
    //   (`技能id必须换算为...` / `等级取自接口真实值` / `被动天赋不参与同步` /
    //    `当前等级高于目标时夹到目标`)。
    //   剩下的 `缺少某个技能目标等级时整体放弃` 仍绿 —— 因为它断言的是"缺键即放弃",
    //   与 id 取哪个无关,属**预期内不敏感**,不是漏检。
    //   改回即全绿 ⇒ 测试确实钉住了这个映射。
    //
}
