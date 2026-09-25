package com.lianyi.paimonsnotebook.ui.screen.cultivate_project.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.util.fastMap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lianyi.paimonsnotebook.common.data.hoyolab.user.UserAndUid
import com.lianyi.paimonsnotebook.common.data.popup.IconTitleInformationPopupWindowData
import com.lianyi.paimonsnotebook.common.data.popup.PopupWindowPositionProvider
import com.lianyi.paimonsnotebook.common.database.PaimonsNotebookDatabase
import com.lianyi.paimonsnotebook.common.database.cultivate.data.CultivateEntityType
import com.lianyi.paimonsnotebook.common.database.cultivate.entity.CultivateEntity
import com.lianyi.paimonsnotebook.common.database.cultivate.entity.CultivateItemMaterials
import com.lianyi.paimonsnotebook.common.database.cultivate.entity.CultivateItems
import com.lianyi.paimonsnotebook.common.database.user.util.AccountHelper
import com.lianyi.paimonsnotebook.common.extension.scope.launchIO
import com.lianyi.paimonsnotebook.common.util.cultivation.CultivateMaterialWriter
import com.lianyi.paimonsnotebook.common.util.cultivation.CultivateSyncPlanner
import com.lianyi.paimonsnotebook.common.util.cultivation.ResinStatisticsCalculator
import com.lianyi.paimonsnotebook.common.extension.scope.withContextMain
import com.lianyi.paimonsnotebook.common.extension.string.errorNotify
import com.lianyi.paimonsnotebook.common.extension.string.notify
import com.lianyi.paimonsnotebook.common.extension.string.warnNotify
import com.lianyi.paimonsnotebook.common.util.data_store.PreferenceKeys
import com.lianyi.paimonsnotebook.common.util.data_store.dataStoreValues
import com.lianyi.paimonsnotebook.common.util.enums.LoadingState
import com.lianyi.paimonsnotebook.common.web.hoyolab.takumi.event.calculate.BatchCalculatePromotionDetail
import com.lianyi.paimonsnotebook.common.web.hoyolab.takumi.event.calculate.CalculateClient
import com.lianyi.paimonsnotebook.common.web.hoyolab.takumi.game_record.GameRecordClient
import com.lianyi.paimonsnotebook.common.web.hutao.genshin.avatar.AvatarData
import com.lianyi.paimonsnotebook.common.web.hutao.genshin.common.service.AvatarService
import com.lianyi.paimonsnotebook.common.web.hutao.genshin.common.service.MaterialService
import com.lianyi.paimonsnotebook.common.web.hutao.genshin.common.service.WeaponService
import com.lianyi.paimonsnotebook.common.web.hutao.genshin.item.Material
import com.lianyi.paimonsnotebook.common.web.hutao.genshin.item.Materials
import com.lianyi.paimonsnotebook.common.web.hutao.genshin.weapon.WeaponData
import com.lianyi.paimonsnotebook.ui.screen.cultivate_project.data.CultivateItemInfoData
import com.lianyi.paimonsnotebook.ui.screen.cultivate_project.data.EntityBaseInfo
import com.lianyi.paimonsnotebook.ui.screen.cultivate_project.data.MaterialBaseInfo
import com.lianyi.paimonsnotebook.ui.screen.cultivate_project.view.CultivateProjectOptionScreen
import com.lianyi.paimonsnotebook.ui.screen.home.util.HomeHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.abs

class CultivateProjectScreenViewModel : ViewModel() {

    var loadingState by mutableStateOf(LoadingState.Loading)

    private val avatarList = mutableListOf<AvatarData>()
    private val weaponList = mutableListOf<WeaponData>()

    private val avatarService by lazy {
        AvatarService(this::onMissingFile)
    }

    private val weaponService by lazy {
        WeaponService(this::onMissingFile)
    }

    private val materialService by lazy {
        MaterialService(this::onMissingFile)
    }

    val entityCultivateItemsPairList =
        mutableStateListOf<Pair<CultivateEntity, List<CultivateItems>>>()

    /*
    * 存储所有计算项(cultivate_item)的所需材料
    * key = id(角色消耗为角色/武器id,技能消耗为技能id,消耗总览为负的角色/武器id)
    * */
    private val itemsMaterialsMap = mutableMapOf<Int, List<CultivateItemMaterials>>()

