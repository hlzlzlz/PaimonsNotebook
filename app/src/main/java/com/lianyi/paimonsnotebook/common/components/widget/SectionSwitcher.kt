package com.lianyi.paimonsnotebook.common.components.widget

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lianyi.paimonsnotebook.common.extension.modifier.radius.radius
import com.lianyi.paimonsnotebook.ui.theme.BackGroundColor
import com.lianyi.paimonsnotebook.ui.theme.Black
import com.lianyi.paimonsnotebook.ui.theme.Black_40
import com.lianyi.paimonsnotebook.ui.theme.CardBackGroundColor

/*
* 板块切换器(两级结构的第一级)
*
* 用于 1.8.24 引入的三个"合并页"(战斗记录 / 养成素材 / 资料库):
* 第一行切换板块,第二行是各板块自己的标签栏。
*
* ⚠️ 外观刻意与 TabBar 区分开:TabBar 是"纯文字 + 选中放大"，
*    这里是"分段控件(卡片底 + 圆角 + 选中块)"。两行都是可点文字时
*    很容易点错行，视觉上必须能区分。
*
* ⚠️ 用等宽 weight 而非横向滚动:三处的板块名都很短(2~4 字)，
*    360dp 下放得下(最多 4 个 × 4 字 ≈ 但实测"养成素材"这类 4 字
*    在 4 等分下约 80dp/项，够用)。**若将来板块名变长或数量变多，
*    这里必须改成 horizontalScroll**，否则文字会被裁掉。
* */
@Composable
fun SectionSwitcher(
    sections: Array<String>,
    currentIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .radius(8.dp)
            .background(CardBackGroundColor)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        sections.forEachIndexed { index, name ->
            val selected = index == currentIndex

            Text(
                text = name,
                fontSize = 14.sp,
                color = if (selected) Black else Black_40,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier
                    .weight(1f)
                    .radius(6.dp)
                    .background(if (selected) BackGroundColor else CardBackGroundColor)
                    .clickable { onSelect(index) }
                    .padding(vertical = 8.dp),
                textAlign = TextAlign.Center
            )
        }
    }
}
