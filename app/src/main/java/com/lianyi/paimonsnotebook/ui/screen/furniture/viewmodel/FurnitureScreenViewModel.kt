package com.lianyi.paimonsnotebook.ui.screen.furniture.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lianyi.paimonsnotebook.common.data.hoyolab.user.User
import com.lianyi.paimonsnotebook.common.database.user.util.AccountHelper
import com.lianyi.paimonsnotebook.common.extension.string.errorNotify
import com.lianyi.paimonsnotebook.common.extension.string.notify
import com.lianyi.paimonsnotebook.common.util.enums.LoadingState
import com.lianyi.paimonsnotebook.common.web.hoyolab.takumi.event.calculate.CalculateClient
import com.lianyi.paimonsnotebook.common.web.hoyolab.takumi.event.calculate.FurnitureItem
import com.lianyi.paimonsnotebook.common.web.hoyolab.takumi.event.calculate.FurnitureShareCodeParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/*
* 洞天摹本
*
* 接通 CalculateFurniture* 三个此前声明未用的端点:
*   输入分享码 -> blueprint 取家具清单 -> 展示每件家具所需数量与缺口
*
* ⚠️ 分享码必须先经 FurnitureShareCodeParser 抽取纯码:
*    用户从游戏里复制的是"摹本分享码：1234…"这类整段文本,
*    直接拼进 URL 会 404。
*
* ⚠️ 该端点需要登录(cookie_token)与 uid/region 才返回 num/lack_num,
*    因此本页依赖已选用户;未登录时提示而不是静默失败。
* */
class FurnitureScreenViewModel : ViewModel() {

    var loadingState by mutableStateOf(LoadingState.Empty)
        private set

    var shareCodeInput by mutableStateOf("")
        private set

    val items = mutableStateListOf<FurnitureItem>()

    //不在计算范围内的家具(blueprint 的 not_calc_list)
    val notCalcItems = mutableStateListOf<FurnitureItem>()

    private val calculateClient by lazy { CalculateClient() }

    fun onShareCodeChange(value: String) {
        shareCodeInput = value
    }

    /*
    * 查询摹本
    * 输入不合法时即时提示,不发起请求
    * */
    fun query() {
        val code = FurnitureShareCodeParser.extract(shareCodeInput)

        if (code == null) {
            "请输入有效的摹本分享码".errorNotify()
            return
        }

        viewModelScope.launch {
            loadingState = LoadingState.Loading

            val user = withContext(Dispatchers.IO) { getSelectedUser() }

            if (user == null) {
                loadingState = LoadingState.Error
                "请先在账号管理中登录并选择用户".errorNotify()
                return@launch
            }

            val result = withContext(Dispatchers.IO) {
                runCatching { calculateClient.getFurnitureBlueprint(user, code) }.getOrNull()
            }

            if (result?.success != true || result.data == null) {
                loadingState = LoadingState.Error
                "获取摹本失败:${result?.message ?: "网络异常"}".errorNotify()
                return@launch
            }

            items.clear()
            items.addAll(result.data.list)

            notCalcItems.clear()
            notCalcItems.addAll(result.data.notCalcList)

            /*
            * ⚠️ 必须判空列表:若无条件置 Success,成功分支会去渲染空列表,
            * 用户看到的是"查询成功但什么都没有",无法区分"该摹本无家具"与
            * "接口返回了空数据"。置 Empty 由界面给出明确文案。
            * */
            loadingState = if (items.isEmpty() && notCalcItems.isEmpty()) {
                LoadingState.Empty
            } else {
                LoadingState.Success
            }

            "已获取摹本清单".notify()
        }
    }

    private suspend fun getSelectedUser(): User? =
        runCatching { AccountHelper.selectedUserFlow.value }.getOrNull()
}
