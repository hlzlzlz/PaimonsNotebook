package com.lianyi.paimonsnotebook.ui.screen.weekly.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lianyi.paimonsnotebook.common.util.enums.LoadingState
import com.lianyi.paimonsnotebook.common.util.weekly.WeeklyMaterialTable
import com.lianyi.paimonsnotebook.common.web.hutao.genshin.avatar.AvatarData
import com.lianyi.paimonsnotebook.common.web.hutao.genshin.common.service.AvatarService
import com.lianyi.paimonsnotebook.common.web.hutao.genshin.common.service.MaterialService
import com.lianyi.paimonsnotebook.common.web.hutao.genshin.common.service.WeaponService
import com.lianyi.paimonsnotebook.common.web.hutao.genshin.weapon.WeaponData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

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
        val birthdays: List<ItemRef>
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

    //0刷新日历 1养成材料
    var tabIndex by mutableStateOf(0)
        private set

    val tabs = arrayOf("刷新日历", "养成材料")

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
    private fun serverDate(): LocalDate = LocalDate.now(ZoneId.of("GMT+8"))

    //服务器时区判断今天星期几,java.time的DAY_OF_WEEK: 1=周一..7=周日
    private fun serverDayOfWeek(): Int = serverDate().dayOfWeek.value

    private fun buildDays(): List<DayInfo> {
        val avatars = avatarService.avatarList
        val weapons = weaponService.weaponList
        val now = serverDate()
        val today = now.dayOfWeek.value

        //记录本次构建依据的日期,供refreshToday判断是否需要跨天重建
        builtDate = now.toString()

        return WeeklyMaterialTable.Day.entries.mapIndexed { index, day ->
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
                talents = WeeklyMaterialTable.talentGroupsFor(day).map(groupsToInfo),
                bosses = WeeklyMaterialTable.bossGroupsFor(day).map(groupsToInfo),
                birthdays = birthdays
            )
        }
    }
}
