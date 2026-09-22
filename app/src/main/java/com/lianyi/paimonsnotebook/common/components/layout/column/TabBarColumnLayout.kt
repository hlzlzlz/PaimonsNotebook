package com.lianyi.paimonsnotebook.common.components.layout.column

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lianyi.paimonsnotebook.common.components.widget.TabBar
import com.lianyi.paimonsnotebook.ui.theme.BackGroundColor

/*
* 顶部tab垂直布局
*
* ⚠️ tabBarWeighted 的用途:
*    TabBar 内部是**横向可滚动**的(否则 tab 一多就溢出屏幕被裁掉)。
*    而 horizontalScroll 在内容超宽时会把自身宽度报到"可用上限",
*    于是同一行里后测量的固定宽度兄弟会被挤成 0 宽。
*    ⇒ 凡 topSlot 里有固定宽度内容(图标/按钮/角色名)且 tab 会超宽的页面,
*      必须传 tabBarWeighted = true,让 TabBar 只吃剩余空间。
*
*    反之,topSlot 里用 Modifier.fillMaxSize() 的页面(角色配队/资源管理/
*    养成计划)绝不能传 —— 那些 Row 会先占满整行,加权后 TabBar 分到 0 宽,
*    **tab 整体消失**。这些页面 tab 数量少(2~3 个)不超宽,保持默认即可。
* */
@Composable
internal fun TabBarColumnLayout(
    tabs: Array<String>,
    backgroundColor: Color = BackGroundColor,
    tabBarPaddingVertical: Dp = 4.dp,
    tabBarPaddingHorizontal: Dp = 12.dp,
    tabsSpace: Dp = 0.dp,
    tabBarWeighted: Boolean = false,
    onTabBarSelect: (Int) -> Unit,
    statusBarEnabled: Boolean = true,
    topSlot: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    TopSlotColumnLayout(
        topSlot = {
            TabBar(
                tabs = tabs,
                onSelect = onTabBarSelect,
                tabBarPadding = PaddingValues(tabBarPaddingHorizontal, tabBarPaddingVertical),
                tabsSpace = tabsSpace,
                //weight 是 RowScope 成员,只能在这里(ColumnScope 内、Row 的 topSlot 中)使用
                modifier = if (tabBarWeighted) Modifier.weight(1f) else Modifier
            )
            topSlot.invoke(this)
        },
        backgroundColor = backgroundColor,
        statusBarEnabled = statusBarEnabled,
        content = content
    )
}