package com.lianyi.paimonsnotebook.ui.screen.gacha.components.page

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lianyi.paimonsnotebook.common.components.media.NetworkImage
import com.lianyi.paimonsnotebook.common.extension.modifier.radius.radius
import com.lianyi.paimonsnotebook.ui.screen.gacha.service.GachaPityCalculator
import com.lianyi.paimonsnotebook.ui.theme.Black_60
import com.lianyi.paimonsnotebook.ui.theme.Success
import com.lianyi.paimonsnotebook.ui.theme.White

private val GroupOrder = listOf("五星角色", "四星角色", "五星武器", "四星武器")

/*
* UP物品复刻倒计时页
* 按四类分组展示每个UP物品距上次进入卡池的天数与历史次数
* */
@Composable
internal fun GachaCountdownPage(
    groups: Map<String, List<GachaPityCalculator.CountdownEntry>>?,
    getItemName: (Int) -> String?,
    getItemIconUrl: (Int) -> String?
) {
    val data = groups ?: return

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                text = "距上次进入UP池的天数,用于观察复刻规律",
                fontSize = 12.sp,
                color = Black_60,
                modifier = Modifier.padding(12.dp, 8.dp, 12.dp, 0.dp)
            )
        }

        GroupOrder.forEach { groupName ->
            val list = data[groupName] ?: return@forEach

            item(key = "group_$groupName") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp, 4.dp)
                        .radius(2.dp)
                        .background(White)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = groupName,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    list.forEach { entry ->
                        CountdownRow(entry, getItemName, getItemIconUrl)
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.size(8.dp))
        }
    }
}

@Composable
private fun CountdownRow(
    entry: GachaPityCalculator.CountdownEntry,
    getItemName: (Int) -> String?,
    getItemIconUrl: (Int) -> String?
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NetworkImage(
            url = getItemIconUrl(entry.itemId) ?: "",
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.width(8.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = getItemName(entry.itemId) ?: "${entry.itemId}",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )

            Text(
                text = "${entry.versionText} · 第${entry.appearances}次UP · 上次至 ${entry.lastTime}",
                fontSize = 11.sp,
                color = Black_60
            )
        }

        if (entry.isCurrent) {
            Text(
                text = "本期",
                fontSize = 13.sp,
                color = Color.White,
                modifier = Modifier
                    .radius(2.dp)
                    .background(Success)
                    .padding(6.dp, 2.dp)
            )
        } else {
            Text(
                text = "距今 ${entry.daysSinceLast} 天",
                fontSize = 13.sp,
                color = Black_60
            )
        }
    }
}
