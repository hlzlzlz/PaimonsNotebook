package com.lianyi.paimonsnotebook.ui.screen.travelers_diary.viewmodel

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
import com.lianyi.paimonsnotebook.common.util.enums.LoadingState
import com.lianyi.paimonsnotebook.common.view.HoyolabWebActivity
import com.lianyi.paimonsnotebook.common.web.hoyolab.takumi.binding.UserGameRoleData
import com.lianyi.paimonsnotebook.common.web.hoyolab.takumi.game_record.GameRecordClient
import com.lianyi.paimonsnotebook.common.web.hoyolab.takumi.game_record.ledger.LedgerData
import com.lianyi.paimonsnotebook.ui.screen.home.util.HomeHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TravelersDiaryScreenViewModel : ViewModel() {

    var ledgerData by mutableStateOf<LedgerData?>(null)
    var loadingState by mutableStateOf(LoadingState.Loading)

    var currentMonth by mutableIntStateOf(0)
        private set

    private val gameRecordClient = GameRecordClient()

    private var currentUser by mutableStateOf<User?>(null)
    var currentGameRole by mutableStateOf<UserGameRoleData.Role?>(null)
        private set

    var showUserGameRoleDialog by mutableStateOf(false)
        private set

    var showConfirmDialog by mutableStateOf(false)
        private set

    init {
        //Compose状态的写入必须在主线程,仅网络请求切换IO
        viewModelScope.launch {
            AccountHelper.selectedUserFlow.collect {
                currentUser = it
                currentGameRole = it?.getSelectedGameRole()
                loadLedger(currentMonth)
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

        loadLedger(currentMonth)
    }

    //切换月份 0表示当月
    fun onMonthChange(month: Int) {
        if (month == currentMonth && ledgerData != null) {
            return
        }
        currentMonth = month
        loadLedger(month)
    }

    private fun loadLedger(month: Int) {
        if (currentUser == null || currentGameRole == null) {
            loadingState = LoadingState.Error
            return
        }

        loadingState = LoadingState.Loading

        viewModelScope.launch {
            val userAndUid = UserAndUid(
                userEntity = currentUser!!.userEntity,
                playerUid = PlayerUid.fromGameRole(currentGameRole!!)
            )

            val result = withContext(Dispatchers.IO) {
                gameRecordClient.getLedgerMonthInfo(userAndUid, month)
            }

            if (result.success) {
                ledgerData = result.data
                loadingState = LoadingState.Success
            } else {
                loadingState = LoadingState.Error
                showConfirmDialog = result.validate
                if (!result.validate) {
                    "获取旅行者札记失败:${result.message}".errorNotify()
                }
            }
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
