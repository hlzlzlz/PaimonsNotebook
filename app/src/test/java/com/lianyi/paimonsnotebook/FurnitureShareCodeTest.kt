package com.lianyi.paimonsnotebook

import com.lianyi.paimonsnotebook.common.web.hoyolab.takumi.event.calculate.FurnitureListData
import com.lianyi.paimonsnotebook.common.web.hoyolab.takumi.event.calculate.FurnitureShareCodeParser
import com.lianyi.paimonsnotebook.common.util.json.JSON
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/*
* 洞天摹本分享码解析 + 家具清单解析的回归测试
*
* 分享码处理是"用户输入"路径,分支多且最容易漏;
* 家具清单解析则要确认字段名与默认值(缺 num/lack_num 时不能整份解析失败)。
* */
class FurnitureShareCodeTest {

    // ---- 分享码抽取 ----

    @Test
    fun 纯数字码原样返回() {
        assertEquals("1234567890", FurnitureShareCodeParser.extract("1234567890"))
    }

    @Test
    fun 从中文提示文本中抽取() {
        assertEquals("1234567890", FurnitureShareCodeParser.extract("摹本分享码：1234567890"))
        assertEquals("1234567890", FurnitureShareCodeParser.extract("摹本分享码: 1234567890"))
    }

    @Test
    fun 从URL中抽取share_code参数() {
        assertEquals(
            "1234567890",
            FurnitureShareCodeParser.extract("https://webstatic.mihoyo.com/x?share_code=1234567890&other=1")
        )
    }

    @Test
    fun 带空白与换行的输入可抽取() {
        assertEquals("1234567890", FurnitureShareCodeParser.extract("  1234567890\n"))
    }

    @Test
    fun 无数字返回null() {
        assertNull(FurnitureShareCodeParser.extract("没有数字"))
        assertNull(FurnitureShareCodeParser.extract(""))
        assertNull(FurnitureShareCodeParser.extract(null))
        assertNull(FurnitureShareCodeParser.extract("   "))
    }

    @Test
    fun 过短数字不被当作分享码() {
        //避免把"摹本"旁边的年份等短数字误判
        assertNull(FurnitureShareCodeParser.extract("2026"))
        assertNull(FurnitureShareCodeParser.extract("1234567"))
    }

    @Test
    fun URL参数优先于其它数字() {
        //URL 里的 share_code 最明确,不应被路径里的其它长数字抢走
        assertEquals(
            "9876543210",
            FurnitureShareCodeParser.extract("https://x.com/12345678901?share_code=9876543210")
        )
    }

    @Test
    fun 有效性判断() {
        assertTrue(FurnitureShareCodeParser.isValid("1234567890"))
        assertTrue(FurnitureShareCodeParser.isValid("摹本分享码：1234567890"))
        assertFalse(FurnitureShareCodeParser.isValid("abc"))
        assertFalse(FurnitureShareCodeParser.isValid(null))
    }

    // ---- 家具清单解析 ----

    @Test
    fun 解析完整家具清单() {
        val json = """
            {"retcode":0,"message":"OK","data":{"list":[
              {"id":101,"name":"松木折屏","icon_url":"https://x/a.png","num":3,"level":1,"lack_num":1}
            ]}}
        """.trimIndent()

        val data = JSON.parse<FurnitureListData>(
            JSON.parse<com.google.gson.JsonObject>(json).getAsJsonObject("data").toString()
        )

        assertEquals(1, data.list.size)
        assertEquals(101, data.list[0].id)
        assertEquals("松木折屏", data.list[0].name)
        assertEquals(3, data.list[0].num)
        assertEquals(1, data.list[0].lackNum)
    }

    @Test
    fun 缺少可选数值字段时用默认值而非解析失败() {
        //num/lack_num/level 只在带 uid+region 时返回,缺失必须能容忍
        val data = JSON.parse<FurnitureListData>(
            """{"list":[{"id":1,"name":"桌子","icon_url":"u"}]}"""
        )

        assertEquals(1, data.list.size)
        assertEquals(0, data.list[0].num)
        assertEquals(0, data.list[0].lackNum)
        assertEquals(0, data.list[0].level)
    }

    @Test
    fun 缺少not_calc_list时为空列表() {
        //compute 端点不返回该字段;blueprint 才返回
        val data = JSON.parse<FurnitureListData>("""{"list":[]}""")

        assertTrue(data.notCalcList.isEmpty())
    }

    @Test
    fun 解析not_calc_list() {
        val data = JSON.parse<FurnitureListData>(
            """{"list":[],"not_calc_list":[{"id":9,"name":"摆件","icon_url":"u"}]}"""
        )

        assertEquals(1, data.notCalcList.size)
        assertEquals(9, data.notCalcList[0].id)
    }

    @Test
    fun 空响应解析为空列表而非崩溃() {
        val data = JSON.parse<FurnitureListData>("""{}""")

        assertTrue(data.list.isEmpty())
        assertTrue(data.notCalcList.isEmpty())
    }
}
