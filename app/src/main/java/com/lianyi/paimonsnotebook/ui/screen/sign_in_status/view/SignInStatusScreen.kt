package com.lianyi.paimonsnotebook.ui.screen.sign_in_status.view

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import com.lianyi.core.ui.components.text.PrimaryText
import com.lianyi.paimonsnotebook.common.components.lazy.ContentSpacerLazyColumn
import com.lianyi.paimonsnotebook.common.components.loading.ContentLoadingLayout
import com.lianyi.paimonsnotebook.common.components.media.NetworkImage
import com.lianyi.paimonsnotebook.common.core.base.BaseActivity
import com.lianyi.paimonsnotebook.common.extension.modifier.radius.radius
import com.lianyi.paimonsnotebook.ui.screen.sign_in_status.viewmodel.SignInStatusScreenViewModel
import com.lianyi.paimonsnotebook.ui.theme.CardBackGroundColor
import com.lianyi.paimonsnotebook.ui.theme.GachaStar5Color
import com.lianyi.paimonsnotebook.ui.theme.PaimonsNotebookTheme
import com.lianyi.paimonsnotebook.ui.theme.Success
import com.lianyi.paimonsnotebook.ui.theme.Warning

/*
* 米游社签到状态页
* 展示当前角色当月签到进度、漏签、补签卡与每日奖励
* */
class SignInStatusScreen : BaseActivity() {

    private val viewModel by lazy {
        ViewModelProvider(this)[SignInStatusScreenViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PaimonsNotebookTheme(this) {
                ContentLoadingLayout(loadingState = viewModel.loadingState) {
                    ContentSpacerLazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(12.dp, 6.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            StatusCard()
                        }

                        item {
                            PrimaryText(text = "当月签到奖励")
                        }

                        item {
                            AwardGrid()
                        }
                        //底部留白
                    }
                }
            }
        }
    }

    @Composable
    private fun StatusCard() {
        val info = viewModel.signInInfo ?: return
        val resign = viewModel.resignInfo

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .radius(6.dp)
                .background(CardBackGroundColor)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PrimaryText(
                    text = "UID ${viewModel.gameUid}",
                    textSize = 15.sp
                )

                Spacer(modifier = Modifier.weight(1f))

                Text(
                    text = if (info.is_sign) "今日已签" else "今日未签",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (info.is_sign) Success else Warning
                )
            }

            InfoLine("累计签到", "${info.total_sign_day} 天")
            InfoLine("当月漏签", "${info.sign_cnt_missed} 天")

            if (resign != null) {
                InfoLine(
                    "补签卡",
                    "${resign.coin_cnt} 张 · 本月已补 ${resign.resign_cnt_monthly}/${resign.resign_limit_monthly}"
                )
            }
        }
    }

    @Composable
    private fun InfoLine(label: String, value: String) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = label,
                fontSize = 13.sp,
                color = Color.Gray,
                modifier = Modifier.weight(1f)
            )

            Text(text = value, fontSize = 13.sp)
        }
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    private fun AwardGrid() {
        val info = viewModel.signInInfo ?: return

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            viewModel.awards.forEachIndexed { index, award ->
                val day = index + 1
                val signed = day < info.total_sign_day
                val isToday = day == info.total_sign_day

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(72.dp)
                        .radius(6.dp)
                        .background(CardBackGroundColor)
                        .then(
                            if (isToday) {
                                Modifier.border(
                                    2.dp,
                                    GachaStar5Color,
                                    RoundedCornerShape(6.dp)
                                )
                            } else {
                                Modifier
                            }
                        )
                        .padding(6.dp, 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    NetworkImage(
                        url = award.icon,
                        modifier = Modifier
                            .size(36.dp)
                            .alpha(if (signed || isToday) 1f else 0.45f)
                    )

                    Text(
                        text = "第${day}天",
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = award.name,
                        fontSize = 10.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
