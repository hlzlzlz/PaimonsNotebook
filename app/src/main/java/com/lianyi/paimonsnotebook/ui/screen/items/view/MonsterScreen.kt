package com.lianyi.paimonsnotebook.ui.screen.items.view

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import com.lianyi.paimonsnotebook.ui.screen.items.components.state.ItemScreenLoadingState
import com.lianyi.paimonsnotebook.ui.screen.items.viewmodel.MonsterScreenViewModel
import com.lianyi.paimonsnotebook.ui.screen.items.widget.MonsterWikiContent
import com.lianyi.paimonsnotebook.ui.theme.PaimonsNotebookTheme

/*
* 怪物资料独立页
*
* 内容已抽到 MonsterWikiContent,与资料库(WikiScreen)共用。
* 本页保留是为了不破坏已有的快捷方式与桌面组件按类名指向的跳转
* (ShortcutsList 以 target.name 为键持久化)。
* */
class MonsterScreen : ComponentActivity() {

    private val viewModel by lazy {
        ViewModelProvider(this)[MonsterScreenViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PaimonsNotebookTheme(this, lightStatusBar = false) {
                ItemScreenLoadingState(loadingState = viewModel.loadingState) {
                    MonsterWikiContent(viewModel = viewModel)
                }
            }
        }
    }
}
