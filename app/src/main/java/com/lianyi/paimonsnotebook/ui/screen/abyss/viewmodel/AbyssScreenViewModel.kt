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
import com.lianyi.paimonsnotebook.common.database.user.util.AccountHelper
import com.lianyi.paimonsnotebook.common.extension.intent.setComponentName
import com.lianyi.paimonsnotebook.common.extension.string.errorNotify
import com.lianyi.paimonsnotebook.common.service.geetest.CardVerificationService
import com.lianyi.paimonsnotebook.common.util.enums.LoadingState
import com.lianyi.paimonsnotebook.common.web.hutao.genshin.common.service.AvatarService
import com.lianyi.paimonsnotebook.common.web.hutao.genshin.common.service.MonsterService
import com.lianyi.paimonsnotebook.common.web.hutao.statistics.HutaoAvatarFloorRateData
import com.lianyi.paimonsnotebook.common.web.hutao.statistics.HutaoOverviewData
import com.lianyi.paimonsnotebook.common.web.hutao.statistics.HutaoStatisticsClient
import com.lianyi.paimonsnotebook.common.web.hutao.statistics.HutaoTeamCombinationData
import com.lianyi.paimonsnotebook.common.view.HoyolabWebActivity
import com.lianyi.paimonsnotebook.common.web.hoyolab.takumi.binding.UserGameRoleData
import com.lianyi.paimonsnotebook.common.web.hoyolab.takumi.game_record.GameRecordClient
import com.lianyi.paimonsnotebook.common.web.hoyolab.takumi.game_record.abyss.SpiralAbyssData
import com.lianyi.paimonsnotebook.common.web.hutao.genshin.avatar.AvatarData
import com.lianyi.paimonsnotebook.common.web.hutao.genshin.monster.MonsterData
import com.lianyi.paimonsnotebook.ui.screen.home.util.HomeHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AbyssScreenViewModel : ViewModel() {

    //0本期 1上期 2总览 3出场率 4使用率 5配队
    var currentPageIndex by mutableIntStateOf(0)

    val tabs = arrayOf(
        "本期", "上期", "全服总览", "出场率", "使用率", "配队"
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
            }
        }
    }

    private fun setLoadingState(page: Int, state: LoadingState) {
        when (page) {
            2 -> overviewLoadingState = state
            3 -> appearanceRateLoadingState = state
            4 -> usageRateLoadingState = state
            5 -> teamCombinationLoadingState = state
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
                val data =
                    result.data.copy(floors = result.data.floors.sortedByDescending { it.index })

                val resultState = if (data.floors.isEmpty()) {
                    LoadingState.Empty
                } else {
                    LoadingState.Success
                }

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

                        if (retry.success) {
                            val data =
                                retry.data.copy(floors = retry.data.floors.sortedByDescending { it.index })

                            finalState = if (data.floors.isEmpty()) LoadingState.Empty else LoadingState.Success

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
        }
    }

    fun getAvatarFromMetadata(avatarId: Int) = avatarMap[avatarId]

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
