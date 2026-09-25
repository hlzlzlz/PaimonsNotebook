package com.lianyi.paimonsnotebook.ui.screen.items.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import com.lianyi.paimonsnotebook.common.extension.scope.launchIO
import com.lianyi.paimonsnotebook.common.util.enums.LoadingState
import com.lianyi.paimonsnotebook.common.web.hutao.genshin.common.service.MaterialService
import com.lianyi.paimonsnotebook.common.web.hutao.genshin.common.service.MonsterService
import com.lianyi.paimonsnotebook.common.web.hutao.genshin.item.Material
import com.lianyi.paimonsnotebook.common.web.hutao.genshin.monster.MonsterData
import com.lianyi.paimonsnotebook.ui.screen.items.util.ItemFilterType
import com.lianyi.paimonsnotebook.ui.screen.items.util.ItemSearchOptionHelper
import com.lianyi.paimonsnotebook.ui.screen.items.util.ItemScreenStateResolver
import com.lianyi.paimonsnotebook.ui.screen.items.viewmodel.base.ItemBaseViewModel

/*
* 怪物资料(资料库的「怪物」标签)
*
* 已从"独立页面 + 自己的 Dialog"改造为与角色/武器同一套界面:
* 左侧列表(可搜索) + 右侧信息卡 + 标签页,复用 ItemScreenContent。
*
* 标签页设计:怪物没有等级/突破/技能这类可算数据(MonsterData 只有
* baseValue / drops / description),且**没有星级字段**,所以不套用角色那套
* "属性/技能/命之座"。按数据实际能表达的维度给出三个标签。
*
* ⚠️ observeCurrentItemState = false:ItemBaseViewModel 的该开关会在
*    currentItem 变化时查询"是否已加入当前养成计划"。怪物不存在这个语义
*    (游戏里怪物材料不走养成计划计算),开着只会对怪物 id 做无意义的 DB 查询。
*    同时 UI 侧传 showAddButton = false,不暴露"添加"入口。
*
* ⚠️ 星级传 0:MonsterData 无星级概念。传 0 时 QualityType 会回落成
*    quality_1 底色(QUALITY_NONE 分支),这是有意为之 —— **不编造星级**。
* */
class MonsterScreenViewModel : ItemBaseViewModel<MonsterData>(observeCurrentItemState = false) {

    private val monsterService by lazy {
        MonsterService {
            //基类的 onMissingFile 是 final,不能重写;它只置 Error,
            //文案由这里补上(两者都必须做:前者维持既有行为,后者给出可操作指引)
            onMissingFile()
            errorMessage = "缺少怪物元数据,请在「设置 - 同步元数据」中下载后再回来"
        }
    }

    private val materialService by lazy {
        MaterialService {
        }
    }

    /*
    * 同名变种折叠后的怪物列表(保留 id 最小的一条)。
    *
    * 与改造前的分组展示不同:统一到列表/筛选架构后,分组标题在筛选结果里
    * 无法保持意义(搜索会把组打散,凭空冒出多个同名组标题),故改为平铺列表,
    * 原来的组名改由卡片副标题(称号)体现。
    * */
    var monsterList by mutableStateOf<List<MonsterData>>(listOf())
        private set

    val itemFilterViewModel by lazy {
        ItemSearchOptionHelper.getMonsterFilterItemViewModel(monsters = monsterList)
    }

    var errorMessage by mutableStateOf("")
        private set

    override val tabs = arrayOf("属性", "掉落", "资料")

    init {
        viewModelScope.launchIO {
            monsterList = monsterService.monsterList
                //同名变种折叠,保留id最小的一条
                .associateBy { it.name }
                .values
                .sortedBy { it.id }

            loadingState = ItemScreenStateResolver.resolve(
                current = loadingState,
                hasItem = monsterList.isNotEmpty()
            )

            //默认选中第一个,使信息卡立即可见(与角色/武器的默认选中一致)
            if (currentItem == null) {
                monsterList.firstOrNull()?.let { onClickItem(it) }
            }
        }
    }

    fun getMaterialById(id: Int): Material = materialService.getMaterialById(id)

    override fun getCurrentItemId(): Int = currentItem?.id ?: 0

    //卡片副标题与列表右侧信息都显示称号(怪物名多为"丘丘人"这类通用名,
    //称号才是区分变种的关键)
    override fun getItemDataContent(
        item: MonsterData,
        type: ItemFilterType,
        isList: Boolean
    ): String = item.title
}
