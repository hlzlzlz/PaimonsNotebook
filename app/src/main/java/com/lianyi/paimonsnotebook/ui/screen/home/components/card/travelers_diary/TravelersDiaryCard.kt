package com.lianyi.paimonsnotebook.ui.screen.home.components.card.travelers_diary

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
import com.lianyi.paimonsnotebook.common.web.hoyolab.takumi.game_record.ledger.LedgerData
import com.lianyi.paimonsnotebook.ui.theme.BackGroundColor
import com.lianyi.paimonsnotebook.ui.theme.Black_60
import com.lianyi.paimonsnotebook.ui.theme.Primary_2
import com.lianyi.paimonsnotebook.ui.theme.White

/*
* 首页旅行者札记摘要卡片
* */
@Composable
fun TravelersDiaryCard(
    ledgerData: LedgerData?,
    onClick: () -> Unit
) {
    if (ledgerData == null) {
        return
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp, 0.dp)
            .radius(2.dp)
            .background(White)
            .clickable {
                onClick.invoke()
            }
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PrimaryText(
                text = "旅行者札记 · ${ledgerData.nickname}",
                textSize = 15.sp
            )

            Spacer(modifier = Modifier.weight(1f))

            InfoText(text = "详情 >", fontSize = 13.sp)
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            InfoText(
                text = "原石",
                fontSize = 13.sp,
                modifier = Modifier.padding(end = 6.dp)
            )

            PrimaryText(
                text = "${ledgerData.month_data.current_primogems}",
                textSize = 16.sp,
                color = Primary_2
            )

            Spacer(modifier = Modifier.weight(1f))

            InfoText(
                text = "摩拉",
                fontSize = 13.sp,
                modifier = Modifier.padding(end = 6.dp)
            )

            PrimaryText(
                text = "${ledgerData.month_data.current_mora}",
                textSize = 16.sp,
                color = Primary_2
            )
        }

        InfoText(
            text = "今日获取原石 ${ledgerData.day_data.current_primogems}",
            fontSize = 13.sp
        )
    }
}
