package com.lianyi.paimonsnotebook.ui.screen.items.viewmodel.base

import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lianyi.paimonsnotebook.common.database.PaimonsNotebookDatabase
import com.lianyi.paimonsnotebook.common.database.cultivate.data.CultivateEntityType
import com.lianyi.paimonsnotebook.common.database.cultivate.data.CultivateItemType
import com.lianyi.paimonsnotebook.common.database.cultivate.entity.CultivateEntity
import com.lianyi.paimonsnotebook.common.database.cultivate.entity.CultivateItemMaterials
import com.lianyi.paimonsnotebook.common.database.cultivate.entity.CultivateItems
import com.lianyi.paimonsnotebook.common.database.cultivate.entity.CultivateProject
import com.lianyi.paimonsnotebook.common.database.user.util.AccountHelper
import com.lianyi.paimonsnotebook.common.extension.intent.setComponentName
import com.lianyi.paimonsnotebook.common.extension.list.takeFirstIf
import com.lianyi.paimonsnotebook.common.extension.scope.launchIO
import com.lianyi.paimonsnotebook.common.extension.string.errorNotify
import com.lianyi.paimonsnotebook.common.extension.string.notify
import com.lianyi.paimonsnotebook.common.extension.string.warnNotify
import com.lianyi.paimonsnotebook.common.util.enums.LoadingState
import com.lianyi.paimonsnotebook.common.util.cultivation.CultivateMaterialWriter
import com.lianyi.paimonsnotebook.common.web.hoyolab.takumi.binding.UserGameRoleData
import com.lianyi.paimonsnotebook.common.web.hoyolab.takumi.event.calculate.BatchCalculatePromotionDetail
import com.lianyi.paimonsnotebook.common.web.hoyolab.takumi.event.calculate.BatchComputeData
import com.lianyi.paimonsnotebook.common.web.hoyolab.takumi.event.calculate.CalculateClient
import com.lianyi.paimonsnotebook.common.web.hutao.genshin.item.Material
import com.lianyi.paimonsnotebook.ui.screen.cultivate_project.view.CultivateProjectOptionScreen
import com.lianyi.paimonsnotebook.ui.screen.home.util.HomeHelper
import com.lianyi.paimonsnotebook.ui.screen.items.data.cultivate.CultivateConfigData
import com.lianyi.paimonsnotebook.ui.screen.items.util.ItemFilterType

/*
* item基本viewModel
*
* observeCurrentItemState:是否观测当前item的状态
* */
open class ItemBaseViewModel<T>(private val observeCurrentItemState: Boolean = true) : ViewModel() {

    var currentItem: T? by mutableStateOf(null)

    var itemAddedToCurrentCultivateProject by mutableStateOf(false)
    var compareItem: T? by mutableStateOf(null)
    var loadingState: LoadingState by mutableStateOf(LoadingState.Loading)
    var showLoadingDialog by mutableStateOf(false)

    val materialList = mutableStateListOf<Material>()

    var currentItemLevel by mutableIntStateOf(1)
    var selectCompareItem = false

    var showNoCultivateProjectNoticeDialog by mutableStateOf(false)
    var showItemConfigDialog by mutableStateOf(false)

    open val tabs: Array<String> = arrayOf()
    val cultivateConfigList = mutableStateListOf<CultivateConfigData>()

    val itemConfigDialogButtons: Array<String> = arrayOf("取消", "确定")

    private var calculateItemId: Int = 0

    /*
    * 当前养成计划缓存
    * 在点击添加按钮时设置
    * */
    private var currentSelectedCultivateProjectCache: CultivateProject? = null

    private var currentGameRoleCache: UserGameRoleData.Role? = null

    init {
        if (observeCurrentItemState) {
            viewModelScope.launchIO {
                snapshotFlow { currentItem }.collect {
                    if (it == null) {
                        itemAddedToCurrentCultivateProject = false
                        return@collect
                    }

                    itemAddedToCurrentCultivateProject =
                        getEntityHasAddedSelectedProject(getCurrentItemId())
                }
            }
        }
    }

    private val projectDao by lazy {
        PaimonsNotebookDatabase.database.cultivateProjectDao
    }

    private val cultivateEntityDao by lazy {
        PaimonsNotebookDatabase.database.cultivateEntityDao
    }

    private val cultivateItemsDao by lazy {
        PaimonsNotebookDatabase.database.cultivateItemsDao
    }

    private val cultivateItemMaterialsDao by lazy {
        PaimonsNotebookDatabase.database.cultivateItemMaterialsDao
    }

    private val calculateClient by lazy {
        CalculateClient()
    }

