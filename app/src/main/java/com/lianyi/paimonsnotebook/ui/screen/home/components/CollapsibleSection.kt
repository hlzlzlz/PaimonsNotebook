package com.lianyi.paimonsnotebook.ui.screen.home.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lianyi.core.ui.components.text.InfoText
import com.lianyi.core.ui.components.text.PrimaryText
import com.lianyi.paimonsnotebook.R
import com.lianyi.paimonsnotebook.ui.theme.White

/*
* 首页可折叠区块
*
* 用于"当期卡池"与"活动"两个区块 —— 它们内容较长(卡池 2 条 + 活动多条),
* 首页整体是 LazyColumn,不做折叠时下面的"公告"要滚很久才能看到。
*
* ⚠️ subtitle 用来在**收起状态**下仍能看出这一块有没有内容/大概几条,
*    否则收起后只剩一个标题,用户不知道值不值得展开。
*
* ⚠️ 折叠状态用 rememberSaveable:首页退出再进入(以及旋转屏幕)时保留用户的选择。
*    用 remember 会在每次重建时重置回默认值。
* */
@Composable
internal fun CollapsibleSection(
    title: String,
    subtitle: String = "",
    defaultExpanded: Boolean = true,
    content: @Composable () -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(defaultExpanded) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(White)
                .clickable { expanded = !expanded }
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PrimaryText(text = title)

            if (subtitle.isNotEmpty()) {
                Spacer(modifier = Modifier.width(8.dp))

                InfoText(
                    text = subtitle,
                    fontSize = 12.sp,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }

            /*
            * 用既有的 ic_chevron_down 旋转来表示展开/收起:
            * 展开朝上、收起朝下 —— 只用一个资源，避免新增图标。
            * */
            Icon(
                painter = painterResource(id = R.drawable.ic_chevron_down),
                contentDescription = if (expanded) "收起" else "展开",
                modifier = Modifier
                    .size(20.dp)
                    .rotate(if (expanded) 180f else 0f)
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                content.invoke()
            }
        }
    }
}
