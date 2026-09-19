package com.lianyi.paimonsnotebook.ui.screen.achievement.view.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lianyi.paimonsnotebook.R
import com.lianyi.paimonsnotebook.common.components.widget.IconTextHintSlotItem
import com.lianyi.paimonsnotebook.ui.screen.achievement.util.enums.UIAFImportStrategy
import com.lianyi.paimonsnotebook.ui.theme.Primary_2

/*
* UIAF 导入策略选择器
*
* 插在导入确认弹窗的 slot 里。用项目既有的 IconTextHintSlotItem
* (与设置页同一个组件),选中项在右侧显示对勾,保持视觉一致。
*
* 为什么需要它:PN 原先导入是"直接覆盖",用户导入别人的存档会
* 静默丢掉自己的进度且无法挽回。给出三种策略让用户自己决定。
* */
@Composable
fun UIAFImportStrategySelector(
    selected: UIAFImportStrategy,
    onSelect: (UIAFImportStrategy) -> Unit
) {
    Column {
        Text(
            text = "导入方式",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = Primary_2
        )

        Spacer(modifier = Modifier.height(6.dp))

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            UIAFImportStrategy.entries.forEach { strategy ->
                IconTextHintSlotItem(
                    title = strategy.label,
                    description = strategy.description,
                    onClick = { onSelect(strategy) },
                    slot = {
                        if (strategy == selected) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_checkmark_circle_full),
                                contentDescription = "已选中",
                                tint = Primary_2
                            )
                        }
                    }
                )
            }
        }
    }
}