    /*
    * 养成结果写入器(与「从米游社同步」共用同一份写入语义)
    * 详见 CultivateMaterialWriter 的类注释。
    * */
    private val cultivateMaterialWriter by lazy {
        CultivateMaterialWriter()
    }

    open fun init(intent: Intent) {
    }

    open fun updateMaterial() {
    }

    open fun onClickCompareItem() {
    }

    open fun onChangeItemLevel(value: Int, promoted: Boolean) {
        this.currentItemLevel = value
    }

    open fun onPromotedChange(promoted: Boolean) {
    }

    open fun toggleFilterContent() {
    }

    fun dismissNoCultivateProjectNoticeDialog() {
        showNoCultivateProjectNoticeDialog = false
    }

    fun addCurrentItemToCultivateProject() {
        val user = AccountHelper.selectedUserFlow.value
        val itemId = getCurrentItemId()

        if (user == null) {
            "必须设置一个默认用户才能使用此功能".warnNotify(false)
            return
        }

        if (user.userGameRoles.isEmpty()) {
            "当前用户没有找到游戏角色,请更换账号或稍后再试".warnNotify()
            return
        }

        currentGameRoleCache = user.getSelectedGameRole()

        if (currentGameRoleCache == null && user.userGameRoles.isNotEmpty()) {
            val role = user.userGameRoles.first()
            currentGameRoleCache = role

            "由于当前用户[${user.userInfo.nickname}]没有设置默认用户,已自动选择角色列表中的第一个角色[${role.nickname}](uid:${role.game_uid})作为本次请求的角色".warnNotify()
        }

        calculateItemId = itemId
        viewModelScope.launchIO {
            currentSelectedCultivateProjectCache = projectDao.getSelectedProject()

            if (currentSelectedCultivateProjectCache == null) {
                showNoCultivateProjectNoticeDialog = true
                return@launchIO
            }

            onShowItemConfigDialog()
        }
    }

    fun updateCurrentItemSelectedState(itemId: Int) {
        //判断当前item是否存在于当前养成计划中
        viewModelScope.launchIO {
            cultivateEntityDao.entityHasAddedSelectedProject(itemId = itemId)
        }
    }

    //这个方法需要子类重写
    open fun getCurrentItemId(): Int = -1

    fun onClickItemConfigDialogButton(index: Int) {
        if (index == 0) {
            showItemConfigDialogRequestDismiss()
            return
        }

        val role = this.currentGameRoleCache

        if (role == null) {
            "没有找到缓存的用户角色数据,请稍后再试".warnNotify()
            showItemConfigDialogRequestDismiss()
            return
        }

        val cultivateConfigDataMap = cultivateConfigList.groupBy {
            it.type
        }

        var avatar: CultivateConfigData? = null
        var weapon: BatchCalculatePromotionDetail.Weapon? = null
        var cultivateSkillList: List<BatchCalculatePromotionDetail.Skill>? = null

        val avatarList = cultivateConfigDataMap[CultivateItemType.Avatar]
        val weaponList = cultivateConfigDataMap[CultivateItemType.Weapon]
        val skillList = cultivateConfigDataMap[CultivateItemType.Skill]

        val items = mutableListOf<BatchCalculatePromotionDetail.Item>()

        if (!skillList.isNullOrEmpty()) {
            cultivateSkillList = skillList.map {
                BatchCalculatePromotionDetail.Skill(
                    id = it.id,
                    level_current = it.fromLevel,
                    level_target = it.toLevel
                )
            }
        }

        if (!avatarList.isNullOrEmpty() && cultivateSkillList != null) {
            avatar = avatarList.first()

            items += BatchCalculatePromotionDetail.Item(
                avatar_id = avatar.id,
                avatar_level_current = avatar.fromLevel,
                avatar_level_target = avatar.toLevel,
                element_attr_id = avatar.itemTypeId,
                skill_list = cultivateSkillList
            )
        }

        if (!weaponList.isNullOrEmpty()) {
            weapon = weaponList.first().let {
                BatchCalculatePromotionDetail.Weapon(
                    id = it.id,
                    level_current = it.fromLevel,
                    level_target = it.toLevel
                )
            }

            items += BatchCalculatePromotionDetail.Item(
                weapon = weapon
            )
        }

        val promotionDetail = BatchCalculatePromotionDetail(
            items = items,
            region = role.region,
            uid = role.game_uid
        )

        compute(promotionDetail)
        showItemConfigDialogRequestDismiss()
    }

    protected fun onMissingFile() {
        loadingState = LoadingState.Error
    }

    fun showItemConfigDialogRequestDismiss() {
        cultivateConfigList.clear()
        showItemConfigDialog = false
    }

    open fun onShowItemConfigDialog() {
        showItemConfigDialog = true
    }

