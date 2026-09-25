package com.lianyi.paimonsnotebook.ui.screen.cultivate_project.view

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import com.lianyi.paimonsnotebook.common.core.base.BaseActivity
import com.lianyi.paimonsnotebook.ui.screen.cultivate_project.viewmodel.CultivateProjectScreenViewModel
import com.lianyi.paimonsnotebook.ui.theme.PaimonsNotebookTheme

/*
* 养成计划独立页
*
* 内容已抽到 CultivateProjectPanel,与合并页(GrowScreen)共用。
* 本页保留是为了不破坏已有的快捷方式与桌面组件按类名指向的跳转
* (ShortcutsList 以 target.name 为键持久化)。
* */
class CultivateProjectScreen : BaseActivity() {

    private val viewModel by lazy {
        ViewModelProvider(this)[CultivateProjectScreenViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PaimonsNotebookTheme(this) {
                CultivateProjectPanel(viewModel = viewModel)
            }
        }
    }
}
