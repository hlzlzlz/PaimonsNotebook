package com.lianyi.paimonsnotebook

import com.lianyi.paimonsnotebook.common.database.cultivate.data.CultivateEntityType
import com.lianyi.paimonsnotebook.common.database.cultivate.data.CultivateItemType
import com.lianyi.paimonsnotebook.common.database.cultivate.entity.CultivateEntity
import com.lianyi.paimonsnotebook.common.database.cultivate.entity.CultivateItems
import com.lianyi.paimonsnotebook.common.util.cultivation.CultivateSyncPlanner
import com.lianyi.paimonsnotebook.common.util.json.JSON
import com.lianyi.paimonsnotebook.common.util.parameter.getParameterizedType
import com.lianyi.paimonsnotebook.common.data.ResultData
import com.lianyi.paimonsnotebook.common.web.hutao.genshin.avatar.AvatarData
import com.lianyi.paimonsnotebook.common.web.hoyolab.takumi.game_record.character.CharacterDetailData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/*
* 同步编排层:从"养成计划 + 元数据 + 接口详情"挑出可同步项
*
* 本层要防的是三类**静默出错**:
*   1. 把不该同步的(武器实体)也同步了
*   2. 计划里没设目标等级时"猜一个默认值"继续同步(等于编造用户目标)
*   3. 无法同步的项被**静默丢弃** —— 用户以为全同步了
*
* 用真实夹具(琴),因为本层依赖元数据里的 GroupId 换算。
* */
class CultivateSyncPlannerTest {

    private val qinId = 10000003

    private fun fixture(name: String): File {
        val testDir = sequenceOf(
            File("src/test/java/com/lianyi/paimonsnotebook"),
            File("app/src/test/java/com/lianyi/paimonsnotebook")
        ).firstOrNull { it.isDirectory }!!

        return File(testDir, name)
    }

    private fun loadAvatar(): AvatarData =
        JSON.parse<AvatarData>(fixture("AvatarMeta_10000003.json").readText())

    private fun loadDetail(): CharacterDetailData.DetailItem {
        val raw = fixture("CharacterDetail_10000003.json").readText()
        val type = getParameterizedType(
            ResultData::class.java,
            CharacterDetailData::class.java
        )
        val envelope: ResultData<CharacterDetailData> = JSON.parse(raw, type)
        return envelope.data.list.first()
    }

    //琴在计划里已设好目标:角色 90 级,三天赋各 9 级
    private fun qinEntity() = CultivateEntity(
        itemId = qinId,
        projectId = 1,
        type = CultivateEntityType.Avatar,
        status = 0
    )

    private fun qinItems(): List<CultivateItems> = listOf(
        CultivateItems(qinId, qinId, 1, CultivateItemType.Avatar, 20, 90, 0),
        //技能项存的是 GroupId(331/332/339),不是接口的 skill_id
        CultivateItems(331, qinId, 1, CultivateItemType.Skill, 1, 9, 0),
        CultivateItems(332, qinId, 1, CultivateItemType.Skill, 1, 9, 0),
        CultivateItems(339, qinId, 1, CultivateItemType.Skill, 1, 9, 0)
    )

    @Test
    fun `计划里配置完整的角色可被同步`() {
        val result = CultivateSyncPlanner.plan(
            entityItems = mapOf(qinEntity() to qinItems()),
            avatarList = listOf(loadAvatar()),
            detailsByAvatarId = mapOf(qinId to loadDetail())
        )

        assertEquals(1, result.candidates.size)
        assertTrue("不应有跳过项:${result.skipped}", result.skipped.isEmpty())

        val info = result.candidates.first().syncInfo
        assertEquals(qinId, info.avatarId)
        assertEquals(20, info.avatarLevelCurrent)
        assertEquals(90, info.avatarLevelTarget)
        assertEquals(listOf(331, 332, 339), info.skills.map { it.groupId }.sorted())
    }

    /*
    * 武器实体的 itemId 可能恰好与某个技能 GroupId 相同,若不按类型过滤会误同步。
    * */
    @Test
    fun `武器实体不参与同步`() {
        val weaponEntity = CultivateEntity(
            itemId = 331,
            projectId = 1,
            type = CultivateEntityType.Weapon,
            status = 0
        )

        val result = CultivateSyncPlanner.plan(
            entityItems = mapOf(weaponEntity to qinItems()),
            avatarList = listOf(loadAvatar()),
            detailsByAvatarId = mapOf(qinId to loadDetail())
        )

        assertTrue("武器实体不应产生候选", result.candidates.isEmpty())
        assertTrue("武器实体也不应被当作'跳过'计入(它本就不该同步)", result.skipped.isEmpty())
    }

