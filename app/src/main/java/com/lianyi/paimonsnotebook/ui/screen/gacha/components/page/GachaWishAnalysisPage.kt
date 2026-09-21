package com.lianyi.paimonsnotebook.ui.screen.gacha.components.page

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lianyi.core.ui.components.text.InfoText
import com.lianyi.core.ui.components.text.PrimaryText
import com.lianyi.paimonsnotebook.common.components.lazy.ContentSpacerLazyColumn
import com.lianyi.paimonsnotebook.common.extension.modifier.radius.radius
import com.lianyi.paimonsnotebook.ui.screen.gacha.service.GachaWishHistory
import com.lianyi.paimonsnotebook.ui.theme.Black_60
import com.lianyi.paimonsnotebook.ui.theme.White

/*
* 祈愿分析页:各池的出金明细与欧非走势
*
* 数据全部来自本地 gacha_items 表(不需要新接口),
* 与"保底"页共用同一套统计口径 —— 两者出金总数必须一致。
* */
@Composable
fun GachaWishAnalysisPage(pools: List<GachaWishHistory.PoolHistory>?) {
    if (pools.isNullOrEmpty()) {
        Column(modifier = Modifier.padding(16.dp)) {
            InfoText(text = "暂无可分析的祈愿记录", fontSize = 14.sp)
        }
        return
    }

    ContentSpacerLazyColumn(modifier = Modifier.fillMaxWidth()) {
        pools.forEach { pool ->
            item {
                PoolAnalysisCard(pool = pool)
            }
        }

        item {
            InfoText(
                text = "统计基于本地已有记录;若首个五星之前缺少记录,该次消耗抽数会偏小," +
                        "相关卡片会标注「记录不完整」。",
                fontSize = 12.sp,
                modifier = Modifier.padding(12.dp, 8.dp)
            )
        }
    }
}

@Composable
private fun PoolAnalysisCard(pool: GachaWishHistory.PoolHistory) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp, 4.dp)
            .radius(6.dp)
            .background(White)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        //标题行:池名 + 出金数
        Row(verticalAlignment = Alignment.CenterVertically) {
            PrimaryText(text = pool.pool.label, textSize = 16.sp)

            Spacer(modifier = Modifier.weight(1f))

            InfoText(
                text = "共 ${pool.entries.size} 金",
                fontSize = 13.sp
            )
        }

        //汇总:平均 / 最欧 / 最非
        Row(verticalAlignment = Alignment.CenterVertically) {
            InfoText(
                text = "平均 ${"%.1f".format(pool.averagePulls)} 抽",
                fontSize = 13.sp
            )

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = "最欧 ${pool.bestPulls} 抽",
                fontSize = 13.sp,
                color = Color(0xFF34C759)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = "最非 ${pool.worstPulls} 抽",
                fontSize = 13.sp,
                color = Color(0xFFFF2D55)
            )
        }

        if (!pool.isComplete) {
            Text(
                text = "⚠️ 记录不完整:首个五星之前缺少记录,首条消耗抽数偏小",
                fontSize = 12.sp,
                color = Color(0xFFE65100)
            )
        }

        //逐次出金明细(倒序展示,最近的在前)
        pool.entries.reversed().forEach { entry ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PrimaryText(
                    text = "#${entry.index}",
                    textSize = 13.sp,
                    color = Black_60
                )

                Spacer(modifier = Modifier.width(8.dp))

                InfoText(
                    text = entry.name,
                    fontSize = 13.sp
                )

                Spacer(modifier = Modifier.weight(1f))

                //欧非分档
                Text(
                    text = GachaWishHistory.luckLabel(
                        pullsUsed = entry.pullsUsed,
                        threshold = pool.pool.orangeThreshold
                    ),
                    fontSize = 12.sp,
                    color = luckColor(entry.pullsUsed, pool.pool.orangeThreshold),
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.width(8.dp))

                InfoText(
                    text = "${entry.pullsUsed} 抽",
                    fontSize = 13.sp
                )

                /*
                * 大保底标记
                * wasGuaranteed 由"上一个五星是否歪"推导,无事件数据时为 false
                * */
                if (entry.wasGuaranteed) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "大保底",
                        fontSize = 11.sp,
                        color = Color(0xFFE65100)
                    )
                }
            }
        }
    }
}

//与 GachaWishHistory.luckLabel 的分档保持一致的颜色
private fun luckColor(pullsUsed: Int, threshold: Int): Color {
    if (threshold <= 0) return Black_60

    val ratio = pullsUsed.toDouble() / threshold

    return when {
        ratio <= 0.3 -> Color(0xFF34C759)
        ratio <= 0.5 -> Color(0xFF34C759)
        ratio <= 0.75 -> Color(0xFF4C8BF5)
        ratio < 1.0 -> Color(0xFFE65100)
        else -> Color(0xFFFF2D55)
    }
}