    private val avatarIdMap = mutableMapOf<Int, AvatarData>()
    private val weaponIdMap = mutableMapOf<Int, WeaponData>()

    private val cultivateProjectDao = PaimonsNotebookDatabase.database.cultivateProjectDao
    private val cultivateEntityDao = PaimonsNotebookDatabase.database.cultivateEntityDao
    private val cultivateItemsDao = PaimonsNotebookDatabase.database.cultivateItemsDao
    private val cultivateItemMaterialsDao =
        PaimonsNotebookDatabase.database.cultivateItemMaterialsDao

    //同步功能用:角色详情/列表 + batch_compute + 写入器
    private val gameRecordClient by lazy { GameRecordClient() }
    private val calculateClient by lazy { CalculateClient() }
    private val cultivateMaterialWriter by lazy { CultivateMaterialWriter() }

    /*
    * 材料总览分组
    * 将相同组别的材料(可合成高一级的)分为同一组
    * */
    val overallMaterialBaseInfoGroupList = mutableStateListOf<List<MaterialBaseInfo>>()

    val overallMaterialBaseInfoGroupListFlatten = mutableStateListOf<MaterialBaseInfo>()

    //树脂预估(胡桃公式,世界等级9期望)
    var resinStatisticsResult by mutableStateOf<ResinStatisticsCalculator.ResinResult?>(null)
        private set

    /*
    * 材料总览实体分组
    * 根据材料总览分组分每个实体对应的组,实体A用材料B,就分到材料B的组中
    * key = 同组材料中第一个材料的material_id, value =使用此材料的实体
    * */
    private val overallEntityBaseInfoMap = mutableMapOf<Int, MutableList<EntityBaseInfo>>()

    var showOverallPageGridList by mutableStateOf(true)
        private set

    //养成项是否根据实体类型排序
    private var cultivateProjectSortByEntityType = false

    //设置setData job引用
    private var setDataJob: Job? = null

    private val mutex = Mutex()

    private var cultivateEntityMapList = mutableMapOf<CultivateEntity, List<CultivateItems>>()

    init {
        loadingState = LoadingState.Loading

        viewModelScope.launchIO {
            avatarList += avatarService.avatarList
            weaponList += weaponService.weaponList

            avatarIdMap += avatarService.avatarList.associateBy { it.id }
            weaponIdMap += weaponService.weaponList.associateBy { it.id }

        }

        viewModelScope.launchIO {
            dataStoreValues {
                cultivateProjectSortByEntityType =
                    it[PreferenceKeys.CultivateProjectSortByEntityType] ?: false

                /*
                * 当setDataJob为空,表示是第一次进入界面,此次不更新数据集合
                * 当不为空时表示已经完成初始化阶段
                * */
                if (setDataJob == null || setDataJob?.isCompleted == true) {
                    return@dataStoreValues
                }

                mutex.withLock {
                    setDataJob?.cancelAndJoin()
                    setDataJob = launchIO {
                        updateMaterialOverallGroup()
                    }
                }
            }
        }

        viewModelScope.launchIO {
            //更新当前选择的养成计划时更新
            cultivateProjectDao.getSelectedProjectFlow().collectLatest { cultivateProject ->
                currentSelectedProjectId = cultivateProject?.projectId ?: -1

                if (cultivateProject == null) {
                    withContextMain {
                        loadingState = LoadingState.Empty
                    }
                    return@collectLatest
                } else {
                    withContextMain {
                        loadingState = LoadingState.Loading
                    }
                }

                withMutexLockUpdateData()
            }
        }
    }

    val tabs = arrayOf("养成计划", "材料总览")

    var currentPageIndex by mutableIntStateOf(0)
        private set

    //当前选中的养成实体id
    private var currentSelectedEntityId: Int = -1

    //当前选中的养成计划id
    private var currentSelectedProjectId: Int = -1

    //显示删除养成实体对话框
    var showDeleteEntityConfirmDialog by mutableStateOf(false)
        private set

    //删除对话框的显示内容
    var deleteEntityConfirmDialogContent = ""
        private set

