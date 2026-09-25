package com.lianyi.paimonsnotebook.ui.screen.weekly.view

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import com.lianyi.paimonsnotebook.common.core.base.BaseActivity
import com.lianyi.paimonsnotebook.ui.screen.items.viewmodel.screen.CultivationMaterialScreenViewModel
import com.lianyi.paimonsnotebook.ui.screen.weekly.viewmodel.WeeklyCalendarScreenViewModel
import com.lianyi.paimonsnotebook.ui.theme.PaimonsNotebookTheme

/*
* 素材日历独立页
*
* 内容已抽到 WeeklyCalendarPanel,与合并页(GrowScreen)共用。
* 本页保留是为了不破坏已有的快捷方式与桌面组件按类名指向的跳转
* (ShortcutsList 以 target.name 为键持久化)。
* */
class WeeklyCalendarScreen : BaseActivity() {

    private val viewModel by lazy {
        ViewModelProvider(this)[WeeklyCalendarScreenViewModel::class.java]
    }

    //养成材料并入后复用其ViewModel
    private val materialViewModel by lazy {
        ViewModelProvider(this)[CultivationMaterialScreenViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PaimonsNotebookTheme(this) {
                WeeklyCalendarPanel(
                    viewModel = viewModel,
                    materialViewModel = materialViewModel
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        //应用可能跨天未重启,回到本页时重算"今天"高亮与生日(纯本地数据,开销可忽略)
        viewModel.refreshToday()
    }
}
