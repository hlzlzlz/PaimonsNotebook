package com.lianyi.paimonsnotebook.ui.screen.home.components.menu

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lianyi.paimonsnotebook.common.components.widget.RoundedTag
import com.lianyi.paimonsnotebook.common.extension.modifier.radius.radius
import com.lianyi.paimonsnotebook.ui.screen.home.data.ModalItemData
import com.lianyi.paimonsnotebook.ui.theme.Black
import com.lianyi.paimonsnotebook.ui.theme.Black_90

/*
* 侧边栏功能项
*
* enabled=false 表示该功能当前不可用(需要元数据但未启用)。
*
* ⚠️ 不可用项**仍然要渲染并且保持可点击**:
*   - 不渲染 ⇒ 用户以为应用缺功能(这正是此前的缺陷,18 项只显示 7 项);
*   - 不可点 ⇒ 用户点了没反应,依然不知道为什么。
*   故置灰 + 右侧"需元数据"标签,点击后由上层提示如何启用。
* */
@Composable
internal fun SideBarMenuListItem(
    item: ModalItemData,
    enabled: Boolean = true,
    block: (ModalItemData) -> Unit,
) {
    Row(
        modifier = Modifier
            .radius(8.dp)
            .fillMaxWidth()
            .height(42.dp)
            .clickable {
                block.invoke(item)
            }
            .padding(8.dp, 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(id = item.icon),
            contentDescription = item.name,
            modifier = Modifier
                .size(24.dp)
                .alpha(if (enabled) 1f else 0.4f),
            colorFilter = ColorFilter.tint(Black_90)
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = item.name,
            fontSize = 14.sp,
            color = if (enabled) Black else Black_90,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .alpha(if (enabled) 1f else 0.5f)
        )

        if (!enabled) {
            RoundedTag(text = "需元数据", fontSize = 10.sp)
        }
    }
}
