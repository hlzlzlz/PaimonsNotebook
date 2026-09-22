package com.lianyi.paimonsnotebook.ui.screen.abyss.components.page

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lianyi.core.ui.components.text.InfoText
import com.lianyi.core.ui.components.text.PrimaryText
import com.lianyi.paimonsnotebook.common.components.lazy.ContentSpacerLazyColumn
import com.lianyi.paimonsnotebook.common.database.abyss.entity.AbyssSeasonSnapshot
import com.lianyi.paimonsnotebook.common.extension.modifier.radius.radius
import com.lianyi.paimonsnotebook.common.util.metadata.genshin.abyss.AbyssSnapshotMapper
import com.lianyi.paimonsnotebook.ui.theme.Black_60
import com.lianyi.paimonsnotebook.ui.theme.White

/*
* 深渊历史成绩页
*
* 数据来自本地 abyss_season_snapshots(每次打开本期/上期时自动存档)。
* 服务端只提供本期与上期,更早的期数取不回来 —— 故本页的数据完全依赖
* 用户"当期是否打开过深渊页",空数据时必须说明来源,避免被当成功能坏了。
* */
@Composable
fun AbyssHistoryPage(snapshots: List<AbyssSeasonSnapshot>) {
    if (snapshots.isEmpty()) {
        Column(modifier = Modifier.padding(16.dp)) {
            InfoText(
                text = "暂无历史成绩。本机从每次打开「本期」或「上期」时开始逐期记录;" +
                        "服务端只保留本期与上期,错过的期数无法补录。",
                fontSize = 13.sp
            )
        }
        return
    }

    ContentSpacerLazyColumn(modifier = Modifier.fillMaxWidth()) {
        //走势概览
        item {
            val trend = AbyssSnapshotMapper.starTrend(snapshots, limit = 6)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp, 4.dp)
                    .radius(6.dp)
                    .background(White)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PrimaryText(text = "最近星数走势", textSize = 16.sp)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    //倒序展示(左新右旧),与列表顺序一致
                    trend.forEachIndexed { index, star ->
                        if (index > 0) {
                            Spacer(modifier = Modifier.width(8.dp))
                        }

                        Text(
                            text = "$star",
                            fontSize = 15.sp,
                            color = if (AbyssSnapshotMapper.isFullStar(star)) {
                                Color(0xFF34C759)
                            } else {
                                Color(0xFF4C8BF5)
                            }
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    InfoText(text = "共 ${snapshots.size} 期", fontSize = 12.sp)
                }

                InfoText(
                    text = "满星为 ${AbyssSnapshotMapper.FULL_STAR} 星",
                    fontSize = 12.sp
                )
            }
        }

        //逐期明细
        items(
            items = snapshots,
            //schedule_id 是稳定业务主键
            key = { it.schedule_id }
        ) { snapshot ->
            SeasonCard(snapshot = snapshot)
        }

        item {
            InfoText(
                text = "仅记录本机打开过深渊页的期数;进行中的本期会在每次查看时更新。",
                fontSize = 12.sp,
                modifier = Modifier.padding(12.dp, 8.dp)
            )
        }
    }
}

@Composable
private fun SeasonCard(snapshot: AbyssSeasonSnapshot) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp, 4.dp)
            .radius(6.dp)
            .background(White)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PrimaryText(
                text = AbyssSnapshotMapper.seasonLabel(snapshot),
                textSize = 15.sp
            )

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "${snapshot.total_star} 星",
                fontSize = 14.sp,
                color = if (AbyssSnapshotMapper.isFullStar(snapshot.total_star)) {
                    Color(0xFF34C759)
                } else {
                    Black_60
                }
            )
        }

        InfoText(
            text = "最高 ${snapshot.max_floor} · 战斗 ${snapshot.total_battle_times} 次" +
                    " · 胜 ${snapshot.total_win_times} 次",
            fontSize = 12.sp
        )

        //逐层星数
        val floors = AbyssSnapshotMapper.deserializeFloors(snapshot.floor_stars)
        if (floors.isNotEmpty()) {
            /*
            * ⚠️ 必须可横向滚动
            *
            * 深渊有 12 层,每层形如 "12层 3/3",横向排布在 360dp 有效宽度下
            * (见 Theme 的 density = widthPixels / 360f)必然溢出、右侧楼层被裁掉。
            * 与 TabBar 溢出是同一类问题。
            * */
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically
            ) {
                floors.forEachIndexed { index, floor ->
                    if (index > 0) {
                        Spacer(modifier = Modifier.width(10.dp))
                    }

                    Text(
                        text = "${floor.index}层 ${floor.star}/${floor.maxStar}",
                        fontSize = 12.sp,
                        color = if (floor.star >= floor.maxStar) {
                            Color(0xFF34C759)
                        } else {
                            Black_60
                        }
                    )
                }
            }
        }
    }
}
