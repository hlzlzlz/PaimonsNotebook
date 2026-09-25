package com.lianyi.paimonsnotebook.ui.screen.weekly.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lianyi.paimonsnotebook.common.util.enums.LoadingState
import com.lianyi.paimonsnotebook.common.util.weekly.GachaMaterialOpenWindow
import com.lianyi.paimonsnotebook.common.util.weekly.WeeklyMaterialTable
import com.lianyi.paimonsnotebook.common.web.hutao.genshin.avatar.AvatarData
import com.lianyi.paimonsnotebook.common.web.hutao.genshin.common.service.AvatarService
import com.lianyi.paimonsnotebook.common.web.hutao.genshin.common.service.GachaEventService
import com.lianyi.paimonsnotebook.common.web.hutao.genshin.common.service.MaterialService
import com.lianyi.paimonsnotebook.common.web.hutao.genshin.common.service.WeaponService
import com.lianyi.paimonsnotebook.common.web.hutao.genshin.weapon.WeaponData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/*
* 素材刷新日历ViewModel
* 按服务器时区(GMT+8)构建一周七天的可刷素材视图
* */
class WeeklyCalendarScreenViewModel : ViewModel() {

    //关联条目(角色/武器)
    data class ItemRef(val name: String, val iconUrl: String)

    data class MaterialGroupInfo(
        val name: String,
        val iconUrl: String,
        val avatars: List<ItemRef>,
        val weapons: List<ItemRef>
    )

    data class DayInfo(
        val label: String,
        val isToday: Boolean,
        val isSundayAll: Boolean,
        val talents: List<MaterialGroupInfo>,
        val bosses: List<MaterialGroupInfo>,
        val birthdays: List<ItemRef>,
        /*
        * 该日是否因"新角色卡池开启后 7 天"而全素材开放(与周日全开区分开:
        * 周日是游戏常态,这个是限时窗口,UI 上说明文案不同)。
        * */
        val isGachaOpenWindow: Boolean = false,
        //触发该窗口的卡池名,用于 UI 说明"为何今天全开放";非窗口期为 null
        val gachaWindowSource: String? = null
    )

    var loadingState by mutableStateOf(LoadingState.Loading)
        private set

    var days by mutableStateOf<List<DayInfo>>(listOf())
        private set

    /*
    * 加载失败原因。
    *
    * 本页的 Error 来自三处 AvatarService/WeaponService/MaterialService 的
    * onMissingFile 回调,以及 buildDays() 结果为空 —— 全部等价于**本地缺少
    * 元数据**。这三个 service 都是 `by lazy`(实例被缓存),单纯重试不会
    * 重新读文件,故不提供重试按钮,而是明确告诉用户去哪里恢复。
    * */
    var errorMessage by mutableStateOf("")
        private set

    //0素材日历 1养成材料
    var tabIndex by mutableStateOf(0)
        private set

    /*
    * ⚠️ 原为 arrayOf("刷新日历", "养成材料") —— 但 0 号标签页展示的是**一周
    * 日历内容**(见 WeeklyCalendarScreen 的 when(tabIndex)),"刷新日历"是动作
    * 描述而非内容名称,容易让用户误以为点它是执行刷新操作。
    * 按实际内容改为"素材日历"(与侧边栏入口名一致)。
    * */
    val tabs = arrayOf("素材日历", "养成材料")

    fun onTabIndexChange(index: Int) {
        tabIndex = index
    }

    private val dayNames = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

    private val avatarService by lazy {
        AvatarService { onMetadataMissing() }
    }

    private val weaponService by lazy {
        WeaponService { onMetadataMissing() }
    }

    private val materialService by lazy {
        MaterialService { onMetadataMissing() }
    }

    /*
    * 卡池排期服务(元数据 GachaEvent.json)
    *
    * ⚠️ 它的 onMissingFile **不**置 loadingState=Error —— 这个文件缺失只影响
    *    "新品期全素材开放"这一项增强,主体日历(按周几)仍然可用。
    *    若跟着置 Error,会让整个素材日历在缺这个文件时变成错误页,属于**放大故障面**。
    * */
    private val gachaEventService by lazy {
        GachaEventService { }
    }

    private fun onMetadataMissing() {
        loadingState = LoadingState.Error
        errorMessage = "缺少养成材料元数据,请在「设置 - 同步元数据」中下载后再回来"
    }

    init {
        viewModelScope.launch {
            val dayInfos = withContext(Dispatchers.IO) { buildDays() }

            days = dayInfos
            if (dayInfos.isEmpty()) {
                loadingState = LoadingState.Error
                if (errorMessage.isBlank()) {
                    errorMessage = "缺少养成材料元数据,请在「设置 - 同步元数据」中下载后再回来"
                }
            } else {
                loadingState = LoadingState.Success
            }
        }
    }

    //上次构建days时依据的服务器日期(如2026-09-18),用于判断是否需要跨天重建
    private var builtDate: String = ""

