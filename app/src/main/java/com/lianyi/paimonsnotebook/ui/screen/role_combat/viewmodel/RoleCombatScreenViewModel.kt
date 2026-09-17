package com.lianyi.paimonsnotebook.ui.screen.role_combat.viewmodel

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
import com.lianyi.paimonsnotebook.common.view.HoyolabWebActivity
import com.lianyi.paimonsnotebook.common.web.hoyolab.takumi.binding.UserGameRoleData
import com.lianyi.paimonsnotebook.common.web.hoyolab.takumi.game_record.GameRecordClient
import com.lianyi.paimonsnotebook.common.web.hoyolab.takumi.game_record.hard_challenge.HardChallengeData
import com.lianyi.paimonsnotebook.common.web.hoyolab.takumi.game_record.role_combat.RoleCombatData
import com.lianyi.paimonsnotebook.common.web.hutao.genshin.avatar.AvatarData
import com.lianyi.paimonsnotebook.common.web.hutao.genshin.common.service.AvatarService
import com.lianyi.paimonsnotebook.common.web.hutao.statistics.HutaoRoleCombatStatisticsData
import com.lianyi.paimonsnotebook.common.web.hutao.statistics.HutaoStatisticsClient
import com.lianyi.paimonsnotebook.ui.screen.home.util.HomeHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RoleCombatScreenViewModel : ViewModel() {

    //0剧诗 1幽境危战 2全服统计
    var currentPageIndex by mutableIntStateOf(0)
        private set

    var roleCombatData by mutableStateOf<RoleCombatData?>(null)
    var roleCombatLoadingState by mutableStateOf(LoadingState.Loading)

    var hardChallengeData by mutableStateOf<HardChallengeData?>(null)
    var hardChallengeLoadingState by mutableStateOf(LoadingState.Loading)

    //胡桃全服剧诗统计,不依赖登录用户
    var statistics by mutableStateOf<HutaoRoleCombatStatisticsData?>(null)
    var statisticsLoadingState by mutableStateOf(LoadingState.Loading)

    //全服统计本期false/上期true
    var statisticsLastPeriod by mutableStateOf(false)
        private set

    private val gameRecordClient = GameRecordClient()
    private val statisticsClient = HutaoStatisticsClient()

    private val avatarMap = mutableMapOf<Int, AvatarData>()

    private var currentUser by mutableStateOf<User?>(null)
    var currentGameRole by mutableStateOf<UserGameRoleData.Role?>(null)
        private set

    var showUserGameRoleDialog by mutableStateOf(false)
        private set

    var showConfirmDialog by mutableStateOf(false)
        private set

    val tabs = arrayOf("幻想真境剧诗", "幽境危战", "全服统计")

    init {
        //Compose状态的写入必须在主线程,仅网络请求切换IO
        viewModelScope.launch {
            //元数据缺失时AvatarService构造会抛异常,不能让整个collect链路中断
            withContext(Dispatchers.IO) {
                runCatching {
                    avatarMap += AvatarService {}.avatarList.associateBy { it.id }
                }.onFailure { it.printStackTrace() }
            }

            AccountHelper.selectedUserFlow.collect {
                currentUser = it
                currentGameRole = it?.getSelectedGameRole()
                load(currentPageIndex)
            }
        }
    }

    fun getAvatarFromMetadata(avatarId: Int) = avatarMap[avatarId]

    //切换全服统计的本期/上期并重新加载
    fun toggleStatisticsPeriod() {
        statisticsLastPeriod = !statisticsLastPeriod
        statistics = null

        if (currentPageIndex == 2) {
            load(2)
        }
    }

    fun showUserGameRoleDialog() {
        showUserGameRoleDialog = true
    }

    fun dismissUserGameRoleDialog() {
        showUserGameRoleDialog = false
    }

    fun dismissConfirmDialog() {
        showConfirmDialog = false
    }

    fun onChangeGameRole(user: User, role: UserGameRoleData.Role) {
        showUserGameRoleDialog = false

        currentUser = user
        currentGameRole = role

        load(currentPageIndex)
    }

    fun onPageIndexChange(page: Int) {
        currentPageIndex = page
        load(page)
    }

    private fun load(page: Int) {
        //全服统计页不依赖登录用户
        if (page != 2 && (currentUser == null || currentGameRole == null)) {
            setLoadingState(page, LoadingState.Error)
            return
        }

        //已加载数据的页面不重复加载
        when (page) {
            0 -> if (roleCombatData != null) return
            1 -> if (hardChallengeData != null) return
            2 -> if (statistics != null) return
        }

        setLoadingState(page, LoadingState.Loading)

        viewModelScope.launch {
            //整体try/catch:本页会调用CardVerificationService.verify(内含withTimeout(180s)),
            //用户超时未完成滑块会抛TimeoutCancellationException;
            //currentUser/currentGameRole为空断言、网络与解析异常也都在此收敛,
            //避免界面永久停留在Loading且没有任何提示
            try {
            if (page == 2) {
                val response = withContext(Dispatchers.IO) {
                    statisticsClient.getRoleCombatStatistics(statisticsLastPeriod)
                }

                if (response?.retcode == 0) {
                    statistics = response.data

                    statisticsLoadingState =
                        if (response.data == null || response.data.BackupAvatarRates.isEmpty())
                            LoadingState.Empty
                        else
                            LoadingState.Success
                } else {
                    statisticsLoadingState = LoadingState.Error
                    "获取全服剧诗统计失败:${response?.message ?: "网络错误"}".errorNotify()
                }

                return@launch
            }

            //用户或角色可能在页面停留期间被删除,此处不再用!!断言
            val user = currentUser
            val gameRole = currentGameRole

            if (user == null || gameRole == null) {
                setLoadingState(page, LoadingState.Error)
                return@launch
            }

            val userAndUid = UserAndUid(
                userEntity = user.userEntity,
                playerUid = PlayerUid.fromGameRole(gameRole)
            )

            when (page) {
                0 -> {
                    val result = withContext(Dispatchers.IO) {
                        gameRecordClient.getRoleCombatData(userAndUid)
                    }
                    if (result.success) {
                        roleCombatData = result.data
                        val entry = result.data.data?.firstOrNull()
                        roleCombatLoadingState =
                            if (entry == null || !entry.has_data) LoadingState.Empty else LoadingState.Success
                    } else {
                        //1034风控:App内滑块验证后自动重试
                        if (result.validate) {
                            val challenge = CardVerificationService.verify(
                                user.userEntity, CardVerificationService.PATH_ROLE_COMBAT
                            )

                            if (challenge != null) {
                                val retry = withContext(Dispatchers.IO) {
                                    gameRecordClient.getRoleCombatData(userAndUid, challenge)
                                }

                                if (retry.success) {
                                    roleCombatData = retry.data
                                    val entry = retry.data.data?.firstOrNull()
                                    roleCombatLoadingState =
                                        if (entry == null || !entry.has_data) LoadingState.Empty else LoadingState.Success
                                    return@launch
                                }
                            }

                            roleCombatLoadingState = LoadingState.Error
                            showConfirmDialog = true
                        } else {
                            roleCombatLoadingState = LoadingState.Error
                            "获取剧诗数据失败:${result.message}".errorNotify()
                        }
                    }
                }

                1 -> {
                    val result = withContext(Dispatchers.IO) {
                        gameRecordClient.getHardChallengeData(userAndUid)
                    }
                    if (result.success) {
                        hardChallengeData = result.data
                        val entry = result.data.data?.firstOrNull()
                        hardChallengeLoadingState =
                            if (entry == null || entry.single?.has_data != true) LoadingState.Empty else LoadingState.Success
                    } else {
                        //1034风控:App内滑块验证后自动重试
                        if (result.validate) {
                            val challenge = CardVerificationService.verify(
                                user.userEntity, CardVerificationService.PATH_HARD_CHALLENGE
                            )

                            if (challenge != null) {
                                val retry = withContext(Dispatchers.IO) {
                                    gameRecordClient.getHardChallengeData(userAndUid, challenge)
                                }

                                if (retry.success) {
                                    hardChallengeData = retry.data
                                    val entry = retry.data.data?.firstOrNull()
                                    hardChallengeLoadingState =
                                        if (entry == null || entry.single?.has_data != true) LoadingState.Empty else LoadingState.Success
                                    return@launch
                                }
                            }

                            hardChallengeLoadingState = LoadingState.Error
                            showConfirmDialog = true
                        } else {
                            hardChallengeLoadingState = LoadingState.Error
                            "获取幽境危战数据失败:${result.message}".errorNotify()
                        }
                    }
                }
            }
            } catch (e: Exception) {
                e.printStackTrace()

                //把当前页从Loading态里解放出来,并给出提示
                setLoadingState(page, LoadingState.Error)
                "获取战斗记录失败:${e.message ?: "未知错误"}".errorNotify()
            }
        }
    }

    private fun setLoadingState(page: Int, state: LoadingState) {
        when (page) {
            0 -> roleCombatLoadingState = state
            1 -> hardChallengeLoadingState = state
            2 -> statisticsLoadingState = state
        }
    }

    fun goValidateScreen() {
        showConfirmDialog = false

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
