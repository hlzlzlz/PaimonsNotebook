package com.lianyi.paimonsnotebook.ui.screen.wiki.view

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import com.lianyi.paimonsnotebook.common.components.spacer.StatusBarPaddingSpacer
import com.lianyi.paimonsnotebook.common.core.base.BaseActivity
import com.lianyi.paimonsnotebook.common.extension.modifier.radius.radius
import com.lianyi.paimonsnotebook.ui.screen.items.components.state.ItemScreenLoadingState
import com.lianyi.paimonsnotebook.ui.screen.items.viewmodel.MonsterScreenViewModel
import com.lianyi.paimonsnotebook.ui.screen.items.viewmodel.screen.AvatarScreenViewModel
import com.lianyi.paimonsnotebook.ui.screen.items.viewmodel.screen.ReliquaryScreenViewModel
import com.lianyi.paimonsnotebook.ui.screen.items.viewmodel.screen.WeaponScreenViewModel
import com.lianyi.paimonsnotebook.ui.screen.items.widget.AvatarWikiContent
import com.lianyi.paimonsnotebook.ui.screen.items.widget.MonsterWikiContent
import com.lianyi.paimonsnotebook.ui.screen.items.widget.ReliquaryWikiContent
import com.lianyi.paimonsnotebook.ui.screen.items.widget.WeaponWikiContent
import com.lianyi.paimonsnotebook.ui.theme.BackGroundColor
import com.lianyi.paimonsnotebook.ui.theme.Black
import com.lianyi.paimonsnotebook.ui.theme.Black_40
import com.lianyi.paimonsnotebook.ui.theme.CardBackGroundColor
import com.lianyi.paimonsnotebook.ui.theme.PaimonsNotebookTheme

/*
* 资料库(合并 角色/武器/怪物/圣遗物 四个资料页)
*
* 采用两级结构:第一行切换四类资料,第二行是各自的标签栏。
*
* ⚠️ 为什么不用一条标签栏装下所有标签:四个页面的标签语义完全不同
* (角色的"属性/技能/命之座"、武器的"属性/精炼"、怪物的"属性/掉落/资料"、
* 圣遗物的"套装效果/资料"),平铺会出现多个重名"属性"且总数达 14 个,
* 用户无法分辨哪一个是哪一类。两级结构下第一级是"我在看哪类资料",
* 第二级才是该类内部的维度。
*
* ⚠️ 四个子 ViewModel 都在本页创建并常驻(切类别不销毁),来回切换不丢
*    已加载数据与列表滚动位置。
*
* 原有四个 Activity 全部保留为可独立启动的入口 —— 快捷方式与桌面组件
* 按类名持久化跳转(见 ShortcutsManagerScreenViewModel:89),删类会让老
* 用户的快捷方式失效。
* */
class WikiScreen : BaseActivity() {

    /*
    * ⚠️ 四个 ViewModel 都在本页创建并常驻(切类别不销毁),这样来回切换
    *    不丢已加载数据与列表状态。代价是打开本页会同时加载四份元数据,
    *    但它们都是本地文件读取且已在子线程,可接受。
    *
    * ⚠️ 角色/武器需要 init(intent) 才能选出"当前项"(它们会读 intent 里的
    *    item_id 或上次查看记录),资料库没有 intent,故传一个空 Intent,
    *    让它们回退到"上次查看的角色/武器"。
    * */
    private val avatarViewModel by lazy {
        ViewModelProvider(this)[AvatarScreenViewModel::class.java]
    }

    private val weaponViewModel by lazy {
        ViewModelProvider(this)[WeaponScreenViewModel::class.java]
    }

    private val monsterViewModel by lazy {
        ViewModelProvider(this)[MonsterScreenViewModel::class.java]
    }

    private val reliquaryViewModel by lazy {
        ViewModelProvider(this)[ReliquaryScreenViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        avatarViewModel.init(Intent())
        weaponViewModel.init(Intent())

        setContent {
            PaimonsNotebookTheme(this, lightStatusBar = false) {
                var currentSection by remember { mutableIntStateOf(0) }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(BackGroundColor)
                ) {
                    StatusBarPaddingSpacer()

                    SectionSwitcher(
                        sections = arrayOf("角色", "武器", "怪物", "圣遗物"),
                        currentIndex = currentSection,
                        onSelect = { currentSection = it }
                    )

                    /*
                    * ⚠️ 每个类别都要各自包一层 ItemScreenLoadingState。
                    *
                    * 四个内容组件内部都有 `currentItem ?: return`,若元数据缺失
                    * (loadingState = Error)或列表为空,它会**什么都不画** ——
                    * 那正是 1.8.23 修过的"失败/空态变成纯白屏"同类问题。
                    * 各 ViewModel 的 loadingState 是独立的,所以状态也必须按
                    * 类别分别处理,不能在外层统一包一个。
                    * */
                    when (currentSection) {
                        0 -> ItemScreenLoadingState(loadingState = avatarViewModel.loadingState) {
                            AvatarWikiContent(
                                viewModel = avatarViewModel,
                                statusBarPaddingEnabled = false
                            )
                        }

                        1 -> ItemScreenLoadingState(loadingState = weaponViewModel.loadingState) {
                            WeaponWikiContent(
                                viewModel = weaponViewModel,
                                statusBarPaddingEnabled = false
                            )
                        }

                        2 -> ItemScreenLoadingState(
                            loadingState = monsterViewModel.loadingState,
                            errorText = monsterViewModel.errorMessage.ifBlank { "缺少怪物元数据" },
                            emptyText = "没有可显示的怪物资料"
                        ) {
                            MonsterWikiContent(
                                viewModel = monsterViewModel,
                                statusBarPaddingEnabled = false
                            )
                        }

                        else -> ItemScreenLoadingState(
                            loadingState = reliquaryViewModel.loadingState,
                            errorText = reliquaryViewModel.errorMessage.ifBlank { "缺少圣遗物元数据" },
                            emptyText = "没有可显示的圣遗物资料"
                        ) {
                            ReliquaryWikiContent(
                                viewModel = reliquaryViewModel,
                                statusBarPaddingEnabled = false
                            )
                        }
                    }
                }
            }
        }
    }
}

/*
* 类别切换器(两级结构的第一级)
*
* 用分段控件样式与下方标签栏区分开,避免两行都是"可点文字"时点错。
* ⚠️ 四个类别名很短(2~3 字),360dp 下放得下,故用等宽 weight 而非横向滚动。
* */
@Composable
private fun SectionSwitcher(
    sections: Array<String>,
    currentIndex: Int,
    onSelect: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .radius(8.dp)
            .background(CardBackGroundColor)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        sections.forEachIndexed { index, name ->
            val selected = index == currentIndex

            Text(
                text = name,
                fontSize = 14.sp,
                color = if (selected) Black else Black_40,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier
                    .weight(1f)
                    .radius(6.dp)
                    .background(if (selected) BackGroundColor else CardBackGroundColor)
                    .clickable { onSelect(index) }
                    .padding(vertical = 8.dp),
                textAlign = TextAlign.Center
            )
        }
    }
}
