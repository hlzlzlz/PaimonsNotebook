package com.lianyi.paimonsnotebook.common.util.cultivation

import com.lianyi.paimonsnotebook.common.database.PaimonsNotebookDatabase
import com.lianyi.paimonsnotebook.common.database.cultivate.data.CultivateEntityType
import com.lianyi.paimonsnotebook.common.database.cultivate.data.CultivateItemType
import com.lianyi.paimonsnotebook.common.database.cultivate.dao.CultivateEntityDao
import com.lianyi.paimonsnotebook.common.database.cultivate.dao.CultivateItemMaterialsDao
import com.lianyi.paimonsnotebook.common.database.cultivate.dao.CultivateItemsDao
import com.lianyi.paimonsnotebook.common.database.cultivate.entity.CultivateEntity
import com.lianyi.paimonsnotebook.common.database.cultivate.entity.CultivateItemMaterials
import com.lianyi.paimonsnotebook.common.database.cultivate.entity.CultivateItems
import com.lianyi.paimonsnotebook.common.web.hoyolab.takumi.event.calculate.BatchCalculatePromotionDetail
import com.lianyi.paimonsnotebook.common.web.hoyolab.takumi.event.calculate.BatchComputeData

/*
* 把 batch_compute 的结果写入养成计划数据库
*
* ## 为什么单独抽这一层
*
* 原先这段逻辑内联在 `ItemBaseViewModel.saveAvatarComputeResult`(约 150 行),
* 只有"从资料页添加角色"这一条路径会用到。1.8.26 新增的「从米游社同步角色
* 等级与天赋」需要**完全相同的写入语义**(同样的材料分组、同样的
* `ownedCount` 计算、同样的先删后插),若复制一份,两边将来必然漂移。
*
* ## 写入语义(与既有实现逐字一致,勿"顺手改动")
*
*   - 材料分三组,分别挂在不同的 cultivate_item_id 下:
*       overall_consume  -> cultivateItemId = **-avatarId**(负数表示"全部材料总览")
*       avatar_consume   -> cultivateItemId = **avatarId**(角色突破材料)
*       skills_consume   -> cultivateItemId = **skill_info.id**(该技能的材料)
*   - `ownedCount` = 持有数 = `num - lack_num`,仅当 `has_user_info` 为真时有效;
*     否则记 `OWNED_COUNT_UNKNOWN(-1)`,UI 据此隐藏该行(显示"持有 0"会误导)。
*   - **先删 entity 再重建**:靠外键 CASCADE 连带删除 items 与 materials。
*     这是既有做法,原因是"不同等级所需材料数量不同",增量更新会残留旧行。
*
* ⚠️ **本层不做材料数量计算** —— PN 的材料由服务端 `batch_compute` 给出,
*    本地没有胡桃那套 OfflineCalculator。调用方必须先把(新等级的)
*    batch_compute 结果传进来。
* */
class CultivateMaterialWriter(
    private val cultivateEntityDao: CultivateEntityDao = PaimonsNotebookDatabase.database.cultivateEntityDao,
    private val cultivateItemsDao: CultivateItemsDao = PaimonsNotebookDatabase.database.cultivateItemsDao,
    private val cultivateItemMaterialsDao: CultivateItemMaterialsDao =
        PaimonsNotebookDatabase.database.cultivateItemMaterialsDao
) {

    /*
    * 写入一个角色的养成结果
    *
    * @return 写入的实体 id;数据不足以写入时返回 null(不抛异常)
    * */
    suspend fun writeAvatar(
        result: BatchComputeData,
        promotionDetail: BatchCalculatePromotionDetail,
        projectId: Int
    ): Int? {
        if (result.items.isEmpty()) return null

        val avatarPromotion = promotionDetail.items.firstOrNull { it.avatar_id != null } ?: return null
        val avatarId = avatarPromotion.avatar_id ?: return null

        val firstResult = result.items.first()

        //全部材料总览(负数 id 表示总览)
        val overallMaterials = result.overall_consume.map {
            CultivateItemMaterials(
                itemId = it.id,
                cultivateItemId = -avatarId,
                projectId = projectId,
                count = it.num,
                lackCount = it.lack_num,
                ownedCount = ownedCountOf(result.has_user_info, it.num, it.lack_num),
                status = if (it.lack_num > 0) 0 else 1
            )
        }

        //角色突破材料(不记 lack_num:与既有实现一致,status 恒为 0)
        val avatarMaterials = firstResult.avatar_consume.map {
            CultivateItemMaterials(
                itemId = it.id,
                cultivateItemId = avatarId,
                projectId = projectId,
                count = it.num,
                lackCount = 0,
                status = 0
            )
        }

        //技能材料,挂在各自 skill_info.id 下
        val avatarSkillMaterials = firstResult.skills_consume.flatMap { skillConsume ->
            skillConsume.consume_list.map { consume ->
                CultivateItemMaterials(
                    itemId = consume.id,
                    cultivateItemId = skillConsume.skill_info.id.toInt(),
                    projectId = projectId,
                    count = consume.num,
                    lackCount = 0,
                    status = 0
                )
            }
        }

        if (overallMaterials.isEmpty() && avatarMaterials.isEmpty() && avatarSkillMaterials.isEmpty()) {
            /*
            * ⚠️ 保留原有的 `error(...)` 语义(抛 IllegalStateException),
            *    不要改成"返回 null" —— 既有调用方 `ItemBaseViewModel` 依赖这个
            *    异常走它的 catch 分支,从而给用户弹出
            *    "添加数据至数据库时出现错误:当前角色养成配置没有所需的养成材料"。
            *    静默返回 null 会让这条提示消失,属于对既有功能的回归。
            * */
            error("当前角色养成配置没有所需的养成材料")
        }

        //先删 entity(CASCADE 连带删 items 与 materials),再重建
        /*
        * ⚠️ 与原实现的一处**有意差异**:原代码是
        *     `if (itemAddedToCurrentCultivateProject) deleteEntity(...)`,
        *     即"仅当自认为已添加过"才删。这里改成**无条件先删**。
        *
        *     两者对既有路径**等价**(没添加过时删除是 no-op,返回 0 行),
        *     但无条件删更稳妥:那个布尔是个**缓存状态**,由
        *     `getEntityHasAddedSelectedProject` 维护;用户切换养成计划后它可能
        *     已过期,此时"实体其实存在但标志为 false"就会插入主键冲突
        *     (primaryKeys = item_id + project_id),或被 @Upsert 静默改写而
        *     残留旧的 materials 行(材料数量是按等级算的,残留即错误)。
        *
        *     本次新增的"同步"路径尤其需要无条件替换语义。
        * */
        cultivateEntityDao.deleteEntityByItemIdAndProjectId(avatarId, projectId)

        cultivateEntityDao.insert(
            CultivateEntity(
                itemId = avatarId,
                projectId = projectId,
                type = CultivateEntityType.Avatar,
                status = 0
            )
        )

        cultivateItemsDao.insert(
            CultivateItems(
                itemId = -avatarId,
                entityItemId = avatarId,
                projectId = projectId,
                itemType = CultivateItemType.Overall,
                fromLevel = 0,
                toLevel = 0,
                status = 0
            )
        )

        cultivateItemsDao.insert(
            CultivateItems(
                itemId = avatarId,
                entityItemId = avatarId,
                projectId = projectId,
                itemType = CultivateItemType.Avatar,
                fromLevel = avatarPromotion.avatar_level_current ?: 0,
                toLevel = avatarPromotion.avatar_level_target ?: 0,
                status = 0
            )
        )

        val skillItems = (avatarPromotion.skill_list ?: emptyList()).map {
            CultivateItems(
                itemId = it.id,
                entityItemId = avatarId,
                projectId = projectId,
                itemType = CultivateItemType.Skill,
                fromLevel = it.level_current,
                toLevel = it.level_target,
                status = 0
            )
        }
        if (skillItems.isNotEmpty()) {
            cultivateItemsDao.insert(skillItems)
        }

        cultivateItemMaterialsDao.insert(overallMaterials)
        cultivateItemMaterialsDao.insert(avatarMaterials)
        cultivateItemMaterialsDao.insert(avatarSkillMaterials)

        return avatarId
    }

    /*
    * 玩家实际持有数 = 需要总数 - 缺少数
    *
    * 与胡桃 InventoryService.cs `(int)item.Num - item.LackNum` 一致。
    * ⚠️ 服务端只在请求带 uid/region 时才按真实库存计算 lack_num;
    *    `has_user_info` 为 false 时二者是纯计算值,相减得 0 —— 此时必须记
    *    **未知(-1)**,否则 UI 会显示"持有 0"这种误导文案。
    * */
    private fun ownedCountOf(hasUserInfo: Boolean, num: Int, lackNum: Int): Int =
        if (hasUserInfo) {
            (num - lackNum).coerceAtLeast(0)
        } else {
            CultivateItemMaterials.OWNED_COUNT_UNKNOWN
        }
}