    open fun getItemDataContent(item: T, type: ItemFilterType, isList: Boolean): String = ""

    open fun onClickItem(item: T) {
        this.currentItem = item
    }

    private fun compute(promotionDetail: BatchCalculatePromotionDetail) {
        val user = AccountHelper.selectedUserFlow.value

        if (user == null) {
            "必须设置一个默认用户才能使用此功能".warnNotify(false)
            return
        }

        viewModelScope.launchIO {
            val res = calculateClient.getCalculateBatchCompute(user, promotionDetail)

            if (!res.success) {
                "添加至养成计划失败:${res.message}".warnNotify(false)
                return@launchIO
            }

            try {
                saveAvatarComputeResult(res.data, promotionDetail)
                saveWeaponComputeResult(res.data, promotionDetail)
            } catch (e: Exception) {
                e.printStackTrace()

                "添加数据至数据库时出现错误:${e.message}".errorNotify()
            }
        }
    }

    /*
    * 把 batch_compute 的角色结果写入养成计划
    *
    * ⚠️ 具体写入逻辑已抽到 `CultivateMaterialWriter` —— 1.8.26 新增的
    *    「从米游社同步角色等级与天赋」需要**完全相同的写入语义**,若在此内联
    *    一份、那边再复制一份,两边将来必然漂移。
    *    本方法只负责"取项目 id"与"成功后的提示"这两件视图层的事。
    * */
    private suspend fun saveAvatarComputeResult(
        result: BatchComputeData,
        promotionDetail: BatchCalculatePromotionDetail
    ) {
        if (result.items.isEmpty()) return

        val projectId = currentSelectedCultivateProjectCache?.projectId ?: return

        val avatarId = cultivateMaterialWriter.writeAvatar(
            result = result,
            promotionDetail = promotionDetail,
            projectId = projectId
        ) ?: return

        onDataAddSuccess("角色", avatarId)
    }

    private suspend fun saveWeaponComputeResult(
        result: BatchComputeData,
        promotionDetail: BatchCalculatePromotionDetail
    ) {
        if (result.items.isEmpty()) return

        val weapon = promotionDetail.items.takeFirstIf { it.weapon != null }?.weapon ?: return
        val projectId = currentSelectedCultivateProjectCache?.projectId ?: return

        val weaponMaterials = result.overall_consume.map {
            CultivateItemMaterials(
                itemId = it.id,
                cultivateItemId = -weapon.id,
                projectId = projectId,
                count = it.num,
                lackCount = it.lack_num,
                status = if (it.lack_num <= 0) {
                    1
                } else {
                    0
                }
            )
        }


        //检查材料是否为空
        if (weaponMaterials.isEmpty()) {
            error("当前武器养成配置没有所需的养成材料")
        }

        if (itemAddedToCurrentCultivateProject) {
            cultivateEntityDao.deleteEntityByItemIdAndProjectId(weapon.id, projectId)
        }

        val weaponEntity = CultivateEntity(
            itemId = weapon.id,
            projectId = projectId,
            type = CultivateEntityType.Weapon,
            status = 0
        )

        cultivateEntityDao.insert(weaponEntity)

        val overallItem = CultivateItems(
            itemId = -weapon.id,
            entityItemId = weapon.id,
            projectId = projectId,
            itemType = CultivateItemType.Overall,
            fromLevel = 0,
            toLevel = 0,
            status = 0
        )

        val weaponItem = CultivateItems(
            itemId = weapon.id,
            entityItemId = weapon.id,
            projectId = projectId,
            itemType = CultivateItemType.Weapon,
            fromLevel = weapon.level_current,
            toLevel = weapon.level_target,
            status = 0
        )

        cultivateItemsDao.insert(overallItem)
        cultivateItemsDao.insert(weaponItem)


        cultivateItemMaterialsDao.insert(weaponMaterials)

        onDataAddSuccess("武器", weapon.id)
    }

    private suspend fun onDataAddSuccess(tag: String, id: Int) {
        "${tag}成功${if (itemAddedToCurrentCultivateProject) "更新" else "添加"}至养成计划[${currentSelectedCultivateProjectCache?.projectName}]中".notify()

        itemAddedToCurrentCultivateProject = getEntityHasAddedSelectedProject(id)
    }

    private suspend fun getEntityHasAddedSelectedProject(itemId: Int): Boolean {
        return cultivateEntityDao.entityHasAddedSelectedProject(itemId)
    }

    fun goCultivateProjectOptionScreen() {
        HomeHelper.goActivityByIntentNewTask {
            setComponentName(CultivateProjectOptionScreen::class.java)
            putExtra("add", true)
        }
        dismissNoCultivateProjectNoticeDialog()
    }

}