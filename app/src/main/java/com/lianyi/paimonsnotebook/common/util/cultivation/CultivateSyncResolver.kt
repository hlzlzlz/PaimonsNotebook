package com.lianyi.paimonsnotebook.common.util.cultivation

import com.lianyi.paimonsnotebook.common.web.hutao.genshin.avatar.AvatarData
import com.lianyi.paimonsnotebook.common.web.hoyolab.takumi.game_record.character.CharacterDetailData

/*
* 把「游戏记录的角色详情」转换成「养成计划要写入的等级信息」
*
* 移植自胡桃 Snap.Hutao.Remastered 的 `SyncAvatarInfoByHoyolabGameRecordAsync`
* （提交 `b994258da`「为养成计划添加通过米游社同步角色等级与天赋的选项」）。
*
* ## ⚠️⚠️ 本文件存在的唯一理由:两套技能 id 体系不同
*
* 这是最容易写出"看起来对、实际全错"的地方(与 1.8.20 DPS 的 `skill_id`/
* `GroupId` 事故同源),**必须靠真实数据钉住**:
*
* | 来源 | 字段 | 琴的取值 |
* | --- | --- | --- |
* | 游戏记录 `character/detail` 的 `skills[]` | `skill_id` | **10031 / 10033 / 10034** |
* | 元数据 `SkillDepot` 的普攻/战技/爆发 | `Id` | **10031 / 10033 / 10034** ← 与上面一致 |
* | 元数据 同上 | `GroupId` | 331 / 332 / 339 |
* | 养成计划 `cultivate_items.item_id`(技能) | —— | **GroupId**(见 AvatarScreenViewModel:271) |
* | 养成计划 `batch_compute` 请求的 `skill_list[].id` | —— | **GroupId** |
*
* ⇒ **接口的 `skill_id` 要先去元数据里换成 `GroupId`,才能写进养成计划**。
*    直接把 `skill_id` 当 `GroupId` 用,会得到一个既不是 331 也不是 10031 的
*    无效 id —— 表现为"同步成功但材料没变"或"算出离谱材料"。
*
* ## 另外两条实测约束
*   - **只取 `skill_type == 1`**:`character/detail` 里同时返回被动天赋
*     (`skill_type == 2`,琴的 321/322/323),它们**没有可升级等级**,
*     混进来会让"三技能"匹配错位。
*   - **天赋等级必须来自接口的 `level`**,不能用元数据推算 —— 与项目
*     "不猜等级、猜等于编造"的既有纪律一致。
*
* ## 刻意不做的事
*   - **不做本地材料重算**:PN 的养成材料由服务端 `batch_compute` 计算,
*     本地没有胡桃那套 `OfflineCalculator`。本层只产出"当前/目标等级",
*     真正的材料刷新由既有链路(重新走一次 batch_compute + 入库)完成。
*   - 因此本文件是**纯函数**,不碰网络与数据库,可完全单测。
* */
object CultivateSyncResolver {

    /*
    * 一个角色的同步结果:角色等级 + 三个可升级技能(普攻/战技/爆发)
    *
    * 技能 id 全部是**元数据的 GroupId**(养成计划口径),不是接口的 skill_id。
    * */
    data class AvatarSyncInfo(
        val avatarId: Int,
        val avatarLevelCurrent: Int,
        val avatarLevelTarget: Int,
        val skills: List<SkillSyncInfo>
    )

    data class SkillSyncInfo(
        //养成计划口径的技能 id(= 元数据 GroupId)
        val groupId: Int,
        val levelCurrent: Int,
        val levelTarget: Int,
        val name: String
    )

    /*
    * 从真实数据反推"当前等级"时,角色等级的上界是用户设定的目标等级 ——
    * 玩家当前等级不可能超过他自己设的目标。
    *
    * 与胡桃一致:`Math.Min(character.Base.Level, levelInfo.AvatarLevelTo)`
    * */
    fun buildAvatarSyncInfo(
        avatar: AvatarData,
        detail: CharacterDetailData.DetailItem,
        //养成计划里已设的目标等级(来自 CultivateItems.toLevel)
        targetAvatarLevel: Int,
        //养成计划里已设的技能目标等级(按 GroupId 索引)
        targetSkillLevels: Map<Int, Int>
    ): AvatarSyncInfo? {
        val depot = avatar.skillDepot

        /*
        * 按元数据的 SkillSlotResolver 口径取三个可升级技能。
        *
        * ⚠️ 复用 SkillSlotResolver.calculableSkills 而不是 `Skills[0]`/`Skills[1]`:
        *    实测有 26/118 角色的 Skills[0] 是「特殊跳跃」这类参数为空的伪技能,
        *    按下标取会静默取错(DPS 那边已踩过,同一套数据同一套坑)。
        * */
        val kept = com.lianyi.paimonsnotebook.common.util.damage.SkillSlotResolver
            .calculableSkills(depot)

        val slotSkills = listOf(
            kept.getOrNull(0),   // 普攻
            kept.getOrNull(1),   // 战技
            depot.EnergySkill    // 爆发
        )

        //任一槽位缺失就整体放弃 —— 与胡桃 `is not [{ }, { }, { }, ..]` 的短路一致,
        //避免"部分同步"导致用户看到半新半旧的等级
        if (slotSkills.any { it == null }) {
            return null
        }

        /*
        * ⚠️ 映射方向:**接口 skill_id -> 元数据 Id -> 取该技能的 GroupId**
        *    下面的 map 以元数据 Id 为键,是为了拿 GroupId;等级一律取自接口。
        * */
        val byMetadataId = slotSkills.filterNotNull().associateBy { it.Id }

        val detailSkillsBySkillId = detail.skills
            //只保留可升级技能,滤掉被动天赋(skill_type == 2)
            .filter { it.skill_type == ACTIVE_SKILL_TYPE }
            .associateBy { it.skill_id }

        val skillSyncInfos = mutableListOf<SkillSyncInfo>()

        for (skill in slotSkills.filterNotNull()) {
            val detailSkill = detailSkillsBySkillId[skill.Id] ?: return null

            //养成计划口径的 id
            val groupId = skill.GroupId

            //目标等级:养成计划里已设的;没设过(NaN/缺失)则不参与同步
            val target = targetSkillLevels[groupId] ?: return null

            /*
            * 当前等级取接口真实值,并夹到目标以下:
            * 玩家当前等级高于自己设的目标时(比如设了"战技6级"但实际已8级),
            * 取 min 才不会算出负数材料。
            * */
            val current = minOf(detailSkill.level, target)

            skillSyncInfos += SkillSyncInfo(
                groupId = groupId,
                levelCurrent = current,
                levelTarget = target,
                name = skill.Name
            )
        }

        //元数据里技能数与接口能匹配上的数量不一致 -> 放弃(避免残缺同步)
        if (skillSyncInfos.size != slotSkills.size) {
            return null
        }

        return AvatarSyncInfo(
            avatarId = avatar.id,
            avatarLevelCurrent = minOf(detail.base.level, targetAvatarLevel),
            avatarLevelTarget = targetAvatarLevel,
            skills = skillSyncInfos
        )
    }

    //可升级技能的类型码(1=普攻/战技/爆发,2=被动天赋)
    private const val ACTIVE_SKILL_TYPE = 1
}
