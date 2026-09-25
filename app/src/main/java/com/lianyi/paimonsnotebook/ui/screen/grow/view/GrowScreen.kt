package com.lianyi.paimonsnotebook.ui.screen.grow.view

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import com.lianyi.paimonsnotebook.ui.screen.cultivate_project.view.CultivateProjectPanel
import com.lianyi.paimonsnotebook.ui.screen.cultivate_project.viewmodel.CultivateProjectScreenViewModel
import com.lianyi.paimonsnotebook.ui.screen.items.viewmodel.screen.CultivationMaterialScreenViewModel
import com.lianyi.paimonsnotebook.ui.screen.weekly.view.WeeklyCalendarPanel
import com.lianyi.paimonsnotebook.ui.screen.weekly.viewmodel.WeeklyCalendarScreenViewModel
import com.lianyi.paimonsnotebook.ui.theme.BackGroundColor
import com.lianyi.paimonsnotebook.ui.theme.Black
import com.lianyi.paimonsnotebook.ui.theme.Black_40
import com.lianyi.paimonsnotebook.ui.theme.CardBackGroundColor
import com.lianyi.paimonsnotebook.ui.theme.PaimonsNotebookTheme

/*
* 养成页(合并 养成计划 + 素材日历)
*
* 采用**两级结构**:第一行切换"养成计划 / 素材日历",第二行是各自的标签栏。
* 两者原本各有 2 个标签,平铺会变成 4 个且语义混杂(一个是我的计划,一个是
* 全服可刷素材),分区后层级更清楚。
*
* ⚠️ 两个子 ViewModel 都在本页创建并常驻,切板块时不销毁(见 CombatRecordScreen 同类注释)。
* */
class GrowScreen : BaseActivity() {

    private val cultivateViewModel by lazy {
        ViewModelProvider(this)[CultivateProjectScreenViewModel::class.java]
    }

    private val weeklyViewModel by lazy {
        ViewModelProvider(this)[WeeklyCalendarScreenViewModel::class.java]
    }

    private val materialViewModel by lazy {
        ViewModelProvider(this)[CultivationMaterialScreenViewModel::class.java]
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
                        sections = arrayOf("养成计划", "素材日历"),
                        currentIndex = currentSection,
                        onSelect = { currentSection = it }
                    )

                    Box(modifier = Modifier.fillMaxSize()) {
                        if (currentSection == 0) {
                            CultivateProjectPanel(
                                viewModel = cultivateViewModel,
                                statusBarEnabled = false
                            )
                        } else {
                            WeeklyCalendarPanel(
                                viewModel = weeklyViewModel,
                                materialViewModel = materialViewModel,
                                statusBarEnabled = false
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        //应用可能跨天未重启,回到本页时重算"今天"高亮与生日(纯本地数据,开销可忽略)
        weeklyViewModel.refreshToday()
    }
}

/*
* 板块切换器(两级结构的第一级)
*
* 用卡片底色 + 圆角做成分段控件的样子,避免与下方标签栏混淆
* (两行都是"可点的文字"时很容易点错)。
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
