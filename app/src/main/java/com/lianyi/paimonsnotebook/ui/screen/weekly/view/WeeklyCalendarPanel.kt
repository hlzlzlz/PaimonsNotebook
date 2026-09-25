package com.lianyi.paimonsnotebook.ui.screen.weekly.view

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.DropdownMenu
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lianyi.core.ui.components.text.InfoText
import com.lianyi.core.ui.components.text.PrimaryText
import com.lianyi.paimonsnotebook.common.components.lazy.ContentSpacerLazyColumn
import com.lianyi.paimonsnotebook.common.components.lazy.ContentSpacerLazyVerticalGrid
import com.lianyi.paimonsnotebook.common.components.layout.column.TabBarColumnLayout
import com.lianyi.paimonsnotebook.common.components.loading.ContentLoadingLayout
import com.lianyi.paimonsnotebook.common.components.media.NetworkImage
import com.lianyi.paimonsnotebook.common.components.placeholder.ErrorPlaceholder
import com.lianyi.paimonsnotebook.common.components.popup.IconTitleInformationPopupWindow
import com.lianyi.paimonsnotebook.common.components.widget.RoundedTag
import com.lianyi.paimonsnotebook.common.extension.modifier.radius.radius
import com.lianyi.paimonsnotebook.ui.screen.items.components.item.list_card.ItemGridListCard
import com.lianyi.paimonsnotebook.ui.screen.items.components.item.material.materialTitle
import com.lianyi.paimonsnotebook.ui.screen.items.data.ItemListCardData
import com.lianyi.paimonsnotebook.ui.screen.items.viewmodel.screen.CultivationMaterialScreenViewModel
import com.lianyi.paimonsnotebook.ui.screen.weekly.viewmodel.WeeklyCalendarScreenViewModel
import com.lianyi.paimonsnotebook.ui.theme.BackGroundColor
import com.lianyi.paimonsnotebook.ui.theme.CardBackGroundColor

/*
* 素材日历面板
*
* 从 WeeklyCalendarScreen 抽出,使其可被合并页与独立页共用。
*
* statusBarEnabled:合并页里外层已有状态栏占位,必须传 false 否则会重复留白。
* */
@Composable
internal fun WeeklyCalendarPanel(
    viewModel: WeeklyCalendarScreenViewModel,
    materialViewModel: CultivationMaterialScreenViewModel,
    statusBarEnabled: Boolean = true
) {
    TabBarColumnLayout(
        tabs = viewModel.tabs,
        onTabBarSelect = viewModel::onTabIndexChange,
        statusBarEnabled = statusBarEnabled,
        topSlot = {
            if (viewModel.tabIndex == 1) {
                WeekDropMenu(materialViewModel)
            }
        }
    ) {
        when (viewModel.tabIndex) {
            1 -> {
                CultivationMaterialContent(materialViewModel)
            }

            else -> {
                WeeklyCalendarContent(viewModel)
            }
        }
    }
}

@Composable
private fun WeeklyCalendarContent(viewModel: WeeklyCalendarScreenViewModel) {
    ContentLoadingLayout(
        loadingState = viewModel.loadingState,
        errorContent = {
            ErrorPlaceholder(
                text = viewModel.errorMessage.ifBlank { "素材日历加载失败" }
            )
        }
    ) {
        ContentSpacerLazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(BackGroundColor),
            contentPadding = PaddingValues(12.dp, 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            statusBarPaddingEnabled = false
        ) {
            items(viewModel.days, key = { it.label }) { day ->
                DayCard(day)
            }
        }
    }
}

