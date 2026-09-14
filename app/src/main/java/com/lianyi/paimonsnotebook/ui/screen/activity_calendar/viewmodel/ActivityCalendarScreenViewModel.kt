package com.lianyi.paimonsnotebook.ui.screen.activity_calendar.viewmodel

import androidx.compose.runtime.getValue
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
import com.lianyi.paimonsnotebook.common.web.hoyolab.takumi.game_record.act_calendar.ActCalendarData
import com.lianyi.paimonsnotebook.ui.screen.home.util.HomeHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/*
* 活动与卡池日历页
* */
class ActivityCalendarScreenViewModel : ViewModel() {

    //0当期 1往期
    var tabIndex by mutableStateOf(0)
        private set

    val tabs = arrayOf("当期", "往期")

    var calendarData by mutableStateOf<ActCalendarData?>(null)
        private set

    fun onTabIndexChange(index: Int) {
        tabIndex = index
    }

    //当期卡池(武器列表服务端只返回当期,selected版本列表无武器)
    fun selectedPools(data: ActCalendarData): List<ActCalendarData.CardPool> =
        data.selected_avatar_card_pool_list +
                data.selected_mixed_card_pool_list +
                data.weapon_card_pool_list

    //进行中活动
    fun ongoingActs(data: ActCalendarData): List<ActCalendarData.Act> =
        if (tabIndex == 0) {
            data.act_list.filter { it.status == 2 && !it.is_finished }
        } else {
            data.act_list.filter { it.is_finished || it.status != 2 }
        }

    //即将开始活动
    fun upcomingActs(data: ActCalendarData): List<ActCalendarData.Act> =
        if (tabIndex == 0) {
            data.act_list.filter { it.status == 1 }
        } else {
            emptyList()
        }

    var loadingState by mutableStateOf(LoadingState.Loading)
        private set

    var showConfirmDialog by mutableStateOf(false)
        private set

    private var currentUser by mutableStateOf<User?>(null)
    var currentGameRole by mutableStateOf<UserGameRoleData.Role?>(null)
        private set

    private val gameRecordClient = GameRecordClient()

    init {
        //Compose状态写入在主线程,网络请求切换IO
        viewModelScope.launch {
            AccountHelper.selectedUserFlow.collect {
                currentUser = it
                currentGameRole = it?.getSelectedGameRole()

                load()
            }
        }
    }

    var showUserGameRoleDialog by mutableStateOf(false)
        private set

    fun showUserGameRoleDialog() {
        showUserGameRoleDialog = true
    }

    fun dismissUserGameRoleDialog() {
        showUserGameRoleDialog = false
    }

    fun onChangeGameRole(user: User, role: UserGameRoleData.Role) {
        dismissUserGameRoleDialog()

        currentUser = user
        currentGameRole = role

        load()
    }

    private fun load() {
        val user = currentUser
        val role = currentGameRole

        if (user == null || role == null) {
            loadingState = LoadingState.Error
            return
        }

        loadingState = LoadingState.Loading

        viewModelScope.launch {
            val userAndUid = UserAndUid(
                userEntity = user.userEntity,
                playerUid = PlayerUid.fromGameRole(role)
            )

            val result = withContext(Dispatchers.IO) {
                runCatching { gameRecordClient.getActCalendar(userAndUid) }
                    .getOrNull()
            }

            //1034风控:App内验证后自动重试,失败回退网页验证提示
            val finalResult = if (result?.validate == true) {
                val challenge = CardVerificationService.verify(
                    user.userEntity, CardVerificationService.PATH_ACT_CALENDAR
                )

                if (challenge != null) {
                    withContext(Dispatchers.IO) {
                        runCatching {
                            gameRecordClient.getActCalendar(userAndUid, challenge)
                        }.getOrNull()
                    } ?: result
                } else {
                    result
                }
            } else {
                result
            }

            if (finalResult?.success == true && finalResult.data != null) {
                calendarData = finalResult.data

                loadingState = LoadingState.Success
            } else {
                loadingState = LoadingState.Error

                if (finalResult?.validate == true) {
                    showConfirmDialog = true
                } else {
                    "获取活动日历失败:${finalResult?.message ?: "网络错误"}".errorNotify()
                }
            }
        }
    }

    fun dismissConfirmDialog() {
        showConfirmDialog = false
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

    fun openStrategy(url: String) {
        if (url.isBlank()) {
            return
        }

        HomeHelper.goActivityByIntentNewTask {
            setComponentName(HoyolabWebActivity::class.java)
            putExtra(HoyolabWebActivity.EXTRA_URL, url)
            putExtra(HoyolabWebActivity.EXTRA_MID, currentUser?.userEntity?.mid ?: "")
        }
    }

    //倒计时文案,信任服务器countdown_seconds
    fun formatCountdown(seconds: Long): String {
        val days = seconds / 86400
        val hours = seconds % 86400 / 3600

        return when {
            days >= 1 -> "剩 ${days}天${hours}时"
            hours >= 1 -> "剩 ${hours}小时"
            else -> "即将结束"
        }
    }
}
