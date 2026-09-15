package com.lianyi.paimonsnotebook.ui.screen.sign_in_status.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lianyi.paimonsnotebook.common.database.user.util.AccountHelper
import com.lianyi.paimonsnotebook.common.data.hoyolab.PlayerUid
import com.lianyi.paimonsnotebook.common.data.hoyolab.user.UserAndUid
import com.lianyi.paimonsnotebook.common.util.enums.LoadingState
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

    private val signInClient = SignInClient()

    init {
        viewModelScope.launch {
            AccountHelper.selectedUserFlow.collect { user ->
                val role = user?.getSelectedGameRole()

                if (user == null || role == null) {
                    loadingState = LoadingState.Error
                    return@collect
                }

                gameUid = role.game_uid

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
                    return@collect
                }

                val (info, resign, reward) = result

                if (!info.success) {
                    loadingState = LoadingState.Error
                    return@collect
                }

                signInInfo = info.data
                resignInfo = if (resign.success) resign.data else null
                awards = if (reward.success) reward.data?.awards.orEmpty() else listOf()

                loadingState = LoadingState.Success
            }
        }
    }
}