    var showPopupWindow by mutableStateOf(false)
        private set

    lateinit var showPopupWindowInfo: IconTitleInformationPopupWindowData
        private set

    var popupWindowProvider by mutableStateOf(PopupWindowPositionProvider())
        private set

    private suspend fun withMutexLockUpdateData() {
        mutex.withLock {
            setDataJob?.cancelAndJoin()
            setDataJob = viewModelScope.launchIO {
                setListData()
            }
        }
    }

    private suspend fun setListData() {
        //更新实体时会回调此部分
        cultivateEntityDao.getCultivateEntityMapListFlowByProjectId(
            currentSelectedProjectId
        ).collectLatest { cultivateMap ->
            cultivateEntityMapList.clear()
            cultivateEntityMapList += cultivateMap

            updateCultivateMaterialsMap()
        }
    }

    private suspend fun updateCultivateMaterialsMap() {
        //更新材料时会回调此部分
        cultivateItemsDao.getCultivateIdsMaterialsMapFlowByProjectId(
            currentSelectedProjectId
        ).collect {

            withContextMain {
                itemsMaterialsMap.clear()

                itemsMaterialsMap += it
            }

            /*
            * 判断两个延迟初始化的map是否初始化完成,没有完成就每100毫秒循环判断一次
            * 此功能只会在第一次进入此界面时用到
            * */
            while (!(avatarIdMap.isNotEmpty() && weaponIdMap.isNotEmpty()) && loadingState != LoadingState.Error) {
                delay(100)
            }

            updateMaterialOverallGroup()
        }
    }

    private fun onMissingFile() {
        loadingState = LoadingState.Error
    }

    fun onSelectTabBar(index: Int) {
        currentPageIndex = index
    }

    /*
    * ══════════════════════════════════════════════════════════════
    * 从米游社同步角色等级与天赋(移植胡桃 b994258da)
    * ══════════════════════════════════════════════════════════════
    *
    * 语义:**只更新"当前等级"这一半,不动用户设定的目标等级**。
    *   - 目标等级 = 计划里已设的 toLevel
    *   - 当前等级 = 接口真实等级
    *   - 重算材料 = 用(新当前, 旧目标)重走 batch_compute 并落库
    *
    * ⚠️ 三处"不静默"的处置(与项目既有 skippedMembers 同一原则):
    *   1. 未登录 / 未选角色 -> 明确提示并中止
    *   2. 单个角色同步失败 -> 记名进 skipped,继续处理其它角色
    *   3. 全部失败 -> 明确告知,不留"什么都没发生"的假象
    * */
    var isSyncing by mutableStateOf(false)
        private set

    fun syncAvatarLevelsFromHoyolab() {
        if (isSyncing) return

        val projectId = currentSelectedProjectId
        if (projectId == -1) {
            "你还没有设置选中的养成计划".warnNotify()
            return
        }

        viewModelScope.launchIO {
            withContextMain { isSyncing = true }

            try {
                doSync(projectId)
            } catch (e: Exception) {
                // ⚠️ 必须捕获:本项目协程未捕获异常会静默杀进程
                "同步失败:${e.message ?: "未知错误"}".errorNotify()
            } finally {
                withContextMain { isSyncing = false }
            }
        }
    }

