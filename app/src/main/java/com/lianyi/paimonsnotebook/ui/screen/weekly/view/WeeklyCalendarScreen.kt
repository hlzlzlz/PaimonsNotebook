package com.lianyi.paimonsnotebook.ui.screen.weekly.view

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import com.lianyi.core.ui.components.text.InfoText
import com.lianyi.core.ui.components.text.PrimaryText
import com.lianyi.paimonsnotebook.common.components.lazy.ContentSpacerLazyColumn
import com.lianyi.paimonsnotebook.common.components.loading.ContentLoadingLayout
import com.lianyi.paimonsnotebook.common.components.media.NetworkImage
import com.lianyi.paimonsnotebook.common.core.base.BaseActivity
import com.lianyi.paimonsnotebook.common.extension.modifier.radius.radius
import com.lianyi.paimonsnotebook.ui.screen.weekly.viewmodel.WeeklyCalendarScreenViewModel
import com.lianyi.paimonsnotebook.ui.theme.BackGroundColor
import com.lianyi.paimonsnotebook.ui.theme.CardBackGroundColor
import com.lianyi.paimonsnotebook.ui.theme.PaimonsNotebookTheme

/*
* 素材刷新日历页
* 按服务器时区展示一周七天可刷的天赋书/周本材料与当日角色生日
* */
class WeeklyCalendarScreen : BaseActivity() {

    private val viewModel by lazy {
        ViewModelProvider(this)[WeeklyCalendarScreenViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PaimonsNotebookTheme(this) {
                ContentLoadingLayout(loadingState = viewModel.loadingState) {
                    ContentSpacerLazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(BackGroundColor),
                        contentPadding = PaddingValues(12.dp, 6.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(viewModel.days, key = { it.label }) { day ->
                            DayCard(day)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun DayCard(day: WeeklyCalendarScreenViewModel.DayInfo) {
        Column(
            modifier = Modifier
                .radius(6.dp)
                .background(CardBackGroundColor)
                .fillMaxWidth()
                .then(
                    if (day.isToday) {
                        Modifier.border(
                            2.dp,
                            Color(0xFFFFB300),
                            androidx.compose.foundation.shape.RoundedCornerShape(6.dp)
                        )
                    } else {
                        Modifier
                    }
                )
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PrimaryText(
                    text = day.label,
                    textSize = 15.sp
                )

                Spacer(modifier = Modifier.width(8.dp))

                if (day.isToday) {
                    Text(
                        text = "今天",
                        fontSize = 11.sp,
                        color = Color.White,
                        modifier = Modifier
                            .radius(2.dp)
                            .background(Color(0xFFFFB300))
                            .padding(4.dp, 1.dp)
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                if (day.isSundayAll) {
                    Text(
                        text = "全部素材本开放",
                        fontSize = 11.sp,
                        color = Color(0xFF2E7D32)
                    )
                }
            }

            if (day.birthdays.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "生日",
                        fontSize = 12.sp,
                        color = Color(0xFFE91E63),
                        modifier = Modifier.width(52.dp)
                    )

                    day.birthdays.take(8).forEach { item ->
                        ItemIcon(item)
                    }
                }
            }

            GroupSection("天赋素材", day.talents)
            GroupSection("周本素材", day.bosses)
        }
    }

    @Composable
    private fun GroupSection(title: String, groups: List<WeeklyCalendarScreenViewModel.MaterialGroupInfo>) {
        if (groups.isEmpty()) {
            return
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            InfoText(text = title)

            groups.forEach { group ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "·",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        modifier = Modifier.width(12.dp)
                    )

                    NetworkImage(
                        url = group.iconUrl,
                        modifier = Modifier.size(28.dp)
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    Text(
                        text = group.name,
                        fontSize = 12.sp,
                        modifier = Modifier.width(96.dp)
                    )

                    (group.avatars.take(6) + group.weapons.take(4)).forEach { item ->
                        ItemIcon(item)
                    }
                }
            }
        }
    }

    @Composable
    private fun ItemIcon(item: WeeklyCalendarScreenViewModel.ItemRef) {
        NetworkImage(
            url = item.iconUrl,
            modifier = Modifier
                .padding(1.dp)
                .size(24.dp)
                .clip(CircleShape)
        )
    }
}
