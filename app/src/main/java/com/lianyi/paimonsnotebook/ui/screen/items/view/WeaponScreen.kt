package com.lianyi.paimonsnotebook.ui.screen.items.view

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import com.lianyi.paimonsnotebook.ui.screen.items.components.state.ItemScreenLoadingState
import com.lianyi.paimonsnotebook.ui.screen.items.viewmodel.screen.WeaponScreenViewModel
import com.lianyi.paimonsnotebook.ui.screen.items.widget.WeaponWikiContent
import com.lianyi.paimonsnotebook.ui.theme.PaimonsNotebookTheme

/*
* 武器资料独立页
*
* 内容已抽到 WeaponWikiContent,与资料库(WikiScreen)共用。
* 本页保留是为了不破坏已有的快捷方式与桌面组件按类名指向的跳转
* (ShortcutsList 以 target.name 为键持久化)。
* */
class WeaponScreen : ComponentActivity() {

    private val viewModel by lazy {
        ViewModelProvider(this)[WeaponScreenViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel.init(intent)

        setContent {
            PaimonsNotebookTheme(this, lightStatusBar = false) {
                ItemScreenLoadingState(loadingState = viewModel.loadingState) {
                    WeaponWikiContent(viewModel = viewModel)
                }
            }
        }
    }
}
