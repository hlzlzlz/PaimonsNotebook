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
import com.lianyi.paimonsnotebook.common.database.ledger.dao.LedgerMonthSnapshotDao
import com.lianyi.paimonsnotebook.common.database.PaimonsNotebookDatabase
import com.lianyi.paimonsnotebook.common.database.ledger.entity.LedgerMonthSnapshot
import com.lianyi.paimonsnotebook.common.database.user.util.AccountHelper
import com.lianyi.paimonsnotebook.common.extension.intent.setComponentName
import com.lianyi.paimonsnotebook.common.extension.string.errorNotify
import com.lianyi.paimonsnotebook.common.util.enums.LoadingState
import com.lianyi.paimonsnotebook.common.util.metadata.genshin.ledger.LedgerHistoryFormatter
import com.lianyi.paimonsnotebook.common.util.metadata.genshin.ledger.LedgerSnapshotMapper
import com.lianyi.paimonsnotebook.common.view.HoyolabWebActivity
import com.lianyi.paimonsnotebook.common.web.hoyolab.takumi.binding.UserGameRoleData
import com.lianyi.paimonsnotebook.common.web.hoyolab.takumi.game_record.GameRecordClient
import com.lianyi.paimonsnotebook.common.web.hoyolab.takumi.game_record.ledger.LedgerData
import com.lianyi.paimonsnotebook.ui.screen.home.util.HomeHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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

    //历史快照(倒序:最新在前)
    var historySnapshots by mutableStateOf<List<LedgerMonthSnapshot>>(listOf())
        private set

    //历史浏览面板是否展开
    var showHistory by mutableStateOf(false)
        private set

    /*
    * 历史行(含环比)
    * 用 derived 而非直接存,避免快照列表与展示数据两处状态不同步
    * */
    val historyRows: List<LedgerHistoryFormatter.HistoryRow>
        get() = LedgerHistoryFormatter.buildRows(historySnapshots)

    init {
        //Compose状态的写入必须在主线程,仅网络请求切换IO
        viewModelScope.launch {
            AccountHelper.selectedUserFlow.collect {
                currentUser = it
                currentGameRole = it?.getSelectedGameRole()
                loadLedger(currentMonth)
                observeHistory()
            }
        }
    }

    /*
    * 订阅当前角色的历史快照
    *
    * 按游戏 uid 订阅 —— 快照的主键是游戏 uid,而非账号 mid
    * (一个账号可有多个角色,历史必须分开)。
    * 切换角色时旧订阅会被 collectLatest 语义自然取代(每次重新 collect 前先取消)。
    * */
    private var historyJob: Job? = null

    private fun observeHistory() {
        val uid = currentGameRole?.game_uid ?: run {
            historySnapshots = emptyList()
            return
        }

        historyJob?.cancel()
        historyJob = viewModelScope.launch {
            PaimonsNotebookDatabase.database.ledgerMonthSnapshotDao
                .getSnapshotsByUid(uid)
                .collect {
                    historySnapshots = it
                }
        }
    }

    fun toggleHistory() {
        showHistory = !showHistory
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

                //落库快照:服务端只保留近期月份,过期即永久丢失;
                //每次成功拉取都存一份(首次成功保留,重复拉取跳过)
                result.data?.let { data ->
                    withContext(Dispatchers.IO) {
                        runCatching {
                            PaimonsNotebookDatabase.database.ledgerMonthSnapshotDao.insertIfAbsent(
                                LedgerSnapshotMapper.toSnapshot(
                                    data = data,
                                    savedAt = System.currentTimeMillis()
                                )
                            )
                        }
                    }
                }
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
