package com.lianyi.paimonsnotebook.ui.screen.gacha.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lianyi.paimonsnotebook.common.database.gacha.data.GachaRecordOverview
import com.lianyi.paimonsnotebook.common.extension.string.warnNotify
import com.lianyi.paimonsnotebook.common.util.enums.LoadingState
import com.lianyi.paimonsnotebook.common.util.metadata.genshin.uigf.UIGFHelper
import com.lianyi.paimonsnotebook.common.web.hutao.genshin.common.service.AvatarService
import com.lianyi.paimonsnotebook.common.web.hutao.genshin.common.service.GachaEventService
import com.lianyi.paimonsnotebook.common.web.hutao.genshin.common.service.WeaponService
import com.lianyi.paimonsnotebook.common.web.hutao.genshin.gacha_event.GachaEventEntry
import com.lianyi.paimonsnotebook.ui.screen.gacha.data.BeyondGachaGroup
import com.lianyi.paimonsnotebook.ui.screen.gacha.data.GachaOverviewListItem
import com.lianyi.paimonsnotebook.ui.screen.gacha.service.GachaPityCalculator
import com.lianyi.paimonsnotebook.ui.screen.gacha.service.GachaRecordService
import com.lianyi.paimonsnotebook.ui.screen.gacha.view.GachaRecordOptionScreen
import com.lianyi.paimonsnotebook.ui.screen.home.util.HomeHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GachaRecordScreenViewModel : ViewModel() {

    var currentPageIndex by mutableIntStateOf(0)

    //0总览 1保底 2复刻 3角色 4武器 5千星奇域
    val tabs = arrayOf("总览", "保底", "复刻", "角色", "武器", "千星奇域")

    //保底统计
    var pityList by mutableStateOf<List<GachaPityCalculator.PoolPity>?>(null)
    var pityLoadingState by mutableStateOf(LoadingState.Loading)

    //复刻倒计时
    var countdownGroups by mutableStateOf<Map<String, List<GachaPityCalculator.CountdownEntry>>?>(null)
    var countdownLoadingState by mutableStateOf(LoadingState.Loading)

    var loadingState by mutableStateOf(LoadingState.Loading)

    var gachaRecordOverview by mutableStateOf<GachaRecordOverview?>(null)
        private set

    var overviewItemMap by mutableStateOf<Map<String, List<GachaOverviewListItem>>>(mapOf())
        private set

    //千星奇域记录(按卡池类型分组)
    var beyondGroups by mutableStateOf<List<BeyondGachaGroup>?>(null)
        private set
    var beyondLoadingState by mutableStateOf(LoadingState.Loading)
        private set

    private val gachaRecordService = GachaRecordService()

    val itemsList = mutableStateListOf<Pair<Int, List<Pair<GachaOverviewListItem, Int>>>>()

    init {
        viewModelScope.launch {
            //总览数据流
            launch {
                gachaRecordService.gachaRecordOverviewForCurrentUidFlow.collect { overview ->
                    gachaRecordOverview = overview

                    loadingState = if (overview == null) {
                        LoadingState.Empty
                    } else if (overview.uid.isEmpty() && overview.list.isEmpty()) {
                        LoadingState.Loading
                    } else if (overview.uid.isNotEmpty() && overview.list.isNotEmpty()) {
                        setPlayerGachaRecordList(gachaRecordOverview!!.uid)

                        LoadingState.Success
                    } else {
                        LoadingState.Empty
                    }
                }
            }
        }
    }

    private val weaponService by lazy {
        WeaponService {
            loadingState = LoadingState.Error
        }
    }

    private val avatarNameService by lazy {
        AvatarService {
            loadingState = LoadingState.Error
        }
    }

    private val weaponNameMap by lazy {
        weaponService.weaponList.associateBy {
            it.name
        }
    }

    private val avatarMap by lazy {
        avatarNameService.avatarList.associateBy {
            it.name
        }
    }

    //id为键的映射,供复刻倒计时展示头像与名称
    private val avatarById by lazy {
        avatarNameService.avatarList.associateBy { it.id }
    }

    private val weaponById by lazy {
        weaponService.weaponList.associateBy { it.id }
    }

    //8位id为角色,5位为武器
    fun getGachaItemName(itemId: Int) =
        if (itemId >= 10000000) avatarById[itemId]?.name else weaponById[itemId]?.name

    fun getGachaItemIconUrl(itemId: Int) =
        if (itemId >= 10000000) avatarById[itemId]?.iconUrl else weaponById[itemId]?.iconUrl

    //读取GachaEvent元数据,文件缺失返回null
    private suspend fun loadGachaEvents(): List<GachaEventEntry>? =
        withContext(Dispatchers.IO) {
            var missing = false

            val list = GachaEventService { missing = true }.eventList

            if (missing) null else list
        }

    private fun loadPity() {
        if (pityList != null) {
            return
        }

        viewModelScope.launch {
            pityLoadingState = LoadingState.Loading

            val uid = gachaRecordService.currentUid()

            val events = loadGachaEvents()

            if (events == null) {
                pityLoadingState = LoadingState.Empty
                "缺少卡池元数据,请在设置中同步元数据后重试".warnNotify()
                return@launch
            }

            val records = withContext(Dispatchers.IO) {
                gachaRecordService.getGachaItemsByUid(uid)
            }

            val result = GachaPityCalculator.calculate(records, events)

            pityList = result
            pityLoadingState =
                if (uid.isEmpty() || result.isEmpty()) LoadingState.Empty else LoadingState.Success
        }
    }

    private fun loadCountdown() {
        if (countdownGroups != null) {
            return
        }

        viewModelScope.launch {
            countdownLoadingState = LoadingState.Loading

            val events = loadGachaEvents()

            if (events == null) {
                countdownLoadingState = LoadingState.Empty
                "缺少卡池元数据,请在设置中同步元数据后重试".warnNotify()
                return@launch
            }

            val result = GachaPityCalculator.buildCountdown(events)

            countdownGroups = result
            countdownLoadingState =
                if (result.isEmpty()) LoadingState.Empty else LoadingState.Success
        }
    }

    private suspend fun setPlayerGachaRecordList(uid: String) {
        withContext(Dispatchers.IO) {
            val map = mutableMapOf<String, List<GachaOverviewListItem>>()

            val items = mutableMapOf<String, Pair<GachaOverviewListItem, Int>>()

            UIGFHelper.gachaList.forEach { type ->
                val list = gachaRecordService.getHistoryWishByUIGFGachaTypeAndUid(
                    uigfGachaType = type,
                    uid = uid
                )
                val star5Items = mutableListOf<GachaOverviewListItem>()

                var count = 0
                list.forEach { item ->
                    count++

                    val iconUrl = when (item.itemType) {
                        UIGFHelper.ItemType.Weapon -> {
                            weaponNameMap[item.name]?.iconUrl
                        }

                        UIGFHelper.ItemType.Avatar -> {
                            avatarMap[item.name]?.iconUrl
                        }

                        else -> null
                    }

                    val data = GachaOverviewListItem(
                        name = item.name,
                        iconUrl = iconUrl ?: "",
                        rankType = item.rankType.toIntOrNull() ?: 0,
                        count = count,
                        type = item.itemType
                    )

                    val pair = items[data.name]
                    if (pair == null) {
                        items[data.name] = data to 1
                    } else {
                        items[data.name] = pair.copy(second = pair.second + 1)
                    }

                    if ((item.rankType.toIntOrNull() ?: 0) == 5) {
                        star5Items += data
                        count = 0
                    }
                }

                if (star5Items.isNotEmpty()) {
                    map += type to star5Items.reversed()
                }
            }

            itemsList.clear()

            itemsList += items.toList().map {
                it.second
            }.toMutableList().sortedByDescending { it.second }.groupBy {
                it.first.rankType
            }.toList()
                .toMutableList()
                .sortedByDescending { it.first }

            overviewItemMap = map
        }
    }

    fun setSelectedPageIndex(pageIndex: Int) {
        this.currentPageIndex = pageIndex

        when (pageIndex) {
            1 -> loadPity()
            2 -> loadCountdown()
            5 -> loadBeyondGacha()
        }
    }

    /*
    * 加载千星奇域记录
    *
    * 该页只依赖当前选中的祈愿 uid(与总览同源),不需要额外参数。
    * 用 beyondLoadingState 单独表达状态:千星奇域是可选数据,
    * 没有记录时应显示"空"而不是让整个祈愿页报错。
    * */
    private fun loadBeyondGacha() {
        viewModelScope.launch {
            beyondLoadingState = LoadingState.Loading

            val uid = gachaRecordService.currentUid()

            val items = withContext(Dispatchers.IO) {
                gachaRecordService.getBeyondGachaItemsByUid(uid)
            }

            val groups = BeyondGachaGroup.from(items)

            beyondGroups = groups
            beyondLoadingState = if (groups.isEmpty()) LoadingState.Empty else LoadingState.Success
        }
    }

    fun goOptionScreen() {
        HomeHelper.goActivity(GachaRecordOptionScreen::class.java)
    }

}