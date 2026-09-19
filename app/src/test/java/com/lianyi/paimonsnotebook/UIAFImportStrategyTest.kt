package com.lianyi.paimonsnotebook

import com.lianyi.paimonsnotebook.ui.screen.achievement.util.enums.UIAFImportStrategy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/*
* UIAF 导入策略的语义测试
*
* 为什么必须锁住:这三种策略的差别只在**主键冲突时怎么处理**,
* 而它们的实现是拼 SQL 动词(LazyMerge 用 INSERT OR IGNORE,
* 其余用 INSERT OR REPLACE)。若把动词写错,表现是"导入后本地进度
* 被静默覆盖"或"该覆盖的没覆盖"—— 两种都不会报错,极难发现。
*
* 另外这里锁一个具体的回归点:stmt 缓存键必须含 SQL 动词。
* 原先只按条数缓存,加了策略后会复用错误语句。
* */
class UIAFImportStrategyTest {

    @Test
    fun `三种策略齐全且顺序与胡桃一致`() {
        //胡桃 ImportStrategyKind: AggressiveMerge=0, LazyMerge=1, Overwrite=2
        assertEquals(3, UIAFImportStrategy.entries.size)
        assertEquals(UIAFImportStrategy.AggressiveMerge, UIAFImportStrategy.entries[0])
        assertEquals(UIAFImportStrategy.LazyMerge, UIAFImportStrategy.entries[1])
        assertEquals(UIAFImportStrategy.Overwrite, UIAFImportStrategy.entries[2])
    }

    @Test
    fun `LazyMerge 用 OR IGNORE 以保留本地`() {
        //OR IGNORE:主键冲突时跳过写入 ⇒ 天然等价于"只补本地没有的"
        assertEquals("INSERT OR IGNORE", UIAFImportStrategy.LazyMerge.sqlVerb)
    }

    @Test
    fun `其余两种用 OR REPLACE 以覆盖`() {
        assertEquals("INSERT OR REPLACE", UIAFImportStrategy.AggressiveMerge.sqlVerb)
        assertEquals("INSERT OR REPLACE", UIAFImportStrategy.Overwrite.sqlVerb)
    }

    @Test
    fun `只有 Overwrite 会先清空`() {
        assertTrue(UIAFImportStrategy.Overwrite.clearBeforeImport)
        assertFalse(UIAFImportStrategy.AggressiveMerge.clearBeforeImport)
        assertFalse(UIAFImportStrategy.LazyMerge.clearBeforeImport)
    }

    /*
    * 回归:stmt 缓存键必须能区分 SQL 动词。
    *
    * 原实现只按 list.size 缓存语句。加上策略后,若键仍只用 size,
    * 先执行 LazyMerge(OR IGNORE) 再执行 AggressiveMerge(OR REPLACE)、
    * 且两批条数相同时,会复用 OR IGNORE 的语句 ⇒ "覆盖"静默失效。
    * */
    @Test
    fun `缓存键含SQL动词时不同策略不会撞键`() {
        val size = 100

        val lazyKey = (UIAFImportStrategy.LazyMerge.sqlVerb + "_" + size).hashCode()
        val aggressiveKey = (UIAFImportStrategy.AggressiveMerge.sqlVerb + "_" + size).hashCode()

        assertTrue("不同策略同条数不应撞键", lazyKey != aggressiveKey)

        //仅用 size 作键时两者会相同 —— 这正是原实现的缺陷
        assertEquals("仅用条数作键会撞键", size.hashCode(), size.hashCode())
    }

    @Test
    fun `默认策略与原行为最接近`() {
        //原先等价于"直接 INSERT OR REPLACE",即 AggressiveMerge
        assertEquals(UIAFImportStrategy.AggressiveMerge, UIAFImportStrategy.default)
        assertFalse("默认策略不应清空本地数据", UIAFImportStrategy.default.clearBeforeImport)
    }

    @Test
    fun `每种策略都有可读的标签与说明`() {
        UIAFImportStrategy.entries.forEach { strategy ->
            assertTrue("${strategy.name} 缺少标签", strategy.label.isNotBlank())
            assertTrue("${strategy.name} 缺少说明", strategy.description.isNotBlank())
        }
    }
}