@Composable
private fun CultivationMaterialContent(materialViewModel: CultivationMaterialScreenViewModel) {
    ContentLoadingLayout(
        loadingState = materialViewModel.loadingState,
        errorContent = {
            ErrorPlaceholder(
                text = materialViewModel.errorMessage.ifBlank { "养成材料加载失败" }
            )
        }
    ) {
        Crossfade(targetState = materialViewModel.currentPageIndex, label = "") {
            ContentSpacerLazyVerticalGrid(
                columns = GridCells.Adaptive(60.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(12.dp, 6.dp),
                modifier = Modifier
                    .fillMaxSize(),
                statusBarPaddingEnabled = false
            ) {
                when (it) {
                    1 -> {
                        materialViewModel.weaponList.forEach { pair ->
                            materialTitle(
                                pair.first,
                                materialViewModel::onClickMaterialItem
                            )

                            items(pair.second, key = { it.id }) { weapon ->
                                ItemGridListCard(
                                    data = weapon,
                                    itemListCardData = ItemListCardData(
                                        iconUrl = weapon.iconUrl,
                                        quality = weapon.rankLevel
                                    ),
                                    dataContent = weapon.name,
                                    onClick = materialViewModel::onClickWeapon
                                )
                            }
                        }
                    }

                    else -> {
                        materialViewModel.avatarList.forEach { pair ->
                            materialTitle(
                                pair.first,
                                materialViewModel::onClickMaterialItem
                            )

                            items(pair.second, key = { it.id }) { avatar ->
                                ItemGridListCard(
                                    data = avatar,
                                    itemListCardData = ItemListCardData(
                                        iconUrl = avatar.iconUrl,
                                        quality = avatar.starCount
                                    ),
                                    dataContent = avatar.name,
                                    onClick = materialViewModel::onClickAvatar
                                )
                            }
                        }
                    }
                }
            }
        }

        if (materialViewModel.showMaterialPopupWindow) {
            IconTitleInformationPopupWindow(
                data = materialViewModel.popupWindowData,
                popupProvider = materialViewModel.popupWindowProvider,
                onDismissRequest = materialViewModel::dismissPopupWindow
            )
        }
    }
}

//切换养成材料按星期几分组(原养成材料页顶栏下拉)
@Composable
private fun WeekDropMenu(materialViewModel: CultivationMaterialScreenViewModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp, 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End
    ) {
        Box {
            Text(
                text = materialViewModel.weekName,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                modifier = Modifier
                    .requiredWidthIn(60.dp, 180.dp)
                    .radius(2.dp)
                    .clickable {
                        materialViewModel.showDropMenu()
                    }
                    .padding(2.dp),
                textAlign = TextAlign.End
            )

            DropdownMenu(
                expanded = materialViewModel.showDropMenu,
                onDismissRequest = materialViewModel::dismissDropMenu
            ) {
                materialViewModel.dropMenuList.forEach { pair ->
                    Text(
                        text = pair.first,
                        fontSize = 18.sp,
                        modifier = Modifier
                            .width(100.dp)
                            .clickable {
                                materialViewModel.onSelectDropMenuItem(pair)
                            }
                            .padding(6.dp, 3.dp)
                    )
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
                        RoundedCornerShape(6.dp)
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
                RoundedTag(
                    text = "今天",
                    backGroundColor = Color(0xFFFFB300),
                    textColor = Color.White
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            /*
            * 全开放的两种原因分开展示:
            *   周日 = 游戏常态;卡池窗口 = 限时。文案不同,否则用户会以为
            *   "今天怎么突然全开了"。
            * */
            if (day.isSundayAll) {
                Text(
                    text = "全部素材本开放",
                    fontSize = 11.sp,
                    color = Color(0xFF2E7D32)
                )
            } else if (day.isGachaOpenWindow) {
                /*
                * ⚠️ 用 widthIn 限宽而非 weight:同一 Row 里已有一个
                *    `Spacer(weight(1f))` 在把内容推到右侧,再给这段文字加
                *    weight 会变成"两者平分剩余空间",标签就贴不到右边缘了。
                *    限宽 + 单行省略即可保证 360dp 下不溢出。
                * */
                Text(
                    text = if (day.gachaWindowSource.isNullOrBlank()) {
                        "新品期·全开"
                    } else {
                        "新品期·全开(${day.gachaWindowSource})"
                    },
                    fontSize = 11.sp,
                    color = Color(0xFFE65100),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 150.dp)
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
