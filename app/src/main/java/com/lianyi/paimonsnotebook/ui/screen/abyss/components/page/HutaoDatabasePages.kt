package com.lianyi.paimonsnotebook.ui.screen.abyss.components.page

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import com.lianyi.paimonsnotebook.common.components.media.NetworkImage
import com.lianyi.paimonsnotebook.common.extension.modifier.radius.radius
import com.lianyi.paimonsnotebook.common.web.hutao.statistics.HutaoAvatarFloorRateData
import com.lianyi.paimonsnotebook.common.web.hutao.statistics.HutaoHoldingRateEntry
import com.lianyi.paimonsnotebook.common.web.hutao.statistics.HutaoOverviewData
import com.lianyi.paimonsnotebook.common.web.hutao.statistics.HutaoTeamCombinationData
import com.lianyi.paimonsnotebook.ui.theme.Black_60
import com.lianyi.paimonsnotebook.ui.theme.White

/*
* 胡桃API全服数据页面(总览/出场率/使用率/配队)
* */
@Composable
internal fun HutaoOverviewPage(overview: HutaoOverviewData?) {
    overview ?: return

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp, 4.dp)
                    .radius(2.dp)
                    .background(White)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "第 ${overview.ScheduleId} 期深渊数据",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )

                OverviewRow("记录总数", "${overview.RecordTotal}")
                OverviewRow("深渊记录数", "${overview.SpiralAbyssTotal}")
                OverviewRow("通关记录数", "${overview.SpiralAbyssPassed}")
                OverviewRow("满星记录数", "${overview.SpiralAbyssFullStar}")
                OverviewRow("总星数", "${overview.SpiralAbyssStarTotal}")
                OverviewRow("总战斗场次", "${overview.SpiralAbyssBattleTotal}")

                if (overview.TimeAverage > 0) {
                    OverviewRow("平均通关时间", String.format("%.1f 分钟", overview.TimeAverage))
                }
            }
        }
    }
}

@Composable
private fun OverviewRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = Black_60,
            modifier = Modifier.weight(1f)
        )
        Text(text = value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun HutaoAvatarRatePage(
    rates: List<HutaoAvatarFloorRateData>?,
    title: String,
    getAvatar: (Int) -> com.lianyi.paimonsnotebook.common.web.hutao.genshin.avatar.AvatarData?
) {
    val floorRates = rates ?: return

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        floorRates.sortedByDescending { it.Floor }.forEach { floorRate ->
            item(key = "floor_${title}_${floorRate.Floor}") {
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
                        text = "${floorRate.Floor} 层",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    floorRate.Ranks.sortedByDescending { it.Rate }.take(15)
                        .forEachIndexed { index, rank ->
                            val avatar = getAvatar(rank.Item)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${index + 1}",
                                    fontSize = 13.sp,
                                    color = Black_60,
                                    modifier = Modifier.width(24.dp)
                                )

                                NetworkImage(
                                    url = avatar?.iconUrl ?: "",
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                Text(
                                    text = avatar?.name ?: "${rank.Item}",
                                    fontSize = 14.sp,
                                    modifier = Modifier.weight(1f)
                                )

                                Text(
                                    text = String.format("%.2f%%", rank.Rate * 100),
                                    fontSize = 13.sp,
                                    color = Black_60
                                )
                            }
                        }
                }
            }
        }
    }
}

@Composable
internal fun HutaoHoldingRatePage(
    entries: List<HutaoHoldingRateEntry>?,
    getAvatar: (Int) -> com.lianyi.paimonsnotebook.common.web.hutao.genshin.avatar.AvatarData?
) {
    val list = entries ?: return

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp, 4.dp)
                    .radius(2.dp)
                    .background(White)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "角色持有率 · 本期(与上期环比)",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Text(
                    text = "持有率为全服记录中拥有该角色的比例,百分比数字为参与统计的分布",
                    fontSize = 12.sp,
                    color = Black_60
                )
            }
        }

        itemsIndexed(list) { index, entry ->
            val avatar = getAvatar(entry.AvatarId)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp, 2.dp)
                    .radius(2.dp)
                    .background(White)
                    .padding(10.dp, 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${index + 1}",
                        fontSize = 13.sp,
                        color = Black_60,
                        modifier = Modifier.width(26.dp)
                    )

                    NetworkImage(
                        url = avatar?.iconUrl ?: "",
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = avatar?.name ?: "${entry.AvatarId}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )

                    DeltaText(
                        text = String.format("%.1f%%", entry.HoldingRate * 100),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        delta = null
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    DeltaText(
                        text = "",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        delta = entry.HoldingDelta
                    )
                }

                Row {
                    Spacer(modifier = Modifier.width(26.dp))

                    entry.Constellations.sortedBy { it.Item }.forEachIndexed { cIndex, constellation ->
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "C${constellation.Item}",
                                fontSize = 10.sp,
                                color = Black_60
                            )

                            DeltaText(
                                text = String.format("%.1f", constellation.Rate * 100),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Normal,
                                delta = entry.ConstellationDeltas.getOrNull(cIndex)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun HutaoTeamPage(
    teams: List<HutaoTeamCombinationData>?,
    getAvatar: (Int) -> com.lianyi.paimonsnotebook.common.web.hutao.genshin.avatar.AvatarData?
) {
    teams ?: return

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        teams.sortedByDescending { it.Floor }.forEach { floorTeam ->
            item(key = "team_${floorTeam.Floor}") {
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
                        text = "${floorTeam.Floor} 层",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    floorTeam.Up.take(5).forEachIndexed { index, team ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${index + 1}",
                                fontSize = 13.sp,
                                color = Black_60,
                                modifier = Modifier.width(24.dp)
                            )

                            team.Item.split(",").take(4).forEach { avatarId ->
                                val avatar = getAvatar(avatarId.toIntOrNull() ?: 0)

                                NetworkImage(
                                    url = avatar?.iconUrl ?: "",
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )

                                Spacer(modifier = Modifier.width(6.dp))
                            }

                            Spacer(modifier = Modifier.weight(1f))

                            Text(
                                text = String.format("%.2f%%", team.Rate * 100),
                                fontSize = 13.sp,
                                color = Black_60
                            )
                        }
                    }
                }
            }
        }
    }
}

/*
* delta为null时只显示text;
* text为空时显示纯环比("环比 +x.x"或"环比 —");
* 否则显示 "text (+x.x)",括号内环比按涨跌着色
* */
@Composable
private fun DeltaText(
    text: String,
    fontSize: TextUnit,
    fontWeight: FontWeight,
    delta: Double?
) {
    val deltaColor = when {
        delta == null -> Black_60
        delta >= 0 -> Color(0xFF2E7D32)
        else -> Color(0xFFC62828)
    }

    if (text.isEmpty()) {
        Text(
            text = if (delta == null) "环比 —"
            else String.format(
                if (delta >= 0) "环比 +%.1f%%" else "环比 %.1f%%",
                delta * 100
            ),
            fontSize = fontSize,
            fontWeight = fontWeight,
            color = deltaColor
        )
        return
    }

    if (delta == null) {
        Text(text = text, fontSize = fontSize, fontWeight = fontWeight)
        return
    }

    Text(
        text = buildAnnotatedString {
            append(text)

            withStyle(
                SpanStyle(
                    color = deltaColor,
                    fontWeight = FontWeight.Normal
                )
            ) {
                append(String.format(if (delta >= 0) " (+%.1f%%)" else " (%.1f%%)", delta * 100))
            }
        },
        fontSize = fontSize,
        fontWeight = fontWeight
    )
}
