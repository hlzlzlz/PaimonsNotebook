package com.lianyi.paimonsnotebook.ui.screen.home.components.card.card_pool

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import com.lianyi.core.ui.components.text.PrimaryText
import com.lianyi.paimonsnotebook.common.components.media.NetworkImage
import com.lianyi.paimonsnotebook.common.extension.modifier.radius.radius
import com.lianyi.paimonsnotebook.common.web.hoyolab.takumi.game_record.act_calendar.ActCalendarData
import com.lianyi.paimonsnotebook.ui.theme.Black_60
import com.lianyi.paimonsnotebook.ui.theme.White

/*
* 首页当期卡池卡片
* 数据来自act_calendar接口,由HomeScreenViewModel加载
* */
@Composable
fun CardPoolCard(pools: List<ActCalendarData.CardPool>) {
    if (pools.isEmpty()) {
        return
    }

    PrimaryText(
        text = "当期卡池",
        modifier = Modifier
            .fillMaxWidth()
            .background(White)
            .padding(8.dp)
    )

    pools.forEach { pool ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp, 0.dp)
                .radius(2.dp)
                .background(White)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = pool.pool_name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )

                Text(
                    text = formatCountdown(pool.countdown_seconds),
                    fontSize = 12.sp,
                    color = Color(0xFFE65100)
                )
            }

            Text(
                text = "${pool.version_name}版本 · ${pool.end_time?.format() ?: ""} 结束",
                fontSize = 12.sp,
                color = Black_60
            )

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                pool.avatars.take(6).forEach { PoolIcon(it.icon) }
                pool.weapon.take(3).forEach { PoolIcon(it.icon) }
            }
        }
    }
}

@Composable
private fun PoolIcon(url: String) {
    NetworkImage(
        url = url,
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
    )
}

//倒计时文案,信任服务器countdown_seconds
private fun formatCountdown(seconds: Long): String {
    val days = seconds / 86400
    val hours = seconds % 86400 / 3600

    return when {
        days >= 1 -> "剩 ${days}天${hours}时"
        hours >= 1 -> "剩 ${hours}小时"
        else -> "即将结束"
    }
}
