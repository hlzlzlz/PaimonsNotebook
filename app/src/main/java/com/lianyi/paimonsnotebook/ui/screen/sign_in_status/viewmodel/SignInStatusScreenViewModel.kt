package com.lianyi.paimonsnotebook.ui.screen.sign_in_status.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lianyi.paimonsnotebook.common.database.user.util.AccountHelper
import com.lianyi.paimonsnotebook.common.data.hoyolab.PlayerUid
import com.lianyi.paimonsnotebook.common.data.hoyolab.user.User
import com.lianyi.paimonsnotebook.common.data.hoyolab.user.UserAndUid
import com.lianyi.paimonsnotebook.common.util.enums.LoadingState
import com.lianyi.paimonsnotebook.common.web.hoyolab.takumi.binding.UserGameRoleData
import com.lianyi.paimonsnotebook.common.web.hoyolab.takumi.event.bbs_sign_reward.SignInClient
import com.lianyi.paimonsnotebook.common.web.hoyolab.takumi.event.bbs_sign_reward.SignInInfoData
import com.lianyi.paimonsnotebook.common.web.hoyolab.takumi.event.bbs_sign_reward.SignInResignInfoData
import com.lianyi.paimonsnotebook.common.web.hoyolab.takumi.event.bbs_sign_reward.SignInRewardData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/*
* 米游社签到状态页
* 展示当前所选角色的当月签到进度、漏签与补签卡情况
* */
class SignInStatusScreenViewModel : ViewModel() {

    var loadingState by mutableStateOf(LoadingState.Loading)
        private set

    var signInInfo by mutableStateOf<SignInInfoData?>(null)
        private set

    var resignInfo by mutableStateOf<SignInResignInfoData?>(null)
        private set

    //当月奖励列表(luna/home),index即第N天
    var awards by mutableStateOf<List<SignInRewardData.Award>>(listOf())
        private set

    var gameUid by mutableStateOf("")
        private set

    /*
    * 失败原因与"重试是否有意义"。
    *
    * 本页有三种 Error 来源:未登录/未选角色(重试无意义)、网络异常(可重试)、
    * 接口返回失败(可重试)。原先三者都只置 LoadingState.Error 且不区分,
    * 页面又是一片空白 ⇒ 用户既不知道原因,也无处可点。
    * 此处按原因给出文案,并且**只在重试真的可能改变结果时**才提供重试按钮 ——
    * 对"未登录"显示重试按钮是骗人的。
    * */
    var errorMessage by mutableStateOf("")
        private set

    var errorRetryable by mutableStateOf(false)
        private set

    //最近一次用于加载的用户与角色,供重试复用
    private var lastUser: User? = null
    private var lastRole: UserGameRoleData.Role? = null

    private val signInClient = SignInClient()

    init {
        viewModelScope.launch {
            AccountHelper.selectedUserFlow.collect { user ->
                val role = user?.getSelectedGameRole()

                lastUser = user
                lastRole = role

                if (user == null || role == null) {
                    loadingState = LoadingState.Error
                    errorMessage = "未登录或未选择游戏角色"
                    errorRetryable = false
                    return@collect
                }

                gameUid = role.game_uid

                loadData(user, role)
            }
        }
    }

    /*
    * 重试当前页。仅对"网络异常/接口失败"有意义;
    * 未登录时 errorRetryable 为 false,界面不会展示该按钮。
    * */
    fun retry() {
        val user = lastUser
        val role = lastRole

        if (user == null || role == null) {
            errorMessage = "未登录或未选择游戏角色"
            errorRetryable = false
            return
        }

        viewModelScope.launch {
            loadData(user, role)
        }
    }

    private suspend fun loadData(user: User, role: UserGameRoleData.Role) {
        loadingState = LoadingState.Loading

        val result = withContext(Dispatchers.IO) {
            runCatching {
                val userAndUid = UserAndUid(
                    userEntity = user.userEntity,
                    playerUid = PlayerUid.fromGameRole(role)
                )

                Triple(
                    signInClient.getSignInInfo(user.userEntity, userAndUid.playerUid),
                    signInClient.getResignInfo(user.userEntity, userAndUid.playerUid),
                    signInClient.getSignInReward()
                )
            }.getOrNull()
        }

        if (result == null) {
            loadingState = LoadingState.Error
            errorMessage = "网络异常,请稍后重试"
            errorRetryable = true
            return
        }

        val (info, resign, reward) = result

        if (!info.success) {
            loadingState = LoadingState.Error
            errorMessage = "获取签到数据失败:${info.message}"
            errorRetryable = true
            return
        }

        signInInfo = info.data
        resignInfo = if (resign.success) resign.data else null
        awards = if (reward.success) reward.data?.awards.orEmpty() else listOf()

        errorMessage = ""
        errorRetryable = false
        loadingState = LoadingState.Success
    }
}
