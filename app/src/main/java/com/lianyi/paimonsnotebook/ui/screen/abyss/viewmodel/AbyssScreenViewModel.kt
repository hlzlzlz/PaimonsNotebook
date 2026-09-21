package com.lianyi.paimonsnotebook.ui.screen.abyss.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lianyi.paimonsnotebook.common.data.hoyolab.PlayerUid
import com.lianyi.paimonsnotebook.common.data.hoyolab.user.User
import com.lianyi.paimonsnotebook.common.data.hoyolab.user.UserAndUid
import com.lianyi.paimonsnotebook.common.database.PaimonsNotebookDatabase
import com.lianyi.paimonsnotebook.common.database.user.util.AccountHelper
import com.lianyi.paimonsnotebook.common.extension.scope.launchSafeIO
import com.lianyi.paimonsnotebook.common.util.metadata.genshin.abyss.AbyssSnapshotMapper
import com.lianyi.paimonsnotebook.common.extension.intent.setComponentName
import com.lianyi.paimonsnotebook.common.extension.string.errorNotify
import com.lianyi.paimonsnotebook.common.service.geetest.CardVerificationService
import com.lianyi.paimonsnotebook.common.util.enums.LoadingState
import com.lianyi.paimonsnotebook.common.web.hutao.genshin.common.service.AvatarService
import com.lianyi.paimonsnotebook.common.web.hutao.genshin.common.service.MonsterService
import com.lianyi.paimonsnotebook.common.web.hutao.genshin.common.service.WeaponService
import com.lianyi.paimonsnotebook.common.web.hutao.statistics.HutaoAvatarCollocationData
import com.lianyi.paimonsnotebook.common.web.hutao.statistics.HutaoAvatarFloorRateData
import com.lianyi.paimonsnotebook.common.web.hutao.statistics.HutaoHoldingRateData
import com.lianyi.paimonsnotebook.common.web.hutao.statistics.HutaoHoldingRateEntry
import com.lianyi.paimonsnotebook.common.web.hutao.statistics.HutaoOverviewData
import com.lianyi.paimonsnotebook.common.web.hutao.statistics.HutaoStatisticsClient
import com.lianyi.paimonsnotebook.common.web.hutao.statistics.HutaoTeamCombinationData
import com.lianyi.paimonsnotebook.common.web.hutao.statistics.HutaoWeaponCollocationData
import com.lianyi.paimonsnotebook.common.view.HoyolabWebActivity
import com.lianyi.paimonsnotebook.common.web.hoyolab.takumi.binding.UserGameRoleData
import com.lianyi.paimonsnotebook.common.web.hoyolab.takumi.game_record.GameRecordClient
import com.lianyi.paimonsnotebook.common.web.hoyolab.takumi.game_record.abyss.SpiralAbyssData
import com.lianyi.paimonsnotebook.common.web.hutao.genshin.avatar.AvatarData
import com.lianyi.paimonsnotebook.common.web.hutao.genshin.monster.MonsterData
import com.lianyi.paimonsnotebook.common.web.hutao.genshin.weapon.WeaponData
import com.lianyi.paimonsnotebook.ui.screen.home.util.HomeHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AbyssScreenViewModel : ViewModel() {

    //0本期 1上期 2总览 3出场率 4使用率 5配队 6持有率 7角色配装 8武器装备
    var currentPageIndex by mutableIntStateOf(0)

    val tabs = arrayOf(
        "本期", "上期", "全服总览", "出场率", "使用率", "配队", "持有率", "角色配装", "武器装备"
    )

    //本期与上期深渊记录
    var currentAbyssRecord by mutableStateOf<SpiralAbyssData?>(null)
    var previousAbyssRecord by mutableStateOf<SpiralAbyssData?>(null)

    //本期与上期深渊记录 加载状态
    var currentAbyssRecordLoadingState by mutableStateOf(LoadingState.Loading)
    var previousAbyssRecordLoadingState by mutableStateOf(LoadingState.Loading)

    //全服数据库 本期false/上期true
    var lastPeriod by mutableStateOf(false)
        private set

    var overview by mutableStateOf<HutaoOverviewData?>(null)
    var overviewLoadingState by mutableStateOf(LoadingState.Loading)

    var appearanceRate by mutableStateOf<List<HutaoAvatarFloorRateData>?>(null)
    var appearanceRateLoadingState by mutableStateOf(LoadingState.Loading)

    var usageRate by mutableStateOf<List<HutaoAvatarFloorRateData>?>(null)
    var usageRateLoadingState by mutableStateOf(LoadingState.Loading)

    var teamCombination by mutableStateOf<List<HutaoTeamCombinationData>?>(null)
    var teamCombinationLoadingState by mutableStateOf(LoadingState.Loading)

    //持有率不受本期/上期切换影响,始终显示本期与上期的环比
    var holdingRate by mutableStateOf<List<HutaoHoldingRateEntry>?>(null)
    var holdingRateLoadingState by mutableStateOf(LoadingState.Loading)

    //角色配装
    var avatarCollocation by mutableStateOf<List<HutaoAvatarCollocationData>?>(null)
    var avatarCollocationLoadingState by mutableStateOf(LoadingState.Loading)

    //武器配队
    var weaponCollocation by mutableStateOf<List<HutaoWeaponCollocationData>?>(null)
    var weaponCollocationLoadingState by mutableStateOf(LoadingState.Loading)

    private val gameRecordClient = GameRecordClient()
    private val statisticsClient = HutaoStatisticsClient()

    private var currentUser by mutableStateOf<User?>(null)
    var currentGameRole by mutableStateOf<UserGameRoleData.Role?>(null)
        private set

    // 1当期 2上期
    private val scheduleType = arrayOf(
        "1", "2"
    )

    private val avatarMap = mutableMapOf<Int, AvatarData>()

    //武器Id -> 武器,供"角色配装/武器配队"解析名称与图标
    private val weaponMap = mutableMapOf<Int, WeaponData>()

    private val monsterMap = mutableMapOf<String, MonsterData>()

    private var metadataLoaded = false

    init {
        //Compose状态的写入必须在主线程,仅文件/网络请求切换IO
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                avatarMap += AvatarService {
                    setErrorStates()
                }.avatarList.associateBy {
                    it.id
                }

                //武器元数据是本页新增依赖,取不到时只影响两个新标签页
                weaponMap += WeaponService {
                }.weaponMap

                monsterMap += MonsterService {
                    setErrorStates()
                }.monsterList.associateBy {
                    it.name
                }

                metadataLoaded = true
            }

            AccountHelper.selectedUserFlow.collect {
                currentUser = it
                currentGameRole = it?.getSelectedGameRole()

                load(currentPageIndex)
            }
        }
    }

    private fun setErrorStates() {
        currentAbyssRecordLoadingState = LoadingState.Error
        previousAbyssRecordLoadingState = LoadingState.Error
        overviewLoadingState = LoadingState.Error
    }

    var showUserGameRoleDialog by mutableStateOf(false)

    var showConfirmDialog by mutableStateOf(false)

    fun showUserGameRoleDialog() {
        showUserGameRoleDialog = true
    }

    fun dismissUserGameRoleDialog() {
        showUserGameRoleDialog = false
    }

    fun showConfirmDialog() {
        showConfirmDialog = true
    }

    fun dismissConfirmDialog() {
        showConfirmDialog = false
    }

    fun onPageIndexChange(value: Int) {
        currentPageIndex = value
        load(value)
    }

    //切换全服数据的本期与上期,清空已加载的比率数据重新加载
    fun togglePeriod() {
        lastPeriod = !lastPeriod

        appearanceRate = null
        usageRate = null
        teamCombination = null
        avatarCollocation = null
        weaponCollocation = null

        load(currentPageIndex)
    }

    private fun load(page: Int) {
        //深渊记录页
        if (page <= 1) {
            when (page) {
                0 -> if (currentAbyssRecord == null) setAbyssRecord(page)
                1 -> if (previousAbyssRecord == null) setAbyssRecord(page)
            }
            return
        }

        //全服数据库页
        if (!metadataLoaded) {
            return
        }

        when (page) {
            2 -> if (overview != null) return
            3 -> if (appearanceRate != null) return
            4 -> if (usageRate != null) return
            5 -> if (teamCombination != null) return
            6 -> if (holdingRate != null) return
            7 -> if (avatarCollocation != null) return
            8 -> if (weaponCollocation != null) return
        }

        setLoadingState(page, LoadingState.Loading)

        viewModelScope.launch {
            val last = lastPeriod

            when (page) {
                2 -> {
                    val response = withContext(Dispatchers.IO) {
                        statisticsClient.getOverview(last)
                    }

                    if (response?.retcode == 0) {
                        overview = response.data
                        overviewLoadingState =
                            if (response.data == null) LoadingState.Empty else LoadingState.Success
                    } else {
                        overviewLoadingState = LoadingState.Error
                        "获取深渊数据库失败:${response?.message ?: "网络错误"}".errorNotify()
                    }
                }

                3 -> {
                    val response = withContext(Dispatchers.IO) {
                        statisticsClient.getAvatarAppearanceRate(last)
                    }

                    if (response?.retcode == 0) {
                        appearanceRate = response.data
                        appearanceRateLoadingState =
                            if (response.data.isNullOrEmpty()) LoadingState.Empty else LoadingState.Success
                    } else {
                        appearanceRateLoadingState = LoadingState.Error
                        "获取出场率失败:${response?.message ?: "网络错误"}".errorNotify()
                    }
                }

                4 -> {
                    val response = withContext(Dispatchers.IO) {
                        statisticsClient.getAvatarUsageRate(last)
                    }

                    if (response?.retcode == 0) {
                        usageRate = response.data
                        usageRateLoadingState =
                            if (response.data.isNullOrEmpty()) LoadingState.Empty else LoadingState.Success
                    } else {
                        usageRateLoadingState = LoadingState.Error
                        "获取使用率失败:${response?.message ?: "网络错误"}".errorNotify()
                    }
                }

                5 -> {
                    val response = withContext(Dispatchers.IO) {
                        statisticsClient.getTeamCombination(last)
                    }

                    if (response?.retcode == 0) {
                        teamCombination = response.data
                        teamCombinationLoadingState =
                            if (response.data.isNullOrEmpty()) LoadingState.Empty else LoadingState.Success
                    } else {
                        teamCombinationLoadingState = LoadingState.Error
                        "获取配队数据失败:${response?.message ?: "网络错误"}".errorNotify()
                    }
                }

                6 -> {
                    //同时拉取本期与上期用于计算环比
                    val responses = withContext(Dispatchers.IO) {
                        statisticsClient.getHoldingRate(false) to
                                statisticsClient.getHoldingRate(true)
                    }

                    val current = responses.first
                    val previous = responses.second

                    if (current?.retcode == 0) {
                        val joined = joinHoldingRate(
                            current = current.data.orEmpty(),
                            previous = previous?.data
                        )

                        holdingRate = joined
                        holdingRateLoadingState =
                            if (joined.isEmpty()) LoadingState.Empty else LoadingState.Success
                    } else {
                        holdingRateLoadingState = LoadingState.Error
                        "获取持有率失败:${current?.message ?: "网络错误"}".errorNotify()
                    }
                }

                7 -> {
                    val response = withContext(Dispatchers.IO) {
                        statisticsClient.getAvatarCollocation(last)
                    }

                    if (response?.retcode == 0) {
                        avatarCollocation = response.data
                        avatarCollocationLoadingState =
                            if (response.data.isNullOrEmpty()) LoadingState.Empty else LoadingState.Success
                    } else {
                        avatarCollocationLoadingState = LoadingState.Error
                        "获取角色配装失败:${response?.message ?: "网络错误"}".errorNotify()
                    }
                }

                8 -> {
                    val response = withContext(Dispatchers.IO) {
                        statisticsClient.getWeaponCollocation(last)
                    }

                    if (response?.retcode == 0) {
                        weaponCollocation = response.data
                        weaponCollocationLoadingState =
                            if (response.data.isNullOrEmpty()) LoadingState.Empty else LoadingState.Success
                    } else {
                        weaponCollocationLoadingState = LoadingState.Error
                        "获取武器配队失败:${response?.message ?: "网络错误"}".errorNotify()
                    }
                }
            }
        }
    }

    //把本期与上期的持有率按角色Id连接,计算总持有率与各命座持有率的环比差值
    private fun joinHoldingRate(
        current: List<HutaoHoldingRateData>,
        previous: List<HutaoHoldingRateData>?
    ): List<HutaoHoldingRateEntry> {
        val previousMap = previous?.associateBy { it.AvatarId }

        return current.map { entry ->
            val last = previousMap?.get(entry.AvatarId)

            HutaoHoldingRateEntry(
                AvatarId = entry.AvatarId,
                HoldingRate = entry.HoldingRate,
                HoldingDelta = last?.let { entry.HoldingRate - it.HoldingRate },
                Constellations = entry.Constellations,
                ConstellationDeltas = entry.Constellations.map { constellation ->
                    val lastConstellation =
                        last?.Constellations?.firstOrNull { it.Item == constellation.Item }

                    if (lastConstellation == null) null
                    else constellation.Rate - lastConstellation.Rate
                }
            )
        }.sortedByDescending { it.HoldingRate }
    }

    //按页设置对应的加载状态(0/1=深渊记录,2~8=统计页)
    private fun setLoadingState(page: Int, state: LoadingState) {
        when (page) {
            0 -> currentAbyssRecordLoadingState = state
            1 -> previousAbyssRecordLoadingState = state
            2 -> overviewLoadingState = state
            3 -> appearanceRateLoadingState = state
            4 -> usageRateLoadingState = state
            5 -> teamCombinationLoadingState = state
            6 -> holdingRateLoadingState = state
            7 -> avatarCollocationLoadingState = state
            8 -> weaponCollocationLoadingState = state
        }
    }

    private fun setAbyssRecord(pageIndex: Int) {
        val state = if (currentUser == null || currentGameRole == null) {
            LoadingState.Error
        } else {
            LoadingState.Loading
        }

        when (pageIndex) {
            0 -> {
                currentAbyssRecordLoadingState = state
            }

            1 -> {
                previousAbyssRecordLoadingState = state
            }
        }

        if (state == LoadingState.Error) return

        viewModelScope.launch {
            /*
            * 整体try/catch:本方法内的CardVerificationService.verify带withTimeout(180s),
            * 用户触发1034风控后不完成滑块会抛TimeoutCancellationException,
            * 打断协程导致下面的LoadingState赋值永不执行 —— 界面永久停在Loading且无提示。
            * RoleCombatScreenViewModel已按同样理由加过兜底,此处补齐。
            * (CancellationException会被协程框架特殊处理,表现为静默挂起而非杀进程)
            * */
            try {
                val userAndUid =
                    UserAndUid(
                        userEntity = currentUser!!.userEntity,
                        playerUid = PlayerUid.fromGameRole(role = currentGameRole!!)
                    )

                val result = withContext(Dispatchers.IO) {
                    gameRecordClient.getSpiralAbyssData(
                        user = userAndUid, scheduleType = scheduleType[pageIndex]
                    )
                }

                if (result.success) {
                    //data声明非空但服务端可能返回null,直接解引用会NPE
                    val resultData = result.data

                    if (resultData == null) {
                        setLoadingState(pageIndex, LoadingState.Error)
                        "深渊数据为空".errorNotify()
                        return@launch
                    }

                    val data =
                        resultData.copy(floors = resultData.floors.sortedByDescending { it.index })

                    val resultState = if (data.floors.isEmpty()) {
                        LoadingState.Empty
                    } else {
                        LoadingState.Success
                    }

                    //落库本期成绩快照(服务端只提供本期/上期,过期即永久丢失)
                    archiveSnapshot(data)

                    when (pageIndex) {
                        0 -> {
                            currentAbyssRecord = data
                            currentAbyssRecordLoadingState = resultState
                        }

                        1 -> {
                            previousAbyssRecord = data
                            previousAbyssRecordLoadingState = resultState
                        }
                    }
                } else {
                    var finalState = LoadingState.Error

                    //1034风控:App内滑块验证后自动重试,失败时回退到网页验证确认框
                    if (result.validate) {
                        val challenge = CardVerificationService.verify(
                            currentUser!!.userEntity, CardVerificationService.PATH_SPIRAL_ABYSS
                        )

                        if (challenge != null) {
                            val retry = withContext(Dispatchers.IO) {
                                gameRecordClient.getSpiralAbyssData(
                                    user = userAndUid, scheduleType = scheduleType[pageIndex], challenge = challenge
                                )
                            }

                            //同样先判空,再解引用
                            val retryData = retry.data

                            if (retry.success && retryData != null) {
                                val data =
                                    retryData.copy(floors = retryData.floors.sortedByDescending { it.index })

                                finalState = if (data.floors.isEmpty()) LoadingState.Empty else LoadingState.Success

                                //重试(风控验证)成功后同样存档
                                archiveSnapshot(data)

                                when (pageIndex) {
                                    0 -> currentAbyssRecord = data
                                    1 -> previousAbyssRecord = data
                                }
                            }
                        }
                    }

                    if (finalState != LoadingState.Success && finalState != LoadingState.Empty) {
                        showConfirmDialog = result.validate
                        if (!result.validate) {
                            "获取深渊数据失败:${result.message}[${result.retcode}]".errorNotify()
                        }
                        finalState = LoadingState.Error
                    }

                    when (pageIndex) {
                        0 -> currentAbyssRecordLoadingState = finalState
                        1 -> previousAbyssRecordLoadingState = finalState
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                //超时/取消:必须让界面脱离Loading,否则永久转圈
                setLoadingState(pageIndex, LoadingState.Error)
                throw e
            } catch (e: Exception) {
                setLoadingState(pageIndex, LoadingState.Error)
                "获取深渊数据时出现异常:${e.message ?: "未知错误"}".errorNotify()
            }
        }
    }

    fun getAvatarFromMetadata(avatarId: Int) = avatarMap[avatarId]
    /*
    * 存档本期成绩
    *
    * 服务端只提供本期(schedule_type=1)与上期(2),更早的期数取不回来 ——
    * 用户某期没打开深渊页,那期成绩就永久丢失。故每次成功拉取即落库。
    *
    * 失败静默:存档是附带价值,不能影响深渊页本身的展示。
    * */
    private fun archiveSnapshot(data: SpiralAbyssData) {
        val uid = currentGameRole?.game_uid ?: return

        launchSafeIO {
            runCatching {
                PaimonsNotebookDatabase.database.abyssSeasonSnapshotDao.upsert(
                    AbyssSnapshotMapper.toSnapshot(
                        data = data,
                        uid = uid,
                        savedAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }    fun getWeaponFromMetadata(weaponId: Int) = weaponMap[weaponId]

    fun getMonsterFromMetadata(monsterName:String) = monsterMap[monsterName]

    fun onChangeGameRole(user: User, role: UserGameRoleData.Role) {
        dismissUserGameRoleDialog()

        currentUser = user
        currentGameRole = role

        load(currentPageIndex)
    }

    fun goValidateScreen() {
        dismissConfirmDialog()

        if (currentUser == null) {
            "当前用户状态异常".errorNotify()
            return
        }

        HomeHelper.goActivityByIntentNewTask {
            setComponentName(HoyolabWebActivity::class.java)
            putExtra("mid", currentUser?.userEntity?.mid ?: "")
        }
    }
}
