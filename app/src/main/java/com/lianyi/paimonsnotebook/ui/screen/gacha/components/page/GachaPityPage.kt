package com.lianyi.paimonsnotebook.ui.screen.gacha.components.page

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.lianyi.paimonsnotebook.common.components.widget.RoundedTag
import com.lianyi.paimonsnotebook.common.extension.modifier.radius.radius
import com.lianyi.paimonsnotebook.ui.screen.gacha.service.GachaPityCalculator
import com.lianyi.paimonsnotebook.ui.theme.CardBackGroundColor

private val GuaranteeRed = Color(0xFFC62828)
private val PityTrack = Color(0xFFE8E8E8)
private val PityFill = Color(0xFF4F86C6)
private val PityFillGuarantee = Color(0xFFFF9800)

/*
* 祈愿保底统计页
* 每个有记录的卡池一张卡片,展示距上次的保底进度、大保底状态与本地软保底估算
* */
@Composable
internal fun GachaPityPage(pities: List<GachaPityCalculator.PoolPity>?) {
    val list = pities ?: return

    ContentSpacerLazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            InfoText(
                text = "出金概率与预计抽数为本地估算模型,非全服统计",
                modifier = Modifier.padding(12.dp, 8.dp, 12.dp, 0.dp)
            )
        }

        items(list) { pity ->
            PityCard(pity)
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun PityCard(pity: GachaPityCalculator.PoolPity) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp, 2.dp)
            .radius(6.dp)
            .background(CardBackGroundColor)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PrimaryText(
                text = pity.pool.label,
                textSize = 16.sp,
                modifier = Modifier.weight(1f)
            )

            if (pity.isGuaranteed) {
                RoundedTag(
                    text = "大保底",
                    backGroundColor = GuaranteeRed,
                    textColor = Color.White
                )
            }
        }

        PityProgressRow(
            label = "距上个五星",
            current = pity.pullsSinceOrange,
            max = pity.pool.orangeThreshold,
            guarantee = pity.isGuaranteed
        )

        PityProgressRow(
            label = "距上个四星",
            current = pity.pullsSincePurple,
            max = 10,
            guarantee = false
        )

        InfoText(text = "累计 ${pity.totalPulls} 抽 · 五星 ${pity.totalOrange} 个")

        if (pity.lastOrangeName.isNotEmpty()) {
            InfoText(text = "上期出金:${pity.lastOrangeName} (${pity.lastOrangeTime})")
        }

        if (pity.pool.hasGuarantee && pity.totalOrange > 0) {
            InfoText(
                text = String.format(
                    "下一抽出金约 %.1f%% · 预计还需 %.1f 抽",
                    pity.nextPullOrangeProbability * 100,
                    pity.expectedPullsToOrange
                )
            )
        }
    }
}

@Composable
private fun PityProgressRow(
    label: String,
    current: Int,
    max: Int,
    guarantee: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            InfoText(
                text = label,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f)
            )

            PrimaryText(text = "$current / $max", textSize = 13.sp)
        }

        val progress = (current.toFloat() / max).coerceIn(0f, 1f)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .radius(3.dp)
                .background(PityTrack)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .height(6.dp)
                    .radius(3.dp)
                    .background(if (guarantee) PityFillGuarantee else PityFill)
            )
        }
    }
}
