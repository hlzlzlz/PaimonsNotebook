package com.lianyi.paimonsnotebook.ui.screen.home.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lianyi.paimonsnotebook.common.extension.data_store.editValue
import com.lianyi.paimonsnotebook.common.extension.list.move
import com.lianyi.paimonsnotebook.common.extension.scope.launchIO
import com.lianyi.paimonsnotebook.common.extension.scope.launchMain
import com.lianyi.paimonsnotebook.common.extension.string.isEmptyList
import com.lianyi.paimonsnotebook.common.extension.string.notify
import com.lianyi.paimonsnotebook.common.extension.string.warnNotify
import com.lianyi.paimonsnotebook.common.util.data_store.PreferenceKeys
import com.lianyi.paimonsnotebook.common.util.data_store.dataStoreValues
import com.lianyi.paimonsnotebook.common.util.json.JSON
import com.lianyi.paimonsnotebook.ui.screen.home.data.HomeCustomDrawerData
import com.lianyi.paimonsnotebook.ui.screen.home.data.ModalItemData
import com.lianyi.paimonsnotebook.ui.screen.home.util.HomeHelper

class HomeDrawerManagerScreenViewModel : ViewModel() {


    var enableCustomHomeDrawer by mutableStateOf(false)
        private set

    private val modalItems = mutableListOf<ModalItemData>()

    val showModalItems = mutableStateListOf<HomeCustomDrawerData>()

    init {
        viewModelScope.launchMain {
            viewModelScope.launchMain {
                dataStoreValues {
                    enableCustomHomeDrawer = it[PreferenceKeys.EnableCustomHomeDrawer] ?: false

                    val customDrawerListJson =
                        it[PreferenceKeys.CustomHomeDrawerList] ?: JSON.EMPTY_LIST

                    updateShowModalItems(enableCustomHomeDrawer, customDrawerListJson)
                }
            }

            modalItems.clear()
            /*
            * 这里必须用 getAllModalItemData 而非 getShowModalItemData:
            * 后者会按元数据开关把"需要元数据"的功能过滤掉,导致用户在
            * 侧边栏管理页里也看不到它们、更无法排序 —— 与侧边栏改为
            * 置灰展示(而非隐藏)的做法保持一致,这里展示完整列表。
            * */
            modalItems += HomeHelper.getAllModalItemData()
        }
    }

    fun toggleCustomHomeDrawer() {
        viewModelScope.launchIO {
            val state = !enableCustomHomeDrawer
            PreferenceKeys.EnableCustomHomeDrawer.editValue(state)

            if (!state) {
                "已禁用自定义侧边栏,当前使用的是默认侧边栏".warnNotify(closeable = false)
            }
        }
    }

    fun toggleModalItemState(item: HomeCustomDrawerData, index: Int) {
        if (!enableCustomHomeDrawer) {
            "未启用自定义侧边栏功能".warnNotify(false)
            return
        }

        this.showModalItems[index] = item.copy(disable = !item.disable)
        sortShowModalList()
    }

    fun submit() {
        if (!enableCustomHomeDrawer) {
            "未启用自定义侧边栏功能".warnNotify(false)
            return
        }

        saveCustomModalList()
    }

    fun onListItemMove(from: Int, to: Int) {
        if (!enableCustomHomeDrawer) return

        showModalItems.move(from, to)
    }

    fun getModelItemByClassName(className: String) = HomeHelper.modalItemMap[className]

    //更新显示的列表数据
    private fun updateShowModalItems(enableCustomDrawer: Boolean, json: String) {
        this.showModalItems.clear()

        //如果未启用自定义侧边栏,或json为空就使用默认的侧边栏数据
        showModalItems += if (!enableCustomDrawer || json.isEmptyList()) {
            modalItems.map {
                it.toCustomDrawerData()
            }
        } else {
            /*
            * ⚠️ 必须迁移旧类名再展示。
            *
            * 1.8.24 合并了侧边栏条目(四个资料页 -> 资料库等),而这里的数据
            * 来自用户早先保存的配置(以类名持久化)。不迁移的话,
            * getModelItemByClassName 对旧类名返回 null,列表项会被
            * `?: return@itemsIndexed` **整行跳过** —— 用户看到管理页少了几行
            * 却没有任何提示,也无从恢复(他配置过的那几项无法再被排到前面)。
            *
            * 迁移后可能出现重复(原来同时启用"角色资料"与"武器资料",
            * 现在都指向资料库),按类名去重并保留首次出现的位置。
            * */
            HomeHelper.getCustomDrawerListFromJson(json)
                .map { it.copy(targetClass = HomeHelper.migrateLegacyDrawerTarget(it.targetClass)) }
                .distinctBy { it.targetClass }
        }

        sortShowModalList()
    }

    private fun sortShowModalList() {
        this.showModalItems.sortBy { it.disable }
    }

    //保存当前自定义列表
    private fun saveCustomModalList() {
        if (showModalItems.count { !it.disable } == 0) {
            "最少保留一个功能".warnNotify(true)
            return
        }

        viewModelScope.launchIO {
            //只保存不为空的选项
            PreferenceKeys.CustomHomeDrawerList.editValue(JSON.stringify(showModalItems.filter {
                HomeHelper.modalItemMap[it.targetClass] != null
            }.mapIndexed { index, homeCustomDrawerData ->
                homeCustomDrawerData.copy(sort = index)
            }))

            "已按照当前配置设置侧边栏".notify()
        }
    }
}