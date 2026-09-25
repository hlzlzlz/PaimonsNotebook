package com.lianyi.paimonsnotebook.common.components.loading

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.lianyi.paimonsnotebook.common.components.placeholder.EmptyPlaceholder
import com.lianyi.paimonsnotebook.common.components.placeholder.ErrorPlaceholder
import com.lianyi.paimonsnotebook.common.util.enums.LoadingState
import com.lianyi.paimonsnotebook.ui.theme.BackGroundColor


/*
* 内容加载布局,根据不同的加载状态显示不同的内容
*
* ⚠️ 三个"非成功态"的默认值**必须渲染可见占位,不能是空 lambda**。
*
*   历史缺陷:此前 loadingContent / emptyContent / errorContent 的默认值
*   全都是 `{}`,于是凡是没有显式传参的调用点,在这三种状态下都会渲染出
*   **一片纯背景色**(看起来像页面坏了)。实测 31 个调用点中有 20 个既没传
*   errorContent 也没传 emptyContent,涉及深渊、祈愿、战斗记录、怪物资料、
*   签到记录、旅行者札记、素材日历等主要页面。
*
*   而错误态尤其致命:加载失败只弹一个3秒toast,随后页面停在空白,
*   全项目又没有任何面向用户的重试入口 ⇒ 用户只能杀掉App重进。
*
* 故此处把默认值改为可见占位,并新增 onRetry 供页面接入重试。
* 仍需要自定义文案的页面继续显式传参,行为不变;
* 有意保持空白的调用点(如 ItemScreenLoadingState 传了 `emptyContent = {}`)
* 也仍然按原样生效。
* */
@Composable
fun ContentLoadingLayout(
    loadingState: LoadingState,
    onRetry: (() -> Unit)? = null,
    loadingContent: @Composable () -> Unit = { ContentLoadingPlaceholder() },
    emptyContent: @Composable () -> Unit = { EmptyPlaceholder() },
    errorContent: @Composable () -> Unit = { ErrorPlaceholder(onRetry = onRetry) },
    defaultContent: @Composable () -> Unit = { ErrorPlaceholder(onRetry = onRetry) },
    noDataContent: @Composable () -> Unit = { EmptyPlaceholder() },
    successContent: @Composable () -> Unit = {}
) {
    Crossfade(
        targetState = loadingState, label = "",
        modifier = Modifier
            .fillMaxSize()
            .background(BackGroundColor)
    ) {

        when (it) {
            LoadingState.Loading -> {
                loadingContent.invoke()
            }

            LoadingState.Success -> {
                successContent.invoke()
            }

            LoadingState.Empty -> {
                emptyContent.invoke()
            }

            LoadingState.Error -> {
                errorContent.invoke()
            }

            LoadingState.NoData -> {
                noDataContent.invoke()
            }

            else -> {
                defaultContent.invoke()
            }
        }
    }
}
