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

    //0刷新日历 1养成材料
    var tabIndex by mutableStateOf(0)
        private set

    val tabs = arrayOf("刷新日历", "养成材料")

    fun onTabIndexChange(index: Int) {
        tabIndex = index
    }

    private val dayNames = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

    private val avatarService by lazy {
        AvatarService { loadingState = LoadingState.Error }
    }

    private val weaponService by lazy {
        WeaponService { loadingState = LoadingState.Error }
    }

    private val materialService by lazy {
        MaterialService { loadingState = LoadingState.Error }
    }

    init {
        viewModelScope.launch {
            val dayInfos = withContext(Dispatchers.IO) { buildDays() }

            days = dayInfos
            loadingState = if (dayInfos.isEmpty()) LoadingState.Error else LoadingState.Success
        }
    }

    //上次构建days时的服务器星期(1=周一..7=周日),用于判断是否需要跨天重建
    private var builtDayOfWeek = 0

    /*
    * 重新计算"今天"高亮与生日
    * 页面原先只在init里算一次,应用跨天后仍显示旧日期:
    * "今天"高亮错位、当天生日不显示。本方法数据全部来自本地元数据(无网络),
    * 由界面在onResume时调用,开销可忽略。
    * */
    fun refreshToday() {
        if (days.isNotEmpty() && builtDayOfWeek == serverDayOfWeek()) {
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

    //服务器时区判断今天星期几,java.time的DAY_OF_WEEK: 1=周一..7=周日
    private fun serverDayOfWeek(): Int =
        LocalDate.now(ZoneId.of("GMT+8")).dayOfWeek.value

    private fun buildDays(): List<DayInfo> {
        val avatars = avatarService.avatarList
        val weapons = weaponService.weaponList
        val today = serverDayOfWeek()

        //记录本次构建依据的星期,供refreshToday判断是否需要跨天重建
        builtDayOfWeek = today

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

            //生日:BirthMonth/BirthDay与服务器日期同月同日
            val now = LocalDate.now(ZoneId.of("GMT+8"))
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