    private suspend fun doSync(projectId: Int) {
        val user = AccountHelper.selectedUserFlow.value
        if (user == null) {
            "请先登录米游社账号".warnNotify()
            return
        }

        val role = user.getSelectedGameRole()
        if (role == null) {
            "请先选择游戏角色".warnNotify()
            return
        }

        val userAndUid = UserAndUid(user.userEntity, role.getPlayerUid())

        //当前计划里已配置的角色实体(只有这些才需要同步)
        val entityItems = cultivateEntityMapList.toMap()
        if (entityItems.isEmpty()) {
            "当前养成计划里还没有养成项".warnNotify()
            return
        }

        /*
        * 先拉角色列表 -> 取全部角色 id。
        * 这里刻意拉"全部自有角色"而不是只拉计划里的:接口按 id 列表查询,
        * 一次拿全比逐个查省请求;且计划里的角色必然在自有角色里。
        * */
        val listRes = gameRecordClient.getCharacterList(userAndUid)
        val characterList = listRes.data?.list
        if (characterList.isNullOrEmpty()) {
            "未能获取角色列表,无法同步".errorNotify()
            return
        }

        //只同步"计划里的角色",避免多余请求
        val plannedIds = entityItems.keys
            .filter { it.type == CultivateEntityType.Avatar }
            .map { it.itemId }
            .toSet()

        val idsToFetch = characterList.map { it.id }.filter { it in plannedIds }
        if (idsToFetch.isEmpty()) {
            "当前计划里的角色均未在游戏角色列表中找到".warnNotify()
            return
        }

        /*
        * 拉详情。⚠️ 显式判空:`ResultData.data` 声明非空但运行时可为 null
        * (解析异常时 getAsJsonNative 返回 null,Gson 用 Unsafe 分配不执行
        *  Kotlin 非空校验)。
        * */
        val detailRes = gameRecordClient.getCharacterDetail(userAndUid, idsToFetch)
        val details = detailRes.data?.list
        if (details.isNullOrEmpty()) {
            "未能获取角色详情(可能触发了风控,请稍后重试)".errorNotify()
            return
        }

        val detailsByAvatarId = details.associateBy { it.base.id }

        val planResult = CultivateSyncPlanner.plan(
            entityItems = entityItems,
            avatarList = avatarList,
            detailsByAvatarId = detailsByAvatarId
        )

        if (planResult.candidates.isEmpty()) {
            //把第一个跳过原因带给用户,否则"什么都没发生"无法排查
            val reason = planResult.skipped.firstOrNull()?.reason
            if (reason.isNullOrBlank()) {
                "没有可同步的角色".warnNotify()
            } else {
                "没有可同步的角色:$reason".warnNotify()
            }
            return
        }

        var updated = 0
        val failed = mutableListOf<String>()

        for (candidate in planResult.candidates) {
            try {
                recomputeAndWrite(candidate, projectId)
                updated++
            } catch (e: Exception) {
                //单个角色失败不影响其它角色
                failed += (candidate.avatar.name)
            }
        }

        val skippedCount = planResult.skipped.size + failed.size

        if (updated == 0) {
            //把"为什么一个都没成功"的具体原因带给用户,否则无法排查
            val detail = failed.firstOrNull()?.let { "$it 材料计算失败" }
                ?: planResult.skipped.firstOrNull()?.reason
                ?: "没有可用的角色"

            "同步失败:$detail".errorNotify()
            return
        }

        val message = buildString {
            append("已更新 $updated 个角色")
            if (skippedCount > 0) append(",跳过 $skippedCount 个")
        }
        message.notify()

        //刷新界面数据
        withMutexLockUpdateData()
    }

    /*
    * 用"新的当前等级 + 原有的目标等级"重走一次 batch_compute,再落库
    *
    * ⚠️ 必须重走服务端计算:PN 本地没有胡桃那套 OfflineCalculator,
    *    材料数量只能由 batch_compute 给出。
    * */
    private suspend fun recomputeAndWrite(
        candidate: CultivateSyncPlanner.SyncCandidate,
        projectId: Int
    ) {
        val info = candidate.syncInfo

        val user = AccountHelper.selectedUserFlow.value ?: return
        //上面 doSync 已确认 role 非空,这里再取一次(值可能被用户切走)
        val role = user.getSelectedGameRole() ?: return

        val promotionDetail = BatchCalculatePromotionDetail(
            items = listOf(
                BatchCalculatePromotionDetail.Item(
                    avatar_id = info.avatarId,
                    avatar_level_current = info.avatarLevelCurrent,
                    avatar_level_target = info.avatarLevelTarget,
                    element_attr_id = candidate.avatar.fetterInfo.elementType,
                    skill_list = info.skills.map {
                        BatchCalculatePromotionDetail.Skill(
                            //⚠️ 必须是养成计划口径的 GroupId(由 CultivateSyncResolver 换算)
                            id = it.groupId,
                            level_current = it.levelCurrent,
                            level_target = it.levelTarget
                        )
                    }
                )
            ),
            region = role.region,
            uid = role.game_uid
        )

        val res = calculateClient.getCalculateBatchCompute(user, promotionDetail)
        if (!res.success) {
            error(res.message.ifBlank { "计算接口返回失败" })
        }

        /*
        * ⚠️ `res.data` 声明为非空,但**运行时可为 null**:解析异常时
        *    `getAsJsonNative` 返回 null,而 Gson 用 Unsafe 分配实例、
        *    不执行 Kotlin 非空校验(项目既有处置见 DpsCalculatorScreenViewModel:186)。
        *
        *    这里不直接传 `res.data` —— 否则在真机偶发解析失败时会走到
        *    CultivateMaterialWriter 里对 `result.items` 的解引用而 NPE。
        *    `throwIfNull` 只做一次判断,保持"单个角色失败不影响其它角色"的语义。
        * */
        val data = res.data ?: error("计算接口返回数据为空")

        cultivateMaterialWriter.writeAvatar(
            result = data,
            promotionDetail = promotionDetail,
            projectId = projectId
        ) ?: error("写入数据失败")
    }

