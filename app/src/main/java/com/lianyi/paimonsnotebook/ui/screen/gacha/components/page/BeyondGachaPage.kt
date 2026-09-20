package com.lianyi.paimonsnotebook.ui.screen.gacha.components.page

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lianyi.paimonsnotebook.common.components.lazy.ContentSpacerLazyColumn
import com.lianyi.paimonsnotebook.common.components.widget.RoundedTag
import com.lianyi.paimonsnotebook.common.extension.modifier.radius.radius
import com.lianyi.core.ui.components.text.InfoText
import com.lianyi.core.ui.components.text.PrimaryText
import com.lianyi.paimonsnotebook.ui.screen.gacha.data.BeyondGachaGroup
import com.lianyi.paimonsnotebook.ui.theme.Black_60
import com.lianyi.paimonsnotebook.ui.theme.CardBackGroundColor
import com.lianyi.paimonsnotebook.ui.theme.CardBackGroundColor_Gray_Dark
import com.lianyi.paimonsnotebook.ui.theme.GachaStar4Color2
import com.lianyi.paimonsnotebook.ui.theme.GachaStar5Color
import com.lianyi.paimonsnotebook.ui.theme.White

/*
* 千星奇域(UGC)祈愿记录页
*
* 与普通祈愿的「总览」页差异:
*   - 不画环形图/进度条:千星奇域的卡池规则与保底机制未公开,
*     照普通卡池的 90/10 权重画进度条会是**编造数据**;
*   - 不显示图标:本地元数据里没有千星奇域物品,拿不到 iconUrl,
*     所以用文字列表(详见 BeyondGachaGroup 的说明);
*   - 只统计条数与五星数,这两个是记录里直接可得的客观值。
* */
@Composable
fun BeyondGachaPage(groups: List<BeyondGachaGroup>) {
    ContentSpacerLazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        statusBarPaddingEnabled = false
    ) {
        items(groups, key = { it.gachaType }) { group ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .radius(6.dp)
                    .background(CardBackGroundColor)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PrimaryText(
                        text = group.gachaTypeName,
                        textSize = 16.sp,
                        modifier = Modifier.weight(1f)
                    )

                    RoundedTag(
                        text = "共 ${group.totalCount} 抽",
                        backGroundColor = CardBackGroundColor_Gray_Dark,
                        textColor = Black_60
                    )
                }

                if (group.star5Count > 0) {
                    InfoText(text = "五星 ${group.star5Count} 个", fontSize = 13.sp)
                }

                group.items.forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .radius(4.dp)
                            .background(White)
                            .padding(10.dp, 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RoundedTag(
                            text = "${item.rank_type}星",
                            backGroundColor = when (item.rank_type) {
                                "5" -> GachaStar5Color
                                "4" -> GachaStar4Color2
                                else -> CardBackGroundColor_Gray_Dark
                            },
                            textColor = if (item.rank_type == "5" || item.rank_type == "4")
                                White else Black_60
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        PrimaryText(
                            text = item.item_name.ifBlank { item.item_id },
                            textSize = 14.sp,
                            bold = false,
                            modifier = Modifier.weight(1f)
                        )

                        InfoText(text = item.time, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
