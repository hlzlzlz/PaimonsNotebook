package com.lianyi.paimonsnotebook.ui.screen.items.util

import com.lianyi.paimonsnotebook.common.util.enums.LoadingState

/*
* 物品详情页(角色/武器)加载状态的收敛规则
*
* 背景:WeaponScreenViewModel 与 AvatarScreenViewModel 原先在 init 末尾**无条件**
* 置 Success。但列表为空时 onClickItem 从未被调用,currentItem 仍是 null,
* 于是成功分支被渲染,而页面里共 39 处 `currentItem!!` 会直接 NPE 崩溃。
*
* 该状态可复现:元数据存在但对应列表为空(或条目全被过滤掉)时。
*
* 抽成纯函数以便单测 —— 这类状态机判断最容易在改动时被写回原样。
* */
object ItemScreenStateResolver {

    /*
    * 收敛 loadingState
    *
    * - 已经不是 Loading(例如元数据缺失已置 Error):保持原状态,不覆盖
    * - 仍是 Loading 且有条目:Success
    * - 仍是 Loading 但无条目:Empty(渲染空内容),绝不能置 Success
    * */
    fun resolve(current: LoadingState, hasItem: Boolean): LoadingState {
        if (current != LoadingState.Loading) return current

        return if (hasItem) LoadingState.Success else LoadingState.Empty
    }
}
