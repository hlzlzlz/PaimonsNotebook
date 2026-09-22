package com.lianyi.paimonsnotebook.common.components.widget

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lianyi.paimonsnotebook.common.extension.value.toDp
import com.lianyi.paimonsnotebook.common.util.compose.animate.animateTextStyleAsState
import com.lianyi.paimonsnotebook.ui.theme.Black
import com.lianyi.paimonsnotebook.ui.theme.Black_20

@Composable
fun TabBar(
    tabs: Array<String>,
    tabBarPadding: PaddingValues,
    index: Int = 0,
    modifier: Modifier = Modifier,
    requiredWidthIn: Pair<Dp, Dp> = 50.dp to 240.dp,
    textSelectColor: Color = Black,
    textUnSelectColor: Color = Black_20,
    textSelectSize: TextUnit = 20.sp,
    textUnSelectSize: TextUnit = 14.sp,
    tabsSpace: Dp = 0.dp,
    onSelect: (Int) -> Unit
) {

    var currentIndex by remember(index) {
        mutableIntStateOf(index)
    }

    /*
    * ⚠️ 必须横向可滚动
    *
    * 每个 tab 最少 requiredWidthIn.first(默认 50dp)且选中时会放大字号,
    * 数量一多必然超出屏幕宽度。原实现是不可滚动的 Row,
    * 超出的 tab 会被**直接裁掉、既看不到也点不到**
    * (祈愿页加到第 7 个、深渊页 10 个时真机复现)。
    *
    * 注意不要改成 LazyRow:TabBarColumnLayout 外层用了
    * Modifier.height(IntrinsicSize.Min),而 Lazy 布局不支持固有尺寸测量,
    * 会直接抛异常/崩溃。
    * */
    val scrollState = rememberScrollState()

    /*
    * 记录每个 tab 在滚动内容中的位置与宽度,用于把选中项滚入可视区。
    * 用普通 map(非 State)避免每次布局都触发重组。
    * */
    val tabBounds = remember { mutableMapOf<Int, Pair<Int, Int>>() }

    //选中项若在可视区外,自动滚入(键盘/程序化切换、以及从右端返回时都需要)
    LaunchedEffect(currentIndex) {
        val bounds = tabBounds[currentIndex] ?: return@LaunchedEffect

        //viewportSize 在首帧布局完成前为 0,此时无法判断可视范围,直接跳过
        val viewportSize = scrollState.viewportSize
        if (viewportSize <= 0) return@LaunchedEffect

        val viewportStart = scrollState.value
        val viewportEnd = viewportStart + viewportSize

        val target = when {
            bounds.first < viewportStart -> bounds.first
            bounds.first + bounds.second > viewportEnd ->
                bounds.first + bounds.second - viewportSize
            else -> null
        }

        if (target != null && scrollState.maxValue > 0) {
            scrollState.animateScrollTo(target.coerceIn(0, scrollState.maxValue))
        }
    }

    Row(
        modifier = modifier
            .padding(tabBarPadding)
            .horizontalScroll(scrollState)
            .height(textSelectSize.toDp() + tabBarPadding.let { it.calculateTopPadding() + it.calculateBottomPadding() })
    ) {
        //以选中的字体样式撑起高度
        Row(
            horizontalArrangement = Arrangement.spacedBy(tabsSpace),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEachIndexed { index, s ->
                val textStyle by animateTextStyleAsState(
                    targetValue = if (currentIndex == index) TextStyle(
                        color = textSelectColor,
                        fontSize = textSelectSize,
                        fontWeight = FontWeight.SemiBold
                    )
                    else TextStyle(
                        color = textUnSelectColor,
                        fontSize = textUnSelectSize,
                        fontWeight = FontWeight.SemiBold
                    )
                )

                Column(
                    modifier = Modifier
                        .requiredWidthIn(requiredWidthIn.first, requiredWidthIn.second)
                        .onGloballyPositioned { coordinates ->
                            tabBounds[index] =
                                coordinates.positionInParent().x.toInt() to coordinates.size.width
                        }
                        .pointerInput(Unit) {
                            detectTapGestures {
                                currentIndex = index
                                onSelect.invoke(currentIndex)
                            }
                        },
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = s, style = textStyle)
                }
            }
        }
    }
}