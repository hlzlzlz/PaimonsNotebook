package com.lianyi.paimonsnotebook.ui.screen.abyss.view

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import com.lianyi.paimonsnotebook.common.core.base.BaseActivity
import com.lianyi.paimonsnotebook.ui.screen.abyss.viewmodel.AbyssScreenViewModel
import com.lianyi.paimonsnotebook.ui.theme.PaimonsNotebookTheme

/*
* 深境螺旋独立页
*
* 内容已抽到 AbyssPanel,与「战斗记录」合并页(CombatRecordScreen)共用。
* 本页保留是为了不破坏已有的快捷方式与桌面组件按类名指向的跳转
* (ShortcutsList 以 target.name 为键持久化)。
* */
class AbyssScreen : BaseActivity() {
    private val viewModel by lazy {
        ViewModelProvider(this)[AbyssScreenViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PaimonsNotebookTheme(this) {
                AbyssPanel(viewModel = viewModel)
            }
        }
    }
}
