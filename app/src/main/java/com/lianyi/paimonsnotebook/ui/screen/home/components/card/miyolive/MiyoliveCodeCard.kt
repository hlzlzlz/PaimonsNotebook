package com.lianyi.paimonsnotebook.ui.screen.home.components.card.miyolive

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lianyi.core.ui.components.text.InfoText
import com.lianyi.core.ui.components.text.PrimaryText
import com.lianyi.paimonsnotebook.common.extension.modifier.radius.radius
import com.lianyi.paimonsnotebook.common.util.system_service.SystemService
import com.lianyi.paimonsnotebook.common.web.hoyolab.takumi.event.miyolive.MiyoliveCodeData
import com.lianyi.paimonsnotebook.common.extension.string.notify
import com.lianyi.paimonsnotebook.ui.theme.Black_60
import com.lianyi.paimonsnotebook.ui.theme.Primary
import com.lianyi.paimonsnotebook.ui.theme.White

/*
* 首页前瞻直播兑换码卡片
* 数据来自miyolive接口,由HomeScreenViewModel在非直播时段留空
* */
@Composable
fun MiyoliveCodeCard(codes: List<MiyoliveCodeData.CodeWrapper>) {
    if (codes.isEmpty()) {
        return
    }

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
                text = "前瞻直播兑换码",
                textSize = 15.sp
            )

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "全部复制",
                fontSize = 13.sp,
                color = Primary,
                modifier = Modifier
                    .clickable {
                        SystemService.setClipBoardText(
                            codes.mapNotNull { it.code }.joinToString("\n")
                        )
                        "已复制全部兑换码".notify()
                    }
                    .padding(4.dp, 2.dp)
            )
        }

        codes.forEach { wrapper ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = wrapper.title ?: "",
                    fontSize = 12.sp,
                    color = Black_60,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 6.dp),
                    maxLines = 1
                )

                PrimaryText(
                    text = wrapper.code ?: "",
                    textSize = 14.sp,
                    modifier = Modifier.weight(1.4f),
                    maxLines = 1
                )

                Text(
                    text = "复制",
                    fontSize = 13.sp,
                    color = Primary,
                    modifier = Modifier
                        .clickable {
                            SystemService.setClipBoardText(wrapper.code ?: return@clickable)
                            "已复制 ${wrapper.code}".notify()
                        }
                        .padding(6.dp, 2.dp)
                )
            }
        }

        InfoText(text = "兑换码有时效性,请尽快在游戏内兑换")
    }
}
