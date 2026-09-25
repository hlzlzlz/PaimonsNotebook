package com.lianyi.paimonsnotebook.ui.screen.items.components.state

import androidx.compose.runtime.Composable
import com.lianyi.paimonsnotebook.common.components.loading.ContentLoadingLayout
import com.lianyi.paimonsnotebook.common.components.loading.ContentLoadingPlaceholder
import com.lianyi.paimonsnotebook.common.components.placeholder.EmptyPlaceholder
import com.lianyi.paimonsnotebook.common.components.placeholder.ErrorPlaceholder
import com.lianyi.paimonsnotebook.common.util.enums.LoadingState

/*
* 资料页(角色/武器/怪物/圣遗物)的加载状态包装
*
* errorText / emptyText 可定制:
*   - 角色与武器:元数据缺失 -> "缺少所需的元数据"(保持原文案)
*   - 怪物与圣遗物:给出可操作指引(去设置里同步元数据)
*
* ⚠️ emptyContent 原先传的是空 lambda `{}` —— 列表为空时整页**纯白**。
*    这属于 1.8.23 修过的"失败/空态渲染成纯背景色"同一类缺陷,此处一并修正:
*    空列表与"元数据缺失"是两种情况,都不该白屏。
* */
@Composable
internal fun ItemScreenLoadingState(
    loadingState: LoadingState,
    errorText: String = "缺少所需的元数据",
    emptyText: String = "没有可显示的内容",
    content: @Composable () -> Unit
) {
    ContentLoadingLayout(
        loadingState = loadingState,
        loadingContent = {
            ContentLoadingPlaceholder(
                text = "正在加载所需的数据",
            )
        },
        successContent = content,
        emptyContent = {
            EmptyPlaceholder(text = emptyText)
        },
        errorContent = {
            ErrorPlaceholder(text = errorText)
        },
        defaultContent = {}
    )
}
