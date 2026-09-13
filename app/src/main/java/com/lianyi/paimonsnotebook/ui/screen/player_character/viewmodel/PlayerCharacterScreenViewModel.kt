package com.lianyi.paimonsnotebook.ui.screen.player_character.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lianyi.paimonsnotebook.common.data.hoyolab.user.User
import com.lianyi.paimonsnotebook.common.data.hoyolab.user.UserAndUid
import com.lianyi.paimonsnotebook.common.database.user.util.AccountHelper
import com.lianyi.paimonsnotebook.common.extension.intent.setComponentName
import com.lianyi.paimonsnotebook.common.extension.scope.launchIO
import com.lianyi.paimonsnotebook.common.service.geetest.CardVerificationService
import com.lianyi.paimonsnotebook.common.view.HoyolabWebActivity
import com.lianyi.paimonsnotebook.common.extension.scope.launchMain
import com.lianyi.paimonsnotebook.common.extension.string.errorNotify
import com.lianyi.paimonsnotebook.common.util.enums.LoadingState
import com.lianyi.paimonsnotebook.common.util.json.JSON
import com.lianyi.paimonsnotebook.common.web.hoyolab.takumi.binding.UserGameRoleData
import com.lianyi.paimonsnotebook.common.web.hoyolab.takumi.game_record.GameRecordClient
import com.lianyi.paimonsnotebook.common.web.hoyolab.takumi.game_record.character.CharacterListData
import com.lianyi.paimonsnotebook.common.web.hutao.genshin.avatar.AvatarData
import com.lianyi.paimonsnotebook.common.web.hutao.genshin.common.service.AvatarService
import com.lianyi.paimonsnotebook.common.web.hutao.genshin.common.service.WeaponService
import com.lianyi.paimonsnotebook.common.web.hutao.genshin.intrinsic.format.FightPropertyFormat
import com.lianyi.paimonsnotebook.common.web.hutao.genshin.weapon.WeaponData
import com.lianyi.paimonsnotebook.ui.screen.home.util.HomeHelper
import com.lianyi.paimonsnotebook.ui.screen.player_character.view.PlayerCharacterDetailScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PlayerCharacterScreenViewModel : ViewModel() {

    private val avatarService by lazy {
        AvatarService(onMissingFile = this::onMissingFile)
    }

    private val weaponService by lazy {
        WeaponService(onMissingFile = this::onMissingFile)
    }

    var currentUser by mutableStateOf<User?>(null)
        private set

    var currentGameRole by mutableStateOf<UserGameRoleData.Role?>(null)
        private set

    init {
        viewModelScope.launchIO {
            launchMain {
                //持续监听用户流:打开页面时账号可能尚未初始化完成,只读一次会得到空值且不会重试
                AccountHelper.selectedUserFlow.collect { user ->
                    setUser(user)
                    setGameRole(user?.getSelectedGameRole())
                }
            }

            launchIO {
                //提前初始化service
                avatarService.avatarList
                weaponService.weaponList
            }
        }
    }

    var showGameRoleDialog by mutableStateOf(false)

    //1034风控验证确认框
    var showConfirmDialog by mutableStateOf(false)
        private set

    var loadingState by mutableStateOf(LoadingState.Loading)
        private set

    val characterList = mutableListOf<CharacterListData.CharacterData>()

    private val gameRecordClient by lazy {
        GameRecordClient()
    }

    //显示的角色列表
    val avatarDataList = mutableStateListOf<AvatarData>()

    private fun onMissingFile() {
        loadingState = LoadingState.Error
    }

    private fun setUser(user: User?) {
        currentUser = user
    }

    //记录上次已加载的角色,避免用户流重复发射时反复请求
    private var loadedGameUid: String? = null

    private fun setGameRole(role: UserGameRoleData.Role?) {
        this.currentGameRole = role

        if (role == null) {
            return
        }

        //同一角色且已有数据时不重复加载
        if (role.game_uid == loadedGameUid && avatarDataList.isNotEmpty()) {
            return
        }

        viewModelScope.launchIO {
            getPlayerCharacterList()
        }
    }

    private suspend fun getPlayerCharacterList() {
        withContext(Dispatchers.IO) {
            loadingState = LoadingState.Loading
            val user = currentUser
            val role = currentGameRole
            if (user == null || role == null) {
                "用户或角色不存在".errorNotify()
                loadingState = LoadingState.Empty
                return@withContext
            }

            val userAndUid = UserAndUid(user.userEntity, role.getPlayerUid())

            val res = gameRecordClient.getCharacterList(userAndUid)

            loadedGameUid = role.game_uid

            if (!res.success) {
                loadingState = LoadingState.Error

                //1034风控:App内滑块验证后自动重试,失败时回退到网页验证
                if (res.validate) {
                    val challenge = CardVerificationService.verify(
                        user.userEntity, CardVerificationService.PATH_CHARACTER_LIST
                    )

                    if (challenge != null) {
                        val retry = gameRecordClient.getCharacterList(userAndUid, challenge = challenge)

                        if (retry.success) {
                            setCharacterList(retry.data)
                            return@withContext
                        }
                    }

                    showConfirmDialog = true
                } else {
                    "获取数据失败:${res.message}[${res.retcode}]".errorNotify()
                }
                return@withContext
            }

            setCharacterList(res.data)
        }
    }

    private fun setCharacterList(characterListData: CharacterListData) {
        this.avatarDataList.clear()
        this.characterList.clear()

        this.avatarDataList += characterListData.list.mapNotNull {
            avatarService.avatarMap[it.id]
        }

        this.characterList += characterListData.list

        loadingState = LoadingState.Success
    }

    fun dismissConfirmDialog() {
        showConfirmDialog = false
    }

    //前往验证界面,验证通过后重新进入本页面即可正常查询
    fun goValidateScreen() {
        showConfirmDialog = false

        val user = currentUser

        if (user == null) {
            "当前用户状态异常".errorNotify()
            return
        }

        HomeHelper.goActivityByIntentNewTask {
            setComponentName(HoyolabWebActivity::class.java)
            putExtra("mid", user.userEntity.mid)
        }
    }

    fun getAvatarDataById(i: Int): AvatarData? {
        return avatarService.avatarMap[i]
    }

    fun getWeaponDataById(i: Int): WeaponData? {
        return weaponService.weaponMap[i]
    }

    fun getWeaponFightPropertyFormatList(
        weaponData: WeaponData,
        i: Int,
        b: Boolean
    ): List<FightPropertyFormat> {
        return weaponService.getFightPropertyFormatList(
            weapon = weaponData,
            level = i,
            promoted = b
        )
    }

    fun onClickListItem(characterData: CharacterListData.CharacterData) {

        val user = currentUser

        if (user == null) {
            "当前用户为空".errorNotify()
            return
        }

        HomeHelper.goActivityByIntentNewTask {
            setComponentName(PlayerCharacterDetailScreen::class.java)

            putExtra(
                PlayerCharacterDetailScreen.PARAM_USER_AND_UID_JSON,
                JSON.stringify(user.getUserAndUid())
            )

            putExtra(
                PlayerCharacterDetailScreen.PARAM_CHARACTER_LIST_JSON,
                JSON.stringify(characterList)
            )

            putExtra(PlayerCharacterDetailScreen.PARAM_SELECTED_CHARACTER_ID, characterData.id)
        }
    }

    fun showChooseGameRoleDialog() {
        showGameRoleDialog = true
    }

    fun onUserGameRoleDialogButtonClick(index: Int) {
        onUserGameRoleDialogDismissRequest()
    }

    fun onSelectedGameRole(user: User, role: UserGameRoleData.Role) {
        setUser(user)
        setGameRole(role)
        onUserGameRoleDialogDismissRequest()
    }

    fun onUserGameRoleDialogDismissRequest() {
        showGameRoleDialog = false
    }
}