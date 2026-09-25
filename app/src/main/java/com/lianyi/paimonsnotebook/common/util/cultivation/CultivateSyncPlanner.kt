package com.lianyi.paimonsnotebook.common.util.cultivation

import com.lianyi.paimonsnotebook.common.database.cultivate.data.CultivateEntityType
import com.lianyi.paimonsnotebook.common.database.cultivate.data.CultivateItemType
import com.lianyi.paimonsnotebook.common.database.cultivate.entity.CultivateEntity
import com.lianyi.paimonsnotebook.common.database.cultivate.entity.CultivateItems
import com.lianyi.paimonsnotebook.common.web.hutao.genshin.avatar.AvatarData
import com.lianyi.paimonsnotebook.common.web.hoyolab.takumi.game_record.character.CharacterDetailData

/*
* 「从米游社同步角色等级与天赋」的编排层
*
* 移植胡桃 `b994258da`(`SyncAvatarInfoByHoyolabGameRecordAsync`)。
*
* ## 职责划分
*   - 本层:**挑出要同步的项**(养成计划里类型为 AvatarAndSkill 的实体),
*     结合当前计划里已设的目标等级,产出待写入的 `AvatarSyncInfo`。
*   - `CultivateSyncResolver`:**id 换算**(接口 skill_id -> 元数据 GroupId)。
*   - `CultivateMaterialWriter`:**落库**。
*
* ## 为什么目标等级取自"计划里已设的值"而不是猜
*
* 这是本功能与"添加"语义的关键区别:同步**只更新"当前等级"这一半**,
* 不动用户设定的目标。所以:
*   - 目标等级 = `cultivate_items.toLevel`(用户之前在资料页设定的)
*   - 当前等级 = 接口返回的真实等级
*   - 重算材料 = 用(新当前, 旧目标)再走一次 batch_compute
*
* ⇒ **计划里没设过目标等级的技能,不参与同步**(`CultivateSyncResolver` 会返回
*    null 整体放弃)—— 给默认值等于编造用户的目标。
*
* ## 纯逻辑
*   本层不发起网络请求、不写库,只做"计划数据 + 元数据 + 接口数据 -> 待同步列表"
*   的转换,故可完全单测(见 `CultivateSyncPlannerTest`)。
* */
object CultivateSyncPlanner {

    /*
    * 一个待同步条目:某角色在当前计划里已有的等级设定 + 接口给出的真实等级
    * */
    data class SyncCandidate(
        val avatar: AvatarData,
        val detail: CharacterDetailData.DetailItem,
        val syncInfo: CultivateSyncResolver.AvatarSyncInfo
    )

    /*
    * 从"当前计划 + 元数据 + 接口详情"算出所有可同步的条目
    *
    * @param entityItems     当前计划的 `entity -> items` 映射(来自 DAO 的 Flow)
    * @param avatarList      角色元数据(用于取 GroupId 与技能槽位)。
    *                        ⚠️ 收 `List<AvatarData>` 而不是 `AvatarService` ——
    *                        service 在**构造器内同步读文件**,传进来会让本层
    *                        无法在纯 JVM 单测里运行(和 Router 的既有教训同类)。
    * @param detailsByAvatarId 接口返回的角色详情,key = 角色 id
    * @return 可同步的候选;**无法同步的项被记录在 skipped 里**,不静默丢弃
    * */
    fun plan(
        entityItems: Map<CultivateEntity, List<CultivateItems>>,
        avatarList: List<AvatarData>,
        detailsByAvatarId: Map<Int, CharacterDetailData.DetailItem>
    ): PlanResult {
        val candidates = mutableListOf<SyncCandidate>()
        val skipped = mutableListOf<SkippedEntry>()

        val avatarIdMap = avatarList.associateBy { it.id }

        entityItems.forEach { (entity, items) ->
            //只处理角色实体(武器没有"等级与天赋"可同步)
            if (entity.type != CultivateEntityType.Avatar) {
                return@forEach
            }

            val avatarId = entity.itemId

            val avatar = avatarIdMap[avatarId]
            if (avatar == null) {
                skipped += SkippedEntry(avatarId, null, "本地元数据里找不到该角色")
                return@forEach
            }

            val detail = detailsByAvatarId[avatarId]
            if (detail == null) {
                skipped += SkippedEntry(avatarId, avatar.name, "米游社角色详情未返回该角色(可能未在游戏中拥有)")
                return@forEach
            }

            /*
            * 取"角色等级"这一项的目标等级。
            *
            * cultivate_items 里 itemType == Avatar 且 itemId == 角色id 的那条
            * 存的是角色等级;itemType == Skill 的存的是各技能等级。
            * */
            val avatarItem = items.firstOrNull {
                it.itemType == CultivateItemType.Avatar && it.itemId == avatarId
            }

            /*
            * 装备位(Overall)与技能项不必在这里筛:技能的目标等级靠下面的
            * targetSkillLevels 映射,而 Overall 的 itemId 是负数,不会与技能 id 冲突。
            * */
            val targetAvatarLevel = avatarItem?.toLevel ?: 0
            if (targetAvatarLevel <= 0) {
                skipped += SkippedEntry(avatarId, avatar.name, "计划里未设定角色目标等级")
                return@forEach
            }

            /*
            * 技能目标等级:key 必须是**养成计划口径的 GroupId**
            * (cultivate_items.item_id 存的就是 GroupId,见 AvatarScreenViewModel:271)
            *
            * 只取 itemType == Skill 的项来构键。实测两个 id 空间**不相交**
            * (角色 id 10000002~10000150,技能 GroupId 231~2539),所以即使把
            * Avatar 项也放进来也不会撞键;但显式按类型过滤,以免将来某一方
            * 的编号规则变化时此处静默出错。
            * */
            val targetSkillLevels = items
                .filter { it.itemType == CultivateItemType.Skill }
                .associate { it.itemId to it.toLevel }
                .filterValues { it > 0 }

            val syncInfo = CultivateSyncResolver.buildAvatarSyncInfo(
                avatar = avatar,
                detail = detail,
                targetAvatarLevel = targetAvatarLevel,
                targetSkillLevels = targetSkillLevels
            )

            if (syncInfo == null) {
                skipped += SkippedEntry(
                    avatarId,
                    avatar.name,
                    "技能等级未能全部匹配(需计划里已设定普攻/战技/爆发的目标等级)"
                )
                return@forEach
            }

            candidates += SyncCandidate(avatar, detail, syncInfo)
        }

        return PlanResult(candidates, skipped)
    }

    /*
    * 同步结果。
    *
    * ⚠️ `skipped` **必须暴露给 UI** —— 与项目既有的 `skippedMembers`
    *    (DPS 页)同一原则:静默丢弃会让用户以为"全部同步成功",
    *    而实际有角色没被更新。
    * */
    data class PlanResult(
        val candidates: List<SyncCandidate>,
        val skipped: List<SkippedEntry>
    )

    data class SkippedEntry(
        val avatarId: Int,
        val avatarName: String?,
        val reason: String
    )
}
