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
import androidx.compose.foundation.lazy.items
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
import com.lianyi.core.ui.components.text.InfoText
import com.lianyi.core.ui.components.text.PrimaryText
import com.lianyi.paimonsnotebook.common.components.media.NetworkImage
import com.lianyi.paimonsnotebook.common.extension.modifier.radius.radius
import com.lianyi.paimonsnotebook.common.web.hutao.statistics.HutaoAvatarCollocationData
import com.lianyi.paimonsnotebook.common.web.hutao.statistics.HutaoAvatarFloorRateData
import com.lianyi.paimonsnotebook.common.web.hutao.statistics.HutaoHoldingRateEntry
import com.lianyi.paimonsnotebook.common.web.hutao.statistics.HutaoOverviewData
import com.lianyi.paimonsnotebook.common.web.hutao.statistics.HutaoTeamCombinationData
import com.lianyi.paimonsnotebook.common.web.hutao.statistics.HutaoWeaponCollocationData
import com.lianyi.paimonsnotebook.ui.theme.Black_60
import com.lianyi.paimonsnotebook.ui.theme.CardBackGroundColor

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
                    .padding(8.dp, 2.dp)
                    .radius(6.dp)
                    .background(CardBackGroundColor)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PrimaryText(
                    text = "第 ${overview.ScheduleId} 期深渊数据",
                    textSize = 16.sp
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
        InfoText(
            text = label,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f)
        )
        PrimaryText(text = value, textSize = 14.sp)
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
                        .padding(8.dp, 2.dp)
                        .radius(6.dp)
                        .background(CardBackGroundColor)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PrimaryText(
                        text = "${floorRate.Floor} 层",
                        textSize = 15.sp
                    )

                    floorRate.Ranks.sortedByDescending { it.Rate }.take(15)
                        .forEachIndexed { index, rank ->
                            val avatar = getAvatar(rank.Item)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                InfoText(
                                    text = "${index + 1}",
                                    fontSize = 13.sp,
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

                                PrimaryText(
                                    text = avatar?.name ?: "${rank.Item}",
                                    textSize = 14.sp,
                                    bold = false,
                                    modifier = Modifier.weight(1f)
                                )

                                InfoText(
                                    text = String.format("%.2f%%", rank.Rate * 100),
                                    fontSize = 13.sp
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
                    .padding(8.dp, 2.dp)
                    .radius(6.dp)
                    .background(CardBackGroundColor)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                PrimaryText(
                    text = "角色持有率 · 本期(与上期环比)",
                    textSize = 15.sp
                )

                InfoText(text = "持有率为全服记录中拥有该角色的比例,百分比数字为参与统计的分布")
            }
        }

        itemsIndexed(list) { index, entry ->
            val avatar = getAvatar(entry.AvatarId)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp, 2.dp)
                    .radius(6.dp)
                    .background(CardBackGroundColor)
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

                    PrimaryText(
                        text = avatar?.name ?: "${entry.AvatarId}",
                        textSize = 14.sp,
                        bold = false,
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
                            InfoText(text = "C${constellation.Item}", fontSize = 10.sp)

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
                        .padding(8.dp, 2.dp)
                        .radius(6.dp)
                        .background(CardBackGroundColor)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PrimaryText(
                        text = "${floorTeam.Floor} 层",
                        textSize = 15.sp
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

                            InfoText(
                                text = String.format("%.2f%%", team.Rate * 100),
                                fontSize = 13.sp
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

/*
* 角色配装页
*
* 每个角色一张卡:头部是同队角色(与谁一起打),其后是所持武器、所穿圣遗物。
* 按AvatarId排序,只展示占比最高的若干项。
* */
@Composable
internal fun HutaoAvatarCollocationPage(
    collocations: List<HutaoAvatarCollocationData>?,
    getAvatar: (Int) -> com.lianyi.paimonsnotebook.common.web.hutao.genshin.avatar.AvatarData?,
    getWeapon: (Int) -> com.lianyi.paimonsnotebook.common.web.hutao.genshin.weapon.WeaponData?
) {
    val list = collocations ?: return

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp, 2.dp)
                    .radius(6.dp)
                    .background(CardBackGroundColor)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                PrimaryText(text = "角色配装 · 全服统计", textSize = 15.sp)
                InfoText(text = "数据来自玩家上传的深渊记录:同队角色为与其同队出场的比例,武器/圣遗物为其所持比例")
            }
        }

        items(
            items = list.sortedByDescending { it.Avatars.firstOrNull()?.Rate ?: 0.0 },
            key = { entry -> "collocation_${entry.AvatarId}" }
        ) { entry ->
            val avatar = getAvatar(entry.AvatarId)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp, 2.dp)
                    .radius(6.dp)
                    .background(CardBackGroundColor)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                //角色名
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NetworkImage(
                        url = avatar?.iconUrl ?: "",
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    PrimaryText(
                        text = avatar?.name ?: "${entry.AvatarId}",
                        textSize = 15.sp,
                        modifier = Modifier.weight(1f)
                    )
                }

                //同队角色
                if (entry.Avatars.isNotEmpty()) {
                    CollocationSectionTitle("常见队友")

                    entry.Avatars.sortedByDescending { it.Rate }.take(6).forEach { rate ->
                        val teammate = getAvatar(rate.Item)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            NetworkImage(
                                url = teammate?.iconUrl ?: "",
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            PrimaryText(
                                text = teammate?.name ?: "${rate.Item}",
                                textSize = 13.sp,
                                bold = false,
                                modifier = Modifier.weight(1f)
                            )

                            InfoText(
                                text = String.format("%.1f%%", rate.Rate * 100),
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                //所持武器
                if (entry.Weapons.isNotEmpty()) {
                    CollocationSectionTitle("常用武器")

                    entry.Weapons.sortedByDescending { it.Rate }.take(6).forEach { rate ->
                        val weapon = getWeapon(rate.Item)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            NetworkImage(
                                url = weapon?.iconUrl ?: "",
                                modifier = Modifier.size(28.dp),
                                contentScale = ContentScale.Fit
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            PrimaryText(
                                text = weapon?.name ?: "${rate.Item}",
                                textSize = 13.sp,
                                bold = false,
                                modifier = Modifier.weight(1f)
                            )

                            InfoText(
                                text = String.format("%.1f%%", rate.Rate * 100),
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                //所穿圣遗物(Item形如"套装Id-件数")
                if (entry.Reliquaries.isNotEmpty()) {
                    CollocationSectionTitle("常用圣遗物")

                    entry.Reliquaries.sortedByDescending { it.Rate }.take(6).forEach { rate ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            PrimaryText(
                                text = formatReliquaryItem(rate.Item),
                                textSize = 13.sp,
                                bold = false,
                                modifier = Modifier.weight(1f)
                            )

                            InfoText(
                                text = String.format("%.1f%%", rate.Rate * 100),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

/*
* 武器配队页
*
* 每把武器一张卡:列出使用该武器的角色及占比(相当于"这把武器给谁用")。
* 元数据里没有的武器Id直接跳过,避免展示一堆纯数字。
* */
@Composable
internal fun HutaoWeaponCollocationPage(
    collocations: List<HutaoWeaponCollocationData>?,
    getAvatar: (Int) -> com.lianyi.paimonsnotebook.common.web.hutao.genshin.avatar.AvatarData?,
    getWeapon: (Int) -> com.lianyi.paimonsnotebook.common.web.hutao.genshin.weapon.WeaponData?
) {
    val list = collocations ?: return

    //只保留元数据里能找到的武器,并按最高使用率排序
    val displayList = list
        .filter { getWeapon(it.WeaponId) != null }
        .sortedByDescending { entry -> entry.Avatars.maxOfOrNull { it.Rate } ?: 0.0 }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp, 2.dp)
                    .radius(6.dp)
                    .background(CardBackGroundColor)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                PrimaryText(text = "武器配队 · 全服统计", textSize = 15.sp)
                InfoText(
                    text = "共 ${displayList.size} 把武器有配队数据;" +
                            "百分比为该角色使用此武器的比例"
                )
            }
        }

        items(
            items = displayList,
            key = { entry -> "weapon_${entry.WeaponId}" }
        ) { entry ->
            val weapon = getWeapon(entry.WeaponId)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp, 2.dp)
                    .radius(6.dp)
                    .background(CardBackGroundColor)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                //武器名
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NetworkImage(
                        url = weapon?.iconUrl ?: "",
                        modifier = Modifier.size(36.dp),
                        contentScale = ContentScale.Fit
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    PrimaryText(
                        text = weapon?.name ?: "${entry.WeaponId}",
                        textSize = 15.sp,
                        modifier = Modifier.weight(1f)
                    )
                }

                entry.Avatars.sortedByDescending { it.Rate }.take(8).forEach { rate ->
                    val avatar = getAvatar(rate.Item)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        NetworkImage(
                            url = avatar?.iconUrl ?: "",
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        PrimaryText(
                            text = avatar?.name ?: "${rate.Item}",
                            textSize = 13.sp,
                            bold = false,
                            modifier = Modifier.weight(1f)
                        )

                        InfoText(
                            text = String.format("%.1f%%", rate.Rate * 100),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CollocationSectionTitle(text: String) {
    InfoText(text = text, fontSize = 12.sp)
}

/*
* 圣遗物Item形如 "2150321-4",末位是件数;这里转成 "套装 2150321 · 4件套"
* 套装名需要ReliquarySet元数据,而本页只按Id展示 —— 不做名称映射以免给出错误名称。
* */
private fun formatReliquaryItem(item: String): String {
    val parts = item.split("-")

    return if (parts.size == 2) {
        "套装 ${parts[0]} · ${parts[1]} 件套"
    } else {
        item
    }
}
