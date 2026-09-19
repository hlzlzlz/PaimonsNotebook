package com.lianyi.paimonsnotebook

import com.lianyi.paimonsnotebook.common.database.cultivate.entity.CultivateItemMaterials
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/*
* 养成材料"持有数"文案的回归测试
*
* 背景:服务端 batch_compute 返回 num(需要总数)与 lack_num(缺少数),
* 二者相减即玩家实际持有(与胡桃 InventoryService 算法一致)。
* PN 原先只存 count/lackCount,把持有数丢了。
*
* 为什么必须测边界:持有数未知时若显示"持有 0",用户会以为材料真的
* 一个都没有 —— 这种误导比不显示更糟。故未知必须返回 null(隐藏该行)。
* */
class CultivateItemMaterialsTest {

    private fun material(
        count: Int = 100,
        lackCount: Int = 0,
        ownedCount: Int = CultivateItemMaterials.OWNED_COUNT_UNKNOWN,
        status: Int = 0
    ) = CultivateItemMaterials(
        itemId = 1,
        cultivateItemId = 2,
        projectId = 3,
        count = count,
        lackCount = lackCount,
        ownedCount = ownedCount,
        status = status
    )

    @Test
    fun `未知持有数返回null以隐藏该行`() {
        //-1 是"服务端未返回库存"的标记,不能显示成"持有 -1"或"持有 0"
        assertNull(material(ownedCount = CultivateItemMaterials.OWNED_COUNT_UNKNOWN).getOwnedCountText())
    }

    @Test
    fun `已知持有数显示为持有N`() {
        assertEquals("持有 0", material(ownedCount = 0).getOwnedCountText())
        assertEquals("持有 123", material(ownedCount = 123).getOwnedCountText())
        assertEquals("持有 999999", material(ownedCount = 999999).getOwnedCountText())
    }

    /*
    * 持有数为 0 是**合法且需要显示**的:它表示服务端确实返回了库存信息,
    * 且玩家该材料为 0。这与"未知"是两回事,不能混为一谈。
    * */
    @Test
    fun `持有0与未知是两种不同状态`() {
        assertEquals("持有 0", material(ownedCount = 0).getOwnedCountText())
        assertNull(material(ownedCount = -1).getOwnedCountText())
    }

    /*
    * 回归:默认构造值必须是"未知"而非 0。
    *
    * 老数据经 AutoMigration 补列时拿到的就是默认值 —— 若默认值是 0,
    * 所有历史计划都会显示"持有 0"。
    * */
    @Test
    fun `默认持有数为未知而非0`() {
        val m = CultivateItemMaterials(
            itemId = 1,
            cultivateItemId = 2,
            projectId = 3,
            count = 10,
            lackCount = 5,
            status = 0
        )

        assertEquals(CultivateItemMaterials.OWNED_COUNT_UNKNOWN, m.ownedCount)
        assertEquals(-1, CultivateItemMaterials.OWNED_COUNT_UNKNOWN)
        assertNull("默认构造的持有数不应显示", m.getOwnedCountText())
    }

    @Test
    fun `原有展示逻辑不受影响`() {
        //showLackNum=false 时显示所需数量
        assertEquals("100", material(count = 100, lackCount = 20).getShowContentAndColor(false).first)
        //showLackNum=true 且未完成时显示缺少数量
        assertEquals("20", material(count = 100, lackCount = 20).getShowContentAndColor(true).first)
    }
}