    /*
    * 计划里没设角色目标等级时,必须记名跳过而不是猜默认值。
    * */
    @Test
    fun `计划未设角色目标等级时记名跳过`() {
        val items = listOf(
            CultivateItems(qinId, qinId, 1, CultivateItemType.Avatar, 20, 0, 0),
            CultivateItems(331, qinId, 1, CultivateItemType.Skill, 1, 9, 0),
            CultivateItems(332, qinId, 1, CultivateItemType.Skill, 1, 9, 0),
            CultivateItems(339, qinId, 1, CultivateItemType.Skill, 1, 9, 0)
        )

        val result = CultivateSyncPlanner.plan(
            entityItems = mapOf(qinEntity() to items),
            avatarList = listOf(loadAvatar()),
            detailsByAvatarId = mapOf(qinId to loadDetail())
        )

        assertTrue(result.candidates.isEmpty())
        assertEquals(1, result.skipped.size)
        assertEquals("计划里未设定角色目标等级", result.skipped.first().reason)
    }

    /*
    * 计划里技能目标等级缺失时同样记名跳过(不部分同步)。
    * */
    @Test
    fun `计划缺技能目标等级时记名跳过`() {
        val items = listOf(
            CultivateItems(qinId, qinId, 1, CultivateItemType.Avatar, 20, 90, 0),
            //故意只给两个技能
            CultivateItems(331, qinId, 1, CultivateItemType.Skill, 1, 9, 0),
            CultivateItems(332, qinId, 1, CultivateItemType.Skill, 1, 9, 0)
        )

        val result = CultivateSyncPlanner.plan(
            entityItems = mapOf(qinEntity() to items),
            avatarList = listOf(loadAvatar()),
            detailsByAvatarId = mapOf(qinId to loadDetail())
        )

        assertTrue(result.candidates.isEmpty())
        assertEquals(1, result.skipped.size)
    }

    /*
    * 接口没返回该角色详情(游戏中未拥有)时,必须说明原因,不能静默。
    * */
    @Test
    fun `接口未返回该角色详情时记名跳过`() {
        val result = CultivateSyncPlanner.plan(
            entityItems = mapOf(qinEntity() to qinItems()),
            avatarList = listOf(loadAvatar()),
            detailsByAvatarId = emptyMap()
        )

        assertTrue(result.candidates.isEmpty())
        assertEquals(1, result.skipped.size)
        assertTrue(
            "原因应说明接口未返回",
            result.skipped.first().reason.contains("未返回")
        )
    }

    /*
    * 本地缺元数据(比如未下载元数据/该角色是新增的)时也要说明,不能崩。
    * */
    @Test
    fun `本地无该角色元数据时记名跳过`() {
        val result = CultivateSyncPlanner.plan(
            entityItems = mapOf(qinEntity() to qinItems()),
            avatarList = emptyList(),
            detailsByAvatarId = mapOf(qinId to loadDetail())
        )

        assertTrue(result.candidates.isEmpty())
        assertEquals(1, result.skipped.size)
        assertTrue(
            "原因应说明元数据里找不到",
            result.skipped.first().reason.contains("元数据")
        )
    }

    /*
    * 多个角色混合:可同步的进 candidates,不可同步的进 skipped,
    * 两边数量守恒(不丢项)。
    * */
    @Test
    fun `可同步与不可同步项同时存在时两边都不丢`() {
        val otherId = 10000031
        val otherEntity = CultivateEntity(
            itemId = otherId,
            projectId = 1,
            type = CultivateEntityType.Avatar,
            status = 0
        )

        val result = CultivateSyncPlanner.plan(
            entityItems = mapOf(
                qinEntity() to qinItems(),
                //第二个角色没有详情 -> 应进 skipped
                otherEntity to qinItems()
            ),
            avatarList = listOf(loadAvatar()),
            detailsByAvatarId = mapOf(qinId to loadDetail())
        )

        assertEquals(1, result.candidates.size)
        assertEquals(1, result.skipped.size)
        //总数守恒:两个实体,一个成功一个跳过
        assertEquals(2, result.candidates.size + result.skipped.size)
    }
}
