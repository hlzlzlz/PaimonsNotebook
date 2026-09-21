package com.lianyi.paimonsnotebook.ui.screen.home.components.card.activity

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
import com.lianyi.paimonsnotebook.common.extension.modifier.radius.radius
import com.lianyi.paimonsnotebook.common.web.hoyolab.takumi.game_record.act_calendar.ActCalendarData
import com.lianyi.paimonsnotebook.common.web.hoyolab.takumi.game_record.act_calendar.ActCalendarHelper
import com.lianyi.paimonsnotebook.ui.theme.White

/*
* 首页活动日历卡片
*
* 数据来自 act_calendar 的 act_list/fixed_act_list/selected_act_list ——
* 这三个字段此前只被解析就丢弃(首页只用了卡池),属"请求已发出、数据已在手"的补齐。
*
* 不显示 reward_list:实测该字段类型不稳定(声明为 List<Any>),
* 且活动奖励文案较长,首页空间有限,详情以游戏内为准。
* */
@Composable
fun ActivityCalendarCard(acts: List<ActCalendarData.Act>) {
    if (acts.isEmpty()) {
        return
    }

    PrimaryText(
        text = "活动日历",
        modifier = Modifier
            .fillMaxWidth()
            .background(White)
            .padding(8.dp)
    )

    acts.forEach { act ->
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
                PrimaryText(
                    text = act.name,
                    textSize = 15.sp
                )

                Spacer(modifier = Modifier.width(6.dp))

                StatusTag(status = act.status)

                val countdown = ActCalendarHelper.countdownText(act.countdown_seconds)
                if (countdown.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = countdown,
                        fontSize = 12.sp,
                        color = Color(0xFFE65100),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            val endTime = act.end_time?.format() ?: ""
            if (endTime.isNotEmpty()) {
                InfoText(
                    text = "$endTime 结束",
                    fontSize = 12.sp
                )
            }
        }
    }
}

/*
* 状态徽章
* 进行中用主色、即将开始用橙色,已结束用灰色 —— 避免只靠文字区分
* */
@Composable
private fun StatusTag(status: Int) {
    val color = when (status) {
        2 -> Color(0xFF2E7D32)
        1 -> Color(0xFFE65100)
        else -> Color(0xFF9E9E9E)
    }

    Text(
        text = ActCalendarHelper.statusText(status),
        fontSize = 11.sp,
        color = color,
        fontWeight = FontWeight.SemiBold
    )
}
