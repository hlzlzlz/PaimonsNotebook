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
import com.lianyi.paimonsnotebook.ui.screen.home.util.HomeHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RoleCombatScreenViewModel : ViewModel() {

    //0剧诗 1幽境危战
    var currentPageIndex by mutableIntStateOf(0)
        private set

    var roleCombatData by mutableStateOf<RoleCombatData?>(null)
    var roleCombatLoadingState by mutableStateOf(LoadingState.Loading)

    var hardChallengeData by mutableStateOf<HardChallengeData?>(null)
    var hardChallengeLoadingState by mutableStateOf(LoadingState.Loading)

    private val gameRecordClient = GameRecordClient()

    private var currentUser by mutableStateOf<User?>(null)
    var currentGameRole by mutableStateOf<UserGameRoleData.Role?>(null)
        private set

    var showUserGameRoleDialog by mutableStateOf(false)
        private set

    var showConfirmDialog by mutableStateOf(false)
        private set

    val tabs = arrayOf("幻想真境剧诗", "幽境危战")

    init {
        //Compose状态的写入必须在主线程,仅网络请求切换IO
        viewModelScope.launch {
            AccountHelper.selectedUserFlow.collect {
                currentUser = it
                currentGameRole = it?.getSelectedGameRole()
                load(currentPageIndex)
            }
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
        if (currentUser == null || currentGameRole == null) {
            setLoadingState(page, LoadingState.Error)
            return
        }

        //已加载数据的页面不重复加载
        when (page) {
            0 -> if (roleCombatData != null) return
            1 -> if (hardChallengeData != null) return
        }

        setLoadingState(page, LoadingState.Loading)

        viewModelScope.launch {
            val userAndUid = UserAndUid(
                userEntity = currentUser!!.userEntity,
                playerUid = PlayerUid.fromGameRole(currentGameRole!!)
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
                                currentUser!!.userEntity, CardVerificationService.PATH_ROLE_COMBAT
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
                                currentUser!!.userEntity, CardVerificationService.PATH_HARD_CHALLENGE
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
        }
    }

    private fun setLoadingState(page: Int, state: LoadingState) {
        when (page) {
            0 -> roleCombatLoadingState = state
            1 -> hardChallengeLoadingState = state
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
