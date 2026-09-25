package com.lianyi.paimonsnotebook.ui.screen.items.viewmodel.screen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import com.lianyi.paimonsnotebook.common.extension.scope.launchIO
import com.lianyi.paimonsnotebook.common.util.enums.LoadingState
import com.lianyi.paimonsnotebook.common.web.hutao.genshin.common.service.ReliquaryService
import com.lianyi.paimonsnotebook.common.web.hutao.genshin.conveter.RelicIconConverter
import com.lianyi.paimonsnotebook.common.web.hutao.genshin.reliquary.ReliquarySetData
import com.lianyi.paimonsnotebook.ui.screen.items.util.ItemFilterType
import com.lianyi.paimonsnotebook.ui.screen.items.util.ItemSearchOptionHelper
import com.lianyi.paimonsnotebook.ui.screen.items.util.ItemScreenStateResolver
import com.lianyi.paimonsnotebook.ui.screen.items.viewmodel.base.ItemBaseViewModel

/*
* 圣遗物资料(资料库的「圣遗物」标签)
*
* 已从"独立列表页"改造为与角色/武器同一套界面(列表 + 信息卡 + 标签页)。
*
* 标签页:按数据实际能表达的维度给两个 ——
*   套装效果(NeedNumber 与 Descriptions 配对) / 资料(套装说明)
* ⚠️ 没有"属性/技能"这类标签,因为 ReliquarySetData 只有
*    名称、图标、件数、效果描述,没有可计算的属性。
*
* ⚠️ observeCurrentItemState = false:同 MonsterScreenViewModel,圣遗物套装
*    不存在"加入养成计划"的语义,不做无意义的 DB 查询;UI 侧传 showAddButton = false。
* */
class ReliquaryScreenViewModel : ItemBaseViewModel<ReliquarySetData>(observeCurrentItemState = false) {

    var errorMessage by mutableStateOf("")
        private set

    var reliquarySetList by mutableStateOf<List<ReliquarySetData>>(listOf())
        private set

    private val reliquaryService by lazy {
        ReliquaryService(
            onMissingFile = {
                //基类的 onMissingFile 是 final,不能重写;它只置 Error
                onMissingFile()
                errorMessage = "缺少圣遗物元数据,请在「设置 - 同步元数据」中下载后再回来"
            }
        )
    }

    val itemFilterViewModel by lazy {
        ItemSearchOptionHelper.getReliquaryFilterItemViewModel(sets = reliquarySetList)
    }

    override val tabs = arrayOf("套装效果", "资料")

    init {
        viewModelScope.launchIO {
            //与改造前一致:按 SetId 倒序(新套装在前)
            reliquarySetList = reliquaryService.reliquarySetList.sortedByDescending { it.SetId }

            loadingState = ItemScreenStateResolver.resolve(
                current = loadingState,
                hasItem = reliquarySetList.isNotEmpty()
            )

            if (currentItem == null) {
                reliquarySetList.firstOrNull()?.let { onClickItem(it) }
            }
        }
    }

    override fun getCurrentItemId(): Int = currentItem?.SetId ?: 0

    fun getReliquaryStar(setId: Int) = reliquaryService.reliquaryMaxStarMap[setId] ?: 0

    //列表卡片显示套装名
    override fun getItemDataContent(
        item: ReliquarySetData,
        type: ItemFilterType,
        isList: Boolean
    ): String = item.Name

    //套装图标的 url(供 UI 与卡片复用)
    fun getReliquaryIconUrl(set: ReliquarySetData) = RelicIconConverter.iconNameToUrl(set.Icon)
}