    /*
    * 重新计算"今天"高亮与生日
    * 页面原先只在init里算一次,应用跨天后仍显示旧日期:
    * "今天"高亮错位、当天生日不显示。本方法数据全部来自本地元数据(无网络),
    * 由界面在onResume时调用,开销可忽略。
    *
    * 以日期(而非星期)为判断依据:生日按"月/日"匹配,同星期但不同日期时生日列表也会不同。
    * */
    fun refreshToday() {
        if (days.isNotEmpty() && builtDate == serverDate().toString()) {
            return
        }

        viewModelScope.launch {
            val dayInfos = withContext(Dispatchers.IO) { buildDays() }

            if (dayInfos.isNotEmpty()) {
                days = dayInfos
                loadingState = LoadingState.Success
            }
        }
    }

    //服务器时区的当前日期
    private fun serverDate(): LocalDate = LocalDate.now(ZoneId.of(SERVER_TIME_ZONE_ID))

    //服务器时区判断今天星期几,java.time的DAY_OF_WEEK: 1=周一..7=周日
    private fun serverDayOfWeek(): Int = serverDate().dayOfWeek.value

    companion object {
        //国服统一东八区。日期与卡池窗口都必须用同一时区,否则跨零点会算出不同"今天"
        private const val SERVER_TIME_ZONE_ID = "GMT+8"
        private val SERVER_ZONE_OFFSET: ZoneOffset = ZoneOffset.ofHours(8)
    }

    private fun buildDays(): List<DayInfo> {
        val avatars = avatarService.avatarList
        val weapons = weaponService.weaponList
        val now = serverDate()
        val today = now.dayOfWeek.value

        /*
        * 读卡池排期(元数据 GachaEvent.json),用于判断"新品期全素材开放"。
        *
        * ⚠️ 元数据缺失时 events 为空 ⇒ GachaMaterialOpenWindow 返回 false ⇒
        *    退化成"只按周几"的原有行为。**不能**因为读不到排期就把整周判成全开。
        * */
        val events = gachaEventService.eventList

        //记录本次构建依据的日期,供refreshToday判断是否需要跨天重建
        builtDate = now.toString()

        return WeeklyMaterialTable.Day.entries.mapIndexed { index, day ->
            /*
            * 该天对应的**真实日期**(而非"每个星期几")。
            *
            * ⚠️ 卡池窗口是按日期算的,必须逐天用真实日期判定 —— 周一到周日
            *    对应的日期是 now 所在周的周一..周日,不能拿 now 去代表整周。
            *    ISO 周以周一为首,故周日会归到本周(周一起算的第 7 格)。
            * */
            val dateOfCell = now.with(DayOfWeek.MONDAY).plusDays(index.toLong())

            val inGachaWindow = GachaMaterialOpenWindow.isDateInOpenWindow(
                events = events,
                date = dateOfCell,
                zoneOffset = SERVER_ZONE_OFFSET
            )

            val gachaWindowSource = if (inGachaWindow) {
                GachaMaterialOpenWindow.findActiveWindowSource(
                    events = events,
                    date = dateOfCell,
                    zoneOffset = SERVER_ZONE_OFFSET
                )?.Name
            } else {
                null
            }

            val groupsToInfo = fun(group: WeeklyMaterialTable.RotationalGroup): MaterialGroupInfo {
                val material = materialService.getMaterialById(group.topId)

                return MaterialGroupInfo(
                    name = material.Name,
                    iconUrl = material.iconUrl,
                    avatars = avatars.filter { avatar ->
                        avatar.cultivationItems.any { it in group.ids }
                    }.map {
                        ItemRef(it.name, it.iconUrl)
                    },
                    weapons = weapons.filter { weapon ->
                        weapon.cultivationItems.any { it in group.ids }
                    }.map {
                        ItemRef(it.name, it.weaponIconUrl)
                    }
                )
            }

            //生日:BirthMonth/BirthDay与服务器日期同月同日(复用外层now,避免重复取系统时间)
            val birthdays = avatars.filter {
                it.fetterInfo.BirthMonth == now.monthValue && it.fetterInfo.BirthDay == now.dayOfMonth
            }.map { ItemRef(it.name, it.iconUrl) }

            DayInfo(
                label = dayNames[index],
                isToday = today == index + 1,
                isSundayAll = day == WeeklyMaterialTable.Day.SUNDAY,
                //新品期强制全开
                talents = WeeklyMaterialTable.talentGroupsFor(day, forceAllOpen = inGachaWindow)
                    .map(groupsToInfo),
                bosses = WeeklyMaterialTable.bossGroupsFor(day, forceAllOpen = inGachaWindow)
                    .map(groupsToInfo),
                birthdays = birthdays,
                isGachaOpenWindow = inGachaWindow,
                gachaWindowSource = gachaWindowSource
            )
        }
    }
}