    fun goOptionScreen() {
        HomeHelper.goActivity(CultivateProjectOptionScreen::class.java)
    }

    fun switchShowOverallPageGridList() {
        showOverallPageGridList = !showOverallPageGridList
    }

    fun onShowMaterialInfoPopupDialog(
        material: Material,
        provider: PopupWindowPositionProvider
    ) {
        showPopupWindowInfo = material.getShowPopupWindowInfo()
        popupWindowProvider = provider
        showPopupWindow = true
    }

    fun onShowEntityInfoPopupDialog(
        id: Int,
        provider: PopupWindowPositionProvider
    ) {
        val weaponData = getWeaponData(id)
        val avatarData = getAvatarData(id)

        if (weaponData == null && avatarData == null) return

        if (avatarData != null) {
            showPopupWindowInfo = IconTitleInformationPopupWindowData(
                title = avatarData.name,
                subTitle = avatarData.fetterInfo.Title,
                iconUrl = avatarData.iconUrl,
                content = avatarData.description
            )
        }

        if (weaponData != null) {
            showPopupWindowInfo = IconTitleInformationPopupWindowData(
                title = weaponData.name,
                subTitle = weaponData.weaponTypeName,
                iconUrl = weaponData.iconUrl,
                content = weaponData.description
            )
        }

        popupWindowProvider = provider
        showPopupWindow = true
    }

    fun onPopupWindowDismissRequest() {
        showPopupWindow = false
    }

    fun entityConfirmDialogDismissRequest() {
        showDeleteEntityConfirmDialog = false
        deleteEntityConfirmDialogContent = ""
        currentSelectedEntityId = -1
    }

    /*
    * 获取角色计算项map
    * */
    fun getAvatarCultivateIdMap(avatarData: AvatarData) = mapOf(
        avatarData.id.let {
            it to CultivateItemInfoData(
                id = it,
                name = "等级提升",
                icon = ""
            )
        },
        avatarData.skillDepot.Skills.first().let {
            it.GroupId to CultivateItemInfoData(
                id = it.GroupId,
                name = "普通攻击",
                icon = it.iconUrl
            )
        },
        avatarData.skillDepot.Skills.last().let {
            it.GroupId to CultivateItemInfoData(
                id = it.GroupId,
                name = "元素战技",
                icon = it.iconUrl
            )
        },
        avatarData.skillDepot.EnergySkill.let {
            it.GroupId to CultivateItemInfoData(
                id = it.GroupId,
                name = "元素爆发",
                icon = it.iconUrl
            )
        },
    )

    fun getAvatarData(id: Int) = avatarIdMap[id]

    fun getWeaponData(id: Int) = weaponIdMap[id]

    fun getMaterialData(id: Int) = materialService.getMaterialById(id)

    //获取计算的材料类型,并根据状态排序
    fun getMaterialListByCultivateItemId(itemId: Int) =
        (itemsMaterialsMap[itemId] ?: listOf()).sortedWith(
            compareBy(
                { it.tempStatus },
                { it.itemId })
        )

