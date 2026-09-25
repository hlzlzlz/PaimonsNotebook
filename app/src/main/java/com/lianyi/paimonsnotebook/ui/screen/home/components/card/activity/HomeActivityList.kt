package com.lianyi.paimonsnotebook.ui.screen.home.components.card.activity

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lianyi.core.ui.components.text.InfoText
import com.lianyi.core.ui.components.text.PrimaryText
import com.lianyi.paimonsnotebook.common.components.media.NetworkImage
import com.lianyi.paimonsnotebook.common.database.disk_cache.entity.DiskCache
import com.lianyi.paimonsnotebook.common.extension.modifier.radius.radius
import com.lianyi.paimonsnotebook.common.util.time.TimeHelper
import com.lianyi.paimonsnotebook.common.web.hoyolab.bbs.NearActivityData
import com.lianyi.paimonsnotebook.common.web.hoyolab.takumi.game_record.act_calendar.ActCalendarData
import com.lianyi.paimonsnotebook.common.web.hoyolab.takumi.game_record.act_calendar.ActCalendarHelper
import com.lianyi.paimonsnotebook.ui.theme.Gray_97
import com.lianyi.paimonsnotebook.ui.theme.Gray_F5
import com.lianyi.paimonsnotebook.ui.theme.Primary_4
import com.lianyi.paimonsnotebook.ui.theme.White

/*
* 首页活动列表(合并「活动日历」与「近期活动」)
*
* 原先首页有两个并列区块:
*   - 活动日历:来自 act_calendar 接口的 act_list/fixed_act_list/selected_act_list
*     (只有名称 + 状态 + 结束时间,无图、不可点)
*   - 近期活动:来自 bbs 首页的 nearActivity(有图、可点进帖子)
* 两者都是"当前有什么活动",并列展示既重复又占地方,故合并为一个「活动」区块。
*
* 合并策略:**同一张卡片样式 + 优先展示近期活动(有图可点),再列活动日历**。
* 不做数据层合并 —— 两者的 id 体系完全不同(bbs 帖子 vs 游戏活动 id),
* 按名称模糊匹配去重会误删同名但不同的活动,属于编造关联。
*
* ⚠️ 保留两个数据源各自的入口语义:
*   - 近期活动 -> 点进 bbs 帖子
*   - 活动日历 -> 无跳转(接口未提供 url;`Act.strategy` 是攻略字段,不是活动链接)
* */
@Composable
internal fun HomeActivityList(
    nearActivities: List<NearActivityData.Hots.Group2.Children.NearActivity>,
    calendarActs: List<ActCalendarData.Act>,
    onClickNearActivity: (String) -> Unit
) {
    //近期活动在前(有图、可点、信息更全)
    nearActivities.forEach { item ->
        NearActivityRow(item = item, onClick = onClickNearActivity)
    }

    calendarActs.forEach { act ->
        CalendarActRow(act = act)
    }
}

/*
* 近期活动行(原有样式保留)
* */
@Composable
private fun NearActivityRow(
    item: NearActivityData.Hots.Group2.Children.NearActivity,
    onClick: (String) -> Unit
) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(White)
            .padding(8.dp, 3.dp)
            .radius(4.dp)
            .clickable { onClick(item.url) }
            .background(Gray_F5)
    ) {
        Row(
            modifier = Modifier
                .radius(4.dp)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NetworkImage(
                url = item.icon,
                modifier = Modifier
                    .radius(4.dp)
                    .size(40.dp),
                contentScale = ContentScale.Crop,
                diskCache = DiskCache(
                    url = item.icon,
                    name = "近期活动图片",
                    createFrom = "首页",
                    description = "${item.title},${item.abstract}",
                    type = com.lianyi.paimonsnotebook.common.database.disk_cache.util.DiskCacheDataType.Temp,
                    lastUseFrom = "首页"
                )
            )

            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                PrimaryText(
                    text = item.title,
                    textSize = 14.sp,
                    //原实现此处用的是普通 Text(非粗体),保持一致
                    bold = false,
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth()
                )

                InfoText(
                    text = item.abstract,
                    fontSize = 13.sp,
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (item.end_time != "0") {
            val timeText =
                "还剩${TimeHelper.timeStampParseToTextDayAndHour((item.end_time.toLongOrNull() ?: 0L) - System.currentTimeMillis())}"

            Text(
                text = timeText,
                fontSize = 10.sp,
                color = White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .background(Primary_4, RoundedCornerShape(0.dp, 4.dp, 0.dp, 4.dp))
                    .padding(4.dp, 1.dp)
            )
        }
    }
}

/*
* 活动日历行(无图,故用状态色块代替图片位,与近期活动行的视觉节奏保持一致)
*
* ⚠️ 外层 White + 内层 Gray_F5 的两层背景是照搬原 WebHomeNearActivity 的做法
*    (白底再叠浅灰卡片),本次保留以维持首页原有的观感,不与合并前产生视觉断层。
* */
@Composable
private fun CalendarActRow(act: ActCalendarData.Act) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(White)
            .padding(8.dp, 3.dp)
            .radius(4.dp)
            .background(Gray_F5)
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            /*
            * 无图活动用状态色块占位:让两类行的高度与左对齐一致,
            * 不会因为一行有图一行没图而参差。
            * */
            Box(
                modifier = Modifier
                    .radius(4.dp)
                    .size(40.dp)
                    .background(statusColor(act.status)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = ActCalendarHelper.statusText(act.status),
                    fontSize = 10.sp,
                    color = White,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                PrimaryText(
                    text = act.name,
                    textSize = 14.sp,
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth()
                )

                val endTime = act.end_time?.format() ?: ""
                InfoText(
                    text = if (endTime.isNotEmpty()) "$endTime 结束" else "时间待定",
                    fontSize = 13.sp,
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            val countdown = ActCalendarHelper.countdownText(act.countdown_seconds)
            if (countdown.isNotEmpty()) {
                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = countdown,
                    fontSize = 11.sp,
                    color = Color(0xFFE65100),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
        }
    }
}

//状态色:进行中用绿、即将开始用橙、已结束用灰(与原来的 StatusTag 一致)
private fun statusColor(status: Int): Color = when (status) {
    2 -> Color(0xFF2E7D32)
    1 -> Color(0xFFE65100)
    else -> Color(0xFF9E9E9E)
}
