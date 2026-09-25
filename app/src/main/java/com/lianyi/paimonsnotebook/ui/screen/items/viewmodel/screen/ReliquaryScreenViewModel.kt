package com.lianyi.paimonsnotebook.ui.screen.items.viewmodel.screen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import com.lianyi.paimonsnotebook.common.extension.scope.launchIO
import com.lianyi.paimonsnotebook.common.extension.scope.withContextMain
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

    private val reliquaryService by lazy {
        ReliquaryService(
            onMissingFile = {
                //基类的 onMissingFile 是 final,不能重写;它只置 Error
                onMissingFile()
                errorMessage = "缺少圣遗物元数据,请在「设置 - 同步元数据」中下载后再回来"
            }
        )
    }

    /*
    * 套装列表(按 SetId 倒序,新套装在前 —— 与改造前一致)。
    *
    * ⚠️ 必须用 `by lazy` 而不是"在 init 的协程里赋值":ItemFilterViewModel
    *    在构造时**按值捕获** items,若筛选器先于数据构造,它会永久持有空列表
    *    —— 表现为"点列表按钮后一条都没有"。详见 MonsterScreenViewModel 同类注释。
    * */
    val reliquarySetList: List<ReliquarySetData> by lazy {
        reliquaryService.reliquarySetList.sortedByDescending { it.SetId }
    }

    val itemFilterViewModel by lazy {
        ItemSearchOptionHelper.getReliquaryFilterItemViewModel(sets = reliquarySetList)
    }

    override val tabs = arrayOf("套装效果", "资料")

    init {
        viewModelScope.launchIO {
            //先触碰 reliquarySetList,让 service 在 IO 线程完成文件读取与解析
            val list = reliquarySetList

            //Compose 状态的写入必须在主线程(本项目硬性约束)
            withContextMain {
                loadingState = ItemScreenStateResolver.resolve(
                    current = loadingState,
                    hasItem = list.isNotEmpty()
                )

                if (currentItem == null) {
                    list.firstOrNull()?.let { onClickItem(it) }
                }
            }
        }
    }

    override fun getCurrentItemId(): Int = currentItem?.SetId ?: 0

    /*
    * ⚠️ 必须重写:基类的 toggleFilterContent() 是**空实现**
    *    (`ItemBaseViewModel:126` 的 `open fun toggleFilterContent() {}`)。
    *    角色/武器各自重写它来打开"物品列表"抽屉,我最初漏了这一处,
    *    导致资料库里的圣遗物页**点列表按钮没反应、列表永远打不开**,
    *    看起来就像"列表是空的"。
    * */
    override fun toggleFilterContent() {
        itemFilterViewModel.toggleFilterContent()
    }

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
