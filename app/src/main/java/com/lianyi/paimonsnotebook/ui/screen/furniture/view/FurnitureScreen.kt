package com.lianyi.paimonsnotebook.ui.screen.furniture.view

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import com.lianyi.core.ui.components.text.InfoText
import com.lianyi.core.ui.components.text.PrimaryText
import com.lianyi.paimonsnotebook.common.components.layout.column.TopSlotColumnLayout
import com.lianyi.paimonsnotebook.common.components.loading.ContentLoadingAnimationPlaceholder
import com.lianyi.paimonsnotebook.common.components.loading.ContentLoadingLayout
import com.lianyi.paimonsnotebook.common.components.media.NetworkImage
import com.lianyi.paimonsnotebook.common.components.placeholder.EmptyPlaceholder
import com.lianyi.paimonsnotebook.common.components.placeholder.ErrorPlaceholder
import com.lianyi.paimonsnotebook.common.components.widget.InputTextFiled
import com.lianyi.paimonsnotebook.common.core.base.BaseActivity
import com.lianyi.paimonsnotebook.common.extension.modifier.radius.radius
import com.lianyi.paimonsnotebook.common.web.hoyolab.takumi.event.calculate.FurnitureItem
import com.lianyi.paimonsnotebook.ui.screen.furniture.viewmodel.FurnitureScreenViewModel
import com.lianyi.paimonsnotebook.ui.theme.BackGroundColor
import com.lianyi.paimonsnotebook.ui.theme.CardBackGroundColor
import com.lianyi.paimonsnotebook.ui.theme.PaimonsNotebookTheme

/*
* 洞天摹本
*
* 接通 CalculateFurniture* 三个此前声明未用的端点:
* 输入分享码 -> 取该摹本所需家具清单 -> 展示每件家具的需要量与缺口。
*
* ⚠️ 分享码需登录态(cookie_token)与 uid/region 才会返回 num/lack_num,
*    故本页要求已选用户。
* */
class FurnitureScreen : BaseActivity() {

    private val viewModel by lazy {
        ViewModelProvider(this)[FurnitureScreenViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            PaimonsNotebookTheme(this) {
                TopSlotColumnLayout(
                    backgroundColor = BackGroundColor,
                    topSlot = {
                        ShareCodeInput(
                            value = viewModel.shareCodeInput,
                            onValueChange = viewModel::onShareCodeChange,
                            onQuery = viewModel::query
                        )
                    }
                ) {
                    ContentLoadingLayout(
                        loadingState = viewModel.loadingState,
                        loadingContent = { ContentLoadingAnimationPlaceholder() },
                        emptyContent = { EmptyPlaceholder("输入摹本分享码后点击查询") },
                        errorContent = { ErrorPlaceholder("获取摹本失败") },
                        successContent = {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (viewModel.items.isNotEmpty()) {
                                    item {
                                        SectionTitle("所需家具")
                                    }

                                    items(
                                        items = viewModel.items,
                                        //家具 id 是稳定业务主键
                                        key = { it.id }
                                    ) { furniture ->
                                        FurnitureRow(furniture)
                                    }
                                }

                                if (viewModel.notCalcItems.isNotEmpty()) {
                                    item {
                                        SectionTitle("不在计算范围内")
                                    }

                                    items(
                                        items = viewModel.notCalcItems,
                                        key = { "not_${it.id}" }
                                    ) { furniture ->
                                        FurnitureRow(furniture)
                                    }
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

/*
* 分享码输入区
* ⚠️ 本页无状态栏占位需求由 TopSlotColumnLayout 负责,
*    但输入区自身要铺卡片底色,否则会与背景糊在一起
* */
@Composable
private fun ShareCodeInput(
    value: String,
    onValueChange: (String) -> Unit,
    onQuery: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardBackGroundColor)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        PrimaryText(text = "洞天摹本", textSize = 16.sp)

        InfoText(
            text = "粘贴游戏内复制的摹本分享码(整段文本也可以),点击查询所需家具",
            fontSize = 12.sp
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            InputTextFiled(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                placeholder = { InfoText(text = "摹本分享码", fontSize = 14.sp) }
            )

            Spacer(modifier = Modifier.width(8.dp))

            Button(onClick = onQuery) {
                Text(text = "查询")
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    PrimaryText(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .background(CardBackGroundColor)
            .padding(8.dp)
    )
}

/*
* 单件家具:图标 + 名称 + 需要量/缺口
* */
@Composable
private fun FurnitureRow(item: FurnitureItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp, 2.dp)
            .radius(6.dp)
            .background(CardBackGroundColor)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NetworkImage(
            url = item.iconUrl,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
        )

        Spacer(modifier = Modifier.width(10.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            PrimaryText(text = item.name.ifBlank { "未知家具" }, textSize = 14.sp)

            if (item.num > 0) {
                InfoText(text = "需要 ${item.num}", fontSize = 12.sp)
            }
        }

        /*
        * 缺口:服务端 lack_num 是"还缺多少",为 0 表示已足够。
        * ⚠️ 只在有缺口时用醒目色,避免整页都是红字。
        * */
        if (item.lackNum > 0) {
            Text(
                text = "缺 ${item.lackNum}",
                fontSize = 13.sp,
                color = Color(0xFFE65100),
                fontWeight = FontWeight.SemiBold
            )
        } else if (item.num > 0) {
            Text(
                text = "已足够",
                fontSize = 12.sp,
                color = Color(0xFF2E7D32)
            )
        }
    }
}
