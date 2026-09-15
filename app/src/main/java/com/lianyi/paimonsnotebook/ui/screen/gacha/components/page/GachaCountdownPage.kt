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
import com.lianyi.core.ui.components.text.InfoText
import com.lianyi.core.ui.components.text.PrimaryText
import com.lianyi.paimonsnotebook.common.components.lazy.ContentSpacerLazyColumn
import com.lianyi.paimonsnotebook.common.components.media.NetworkImage
import com.lianyi.paimonsnotebook.common.components.widget.RoundedTag
import com.lianyi.paimonsnotebook.common.extension.modifier.radius.radius
import com.lianyi.paimonsnotebook.ui.screen.gacha.service.GachaPityCalculator
import com.lianyi.paimonsnotebook.ui.theme.CardBackGroundColor
import com.lianyi.paimonsnotebook.ui.theme.Success

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

    ContentSpacerLazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            InfoText(
                text = "距上次进入UP池的天数,用于观察复刻规律",
                modifier = Modifier.padding(12.dp, 8.dp, 12.dp, 0.dp)
            )
        }

        GroupOrder.forEach { groupName ->
            val list = data[groupName] ?: return@forEach

            item(key = "group_$groupName") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp, 2.dp)
                        .radius(6.dp)
                        .background(CardBackGroundColor)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PrimaryText(
                        text = groupName,
                        textSize = 15.sp
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
            PrimaryText(
                text = getItemName(entry.itemId) ?: "${entry.itemId}",
                textSize = 14.sp,
                bold = false
            )

            InfoText(
                text = "${entry.versionText} · 第${entry.appearances}次UP · 上次至 ${entry.lastTime}",
                fontSize = 11.sp
            )
        }

        if (entry.isCurrent) {
            RoundedTag(
                text = "本期",
                backGroundColor = Success,
                textColor = Color.White
            )
        } else {
            InfoText(text = "距今 ${entry.daysSinceLast} 天", fontSize = 13.sp)
        }
    }
}
