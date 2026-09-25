package com.lianyi.paimonsnotebook.ui.screen.role_combat.view

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import com.lianyi.paimonsnotebook.common.core.base.BaseActivity
import com.lianyi.paimonsnotebook.ui.screen.role_combat.viewmodel.RoleCombatScreenViewModel
import com.lianyi.paimonsnotebook.ui.theme.PaimonsNotebookTheme

/*
* 战斗记录独立页
*
* 内容已抽到 RoleCombatPanel,与深境螺旋合并页(CombatRecordScreen)共用。
* 本页保留是为了不破坏已有的快捷方式与桌面组件按类名指向的跳转
* (ShortcutsList 以 target.name 为键持久化)。
* */
class RoleCombatScreen : BaseActivity() {
    private val viewModel by lazy {
        ViewModelProvider(this)[RoleCombatScreenViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PaimonsNotebookTheme(this) {
                RoleCombatPanel(viewModel = viewModel)
            }
        }
    }
}