    fun onClickCultivateCardDelete(cultivateEntity: CultivateEntity, name: String) {
        currentSelectedEntityId = cultivateEntity.itemId

        deleteEntityConfirmDialogContent = "确定要从养成计划中删除[${name}]吗?"
        showDeleteEntityConfirmDialog = true
    }

    fun deleteCurrentSelectedEntity() {
        if (currentSelectedEntityId == -1) return
        if (currentSelectedProjectId == -1) return

        viewModelScope.launchIO {

            cultivateEntityDao.deleteEntityByItemIdAndProjectId(
                itemId = currentSelectedEntityId,
                projectId = currentSelectedProjectId
            )

            "已删除所选项".warnNotify(false)

            entityConfirmDialogDismissRequest()
        }
    }

    private suspend fun updateMaterialOverallGroup() {
        //如果当前没有选中养成计划就直接返回
        if (currentSelectedProjectId == -1) return

        //五星突破水晶id
        val gemIds = Materials.AvatarPromotionGemSimpleItems

        var currentMaterialBaseInfoGroup = mutableListOf<MaterialBaseInfo>()

        val tempPairList = cultivateEntityMapList.toList().sortedBy {
            if (cultivateProjectSortByEntityType) {
                it.first.type.ordinal.toLong()
            } else {
                it.first.addTime
            }
        }

        val tempOverallMaterialBaseInfoGroupList = mutableListOf<List<MaterialBaseInfo>>()

        val tempOverallEntityBaseInfoMap =  mutableMapOf<Int, MutableList<EntityBaseInfo>>()


        /*
        * 将所有养成实体的材料列表根据itemId分类
        *
        * */
        tempPairList.asSequence().map { (cultivateEntity, _) ->
            itemsMaterialsMap[-cultivateEntity.itemId] ?: listOf()
        }.flatten()
            .groupBy { it.itemId }
            .map { map ->
                val material = getMaterialData(map.key)
                //最小的缺少材料的数量
                val minLackCountMaterials = map.value.minBy { it.lackCount }

                val lackCount = minLackCountMaterials.lackCount
                val count = minLackCountMaterials.count

                //所需的材料数量
                val totalMaterialsCount = map.value.sumOf { it.count }

                /*
                * 获取材料可用数量
                * lackCount小于0代表这个材料多余所需数量
                * 大于0代表持有数量少于所需数量
                * */
                val availableCount = if (lackCount < 0) {
                    abs(lackCount - count)
                } else {
                    count - lackCount
                }

                MaterialBaseInfo(
                    material = material,
                    count = totalMaterialsCount,
                    availableCount = availableCount
                )
            }.sortedBy { it.material.Id }.toList()
            .forEach { materialBaseInfo ->
                if (currentMaterialBaseInfoGroup.isEmpty()) {
                    currentMaterialBaseInfoGroup += materialBaseInfo
                } else {
                    val first = currentMaterialBaseInfoGroup.first().material
                    val last = currentMaterialBaseInfoGroup.last().material
                    val material = materialBaseInfo.material
                    //智识之冕(104319)单独进行判断,其次添加至组别的材料只能为五星以下的材料,如果为五星材料则需要判断第一个添加的材料是否是二星
                    if ((material.RankLevel < 5 || first.RankLevel == 2 || gemIds.contains(material.Id)) && last.Id + 1 == material.Id && last.RankLevel + 1 == material.RankLevel && material.Id != 104319) {
                        currentMaterialBaseInfoGroup += materialBaseInfo
                    } else {
                        tempOverallMaterialBaseInfoGroupList += currentMaterialBaseInfoGroup
                        currentMaterialBaseInfoGroup = mutableListOf(materialBaseInfo)
                    }
                }
            }
        if (currentMaterialBaseInfoGroup.isNotEmpty()) {
            tempOverallMaterialBaseInfoGroupList += currentMaterialBaseInfoGroup
        }

        //养成实体所需的材料分类后再将对应材料的id取出
        val materialGroupListItemIdsList = tempOverallMaterialBaseInfoGroupList.map {
            it.map { baseInfo ->
                baseInfo.material.Id
            }.toSet()
        }

        tempPairList.forEach { (cultivateEntity, _) ->

            //获取养成材料总览材料列表id集合
            val itemIds =
                (itemsMaterialsMap[-cultivateEntity.itemId] ?: return@forEach).fastMap { it.itemId }

            //遍历set判断养成实体是否需要对应的材料
            materialGroupListItemIdsList.forEach { baseInfoMaterialIdsSet ->
                //判断养成材料id集合与分组材料集合是否有交际
                val add = itemIds.any { it in baseInfoMaterialIdsSet }

                var list = tempOverallEntityBaseInfoMap[baseInfoMaterialIdsSet.first()]

                if (list == null) {
                    list = mutableListOf()
                    tempOverallEntityBaseInfoMap[baseInfoMaterialIdsSet.first()] = list
                }

                if (add) {
                    if (cultivateEntity.type == CultivateEntityType.Avatar) {
                        val avatar = getAvatarData(cultivateEntity.itemId) ?: return
                        list.add(
                            EntityBaseInfo(
                                id = avatar.id,
                                name = avatar.name,
                                iconUrl = avatar.iconUrl,
                                star = avatar.quality
                            )
                        )
                    }

                    if (cultivateEntity.type == CultivateEntityType.Weapon) {
                        val weapon = getWeaponData(cultivateEntity.itemId) ?: return
                        list.add(
                            EntityBaseInfo(
                                id = weapon.id,
                                name = weapon.name,
                                iconUrl = weapon.iconUrl,
                                star = weapon.rankLevel
                            )
                        )
                    }
                }
            }
        }

        /*
        * 分组数据排序
        * 长度最长并且未完成数量最多的集合排到最前面
        * */
        tempOverallMaterialBaseInfoGroupList.sortWith(
            compareBy<List<MaterialBaseInfo>> {
                it.all { baseInfo -> baseInfo.lackCount <= 0 }
            }.thenByDescending { it.size }
                .thenBy { it.count { baseInfo -> baseInfo.lackCount <= 0 } }
        )

        val tempOverallMaterialBaseInfoGroupListFlatten =
            tempOverallMaterialBaseInfoGroupList.flatten()
                .sortedBy {
                    it.lackCount <= 1
                }

        /*
        * 分组数据实体排序
        * 根据星级排序,星级最大的在最前方
        * */
        overallEntityBaseInfoMap.forEach { (_, u) ->
            u.sortByDescending { it.star }
        }

        /*
        * 更新数据集与加载状态
        * */
        withContextMain {
            overallMaterialBaseInfoGroupList.clear()
            overallMaterialBaseInfoGroupListFlatten.clear()
            overallEntityBaseInfoMap.clear()
            entityCultivateItemsPairList.clear()

            overallMaterialBaseInfoGroupList += tempOverallMaterialBaseInfoGroupList
            overallMaterialBaseInfoGroupListFlatten += tempOverallMaterialBaseInfoGroupListFlatten
            overallEntityBaseInfoMap += tempOverallEntityBaseInfoMap
            entityCultivateItemsPairList += tempPairList

            resinStatisticsResult = ResinStatisticsCalculator.calculate(
                tempOverallMaterialBaseInfoGroupListFlatten.map { it.material to it.lackCount }
            )

            loadingState = LoadingState.Success
        }
    }

    /*
    * 当提交材料item更新队列时
    * 批量更新点击的材料列表,频繁更新数据库频繁造成UI更新消耗大量性能,因此一段时间内的状态更新会汇总后批量更新
    * */
    fun onEmitMaterialItemUpdateQueue(
        cultivateItems: CultivateItems,
        cultivateItemMaterials: List<CultivateItemMaterials>
    ) {
        viewModelScope.launchIO {
            //状态取反
            cultivateItemMaterials.groupBy {
                it.tempStatus
            }.forEach { (status, list) ->
                cultivateItemMaterialsDao.updateStatusByMaterialIds(
                    status = status,
                    projectId = cultivateItems.projectId,
                    cultivateItemId = cultivateItems.itemId,
                    materialIds = list.map { it.itemId }
                )
            }
        }
    }

    /*
    * 通过材料id获取使用此材料的实体列表
    * materialId = 材料分组中第一个材料
    * */
    fun getOverallEntityBaseInfoListByMaterialId(materialId: Int) =
        overallEntityBaseInfoMap[materialId] ?: listOf()
}