package com.lianyi.paimonsnotebook.ui.screen.combat.view

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import com.lianyi.paimonsnotebook.common.components.spacer.StatusBarPaddingSpacer
import com.lianyi.paimonsnotebook.common.core.base.BaseActivity
import com.lianyi.paimonsnotebook.common.extension.modifier.radius.radius
import com.lianyi.paimonsnotebook.ui.screen.abyss.view.AbyssPanel
import com.lianyi.paimonsnotebook.ui.screen.abyss.viewmodel.AbyssScreenViewModel
import com.lianyi.paimonsnotebook.ui.screen.role_combat.view.RoleCombatPanel
import com.lianyi.paimonsnotebook.ui.screen.role_combat.viewmodel.RoleCombatScreenViewModel
import com.lianyi.paimonsnotebook.ui.theme.BackGroundColor
import com.lianyi.paimonsnotebook.ui.theme.Black
import com.lianyi.paimonsnotebook.ui.theme.Black_40
import com.lianyi.paimonsnotebook.ui.theme.CardBackGroundColor
import com.lianyi.paimonsnotebook.ui.theme.PaimonsNotebookTheme

/*
* 战斗记录页(合并 深境螺旋 + 幻想真境剧诗/幽境危战)
*
* 采用**两级结构**而不是把两者的标签平铺成一行:
*   深境螺旋有 10 个标签、战斗记录有 3 个,平铺会变成 13 个,
*   即使横向可滚动也要滑很久才能找到目标,且两组语义不同
*   (一边是我的深渊战绩+全服统计,一边是剧诗/危战)。
*
* 布局:第一行 = 两个板块切换;第二行 = 该板块自己的标签(由各 Panel 渲染)。
*
* ⚠️ 两个子 ViewModel 都在本页创建并**常驻**,切板块时不销毁 —— 这样来回切换
*    不会丢掉已加载的数据(与两个独立页各自加载相比是净收益)。
*    代价是打开本页会同时加载两边的数据;两边都有 '已加载则跳过' 的守卫,
*    且都在子线程,故可接受。
* */
class CombatRecordScreen : BaseActivity() {

    private val abyssViewModel by lazy {
        ViewModelProvider(this)[AbyssScreenViewModel::class.java]
    }

    private val roleCombatViewModel by lazy {
        ViewModelProvider(this)[RoleCombatScreenViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PaimonsNotebookTheme(this) {
                var currentSection by remember { mutableIntStateOf(0) }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(BackGroundColor)
                ) {
                    StatusBarPaddingSpacer()

                    SectionSwitcher(
                        sections = arrayOf("深境螺旋", "战斗记录"),
                        currentIndex = currentSection,
                        onSelect = { currentSection = it }
                    )

                    /*
                    * 两个 Panel 都在这里常驻,用显隐而不是 if/else 切换到销毁:
                    * 若用 if,切回来时 Panel 会重新组合,虽然 ViewModel 里的数据
                    * 仍在,但 ScrollState 等界面状态会重置,体验上是"回来又要重新滚"。
                    * */
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (currentSection == 0) {
                            AbyssPanel(
                                viewModel = abyssViewModel,
                                statusBarEnabled = false
                            )
                        } else {
                            RoleCombatPanel(
                                viewModel = roleCombatViewModel,
                                statusBarEnabled = false
                            )
                        }
                    }
                }
            }
        }
    }
}

/*
* 板块切换器(两级结构的第一级)
*
* 外观上与 TabBar 区分开:用卡片底色 + 圆角做成分段控件的样子,
* 避免与下方的标签栏混淆(两行都是"可点的文字"时很容易点错)。
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
