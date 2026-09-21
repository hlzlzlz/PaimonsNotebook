package com.lianyi.paimonsnotebook

import com.lianyi.paimonsnotebook.common.util.enums.LoadingState
import com.lianyi.paimonsnotebook.ui.screen.items.util.ItemScreenStateResolver
import org.junit.Assert.assertEquals
import org.junit.Test

/*
* 物品详情页加载状态收敛的回归测试
*
* 钉住的是真实崩溃路径:武器/角色列表为空时 currentItem 为 null,
* 若无条件置 Success,页面里 39 处 `currentItem!!` 会 NPE。
* */
class ItemScreenStateResolverTest {

    @Test
    fun 有条目且仍在加载时进入成功态() {
        assertEquals(
            LoadingState.Success,
            ItemScreenStateResolver.resolve(LoadingState.Loading, hasItem = true)
        )
    }

    @Test
    fun 无条目时绝不能进入成功态() {
        //这是崩溃的根源:Success 会让成功分支去解引用 null 的 currentItem
        val result = ItemScreenStateResolver.resolve(LoadingState.Loading, hasItem = false)

        assertEquals(LoadingState.Empty, result)
        assertEquals(
            "置为 Success 会导致 currentItem!! NPE",
            false,
            result == LoadingState.Success
        )
    }

    @Test
    fun 已处于错误态时不被覆盖() {
        //元数据缺失已置 Error,不能被收敛逻辑改回 Success
        assertEquals(
            LoadingState.Error,
            ItemScreenStateResolver.resolve(LoadingState.Error, hasItem = false)
        )
        assertEquals(
            LoadingState.Error,
            ItemScreenStateResolver.resolve(LoadingState.Error, hasItem = true)
        )
    }

    @Test
    fun 已处于空态时保持不变() {
        assertEquals(
            LoadingState.Empty,
            ItemScreenStateResolver.resolve(LoadingState.Empty, hasItem = false)
        )
    }

    @Test
    fun 已处于无数据态时保持不变() {
        assertEquals(
            LoadingState.NoData,
            ItemScreenStateResolver.resolve(LoadingState.NoData, hasItem = false)
        )
    }

    @Test
    fun 只有加载中才会被收敛() {
        //遍历所有状态,确认只有 Loading 会被改写
        LoadingState.entries
            .filter { it != LoadingState.Loading }
            .forEach { state ->
                assertEquals(
                    "非 Loading 状态($state)不应被改写",
                    state,
                    ItemScreenStateResolver.resolve(state, hasItem = true)
                )
                assertEquals(
                    "非 Loading 状态($state)不应被改写",
                    state,
                    ItemScreenStateResolver.resolve(state, hasItem = false)
                )
            }
    }
}
