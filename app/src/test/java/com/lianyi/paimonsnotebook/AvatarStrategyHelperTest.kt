package com.lianyi.paimonsnotebook

import com.lianyi.paimonsnotebook.common.util.hoyolab.strategy.AvatarStrategyHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/*
* 角色攻略链接的回归测试
*
* 背景:该入口曾经"静默消失" —— 原实现先请求胡桃 /strategy/all 取精确攻略帖ID,
* 而该路由已下线(实测两域全404),取不到就返回null,调用方写的是
* strategyUrl?.let { TextButton(...) },于是按钮直接不渲染、界面上毫无提示。
* 现在改为直接拼B站Wiki直链(胡桃工具箱唯一还可用的攻略命令也是这个URL格式)。
*
* 这里锁死三件事:
*   1. 角色名被正确URL编码(中文不能原样进URL)
*   2. 非空角色名永远返回可用链接(即按钮不会因取数失败而消失)
*   3. 空角色名返回null(避免拼出 .../ys//攻略 这种坏链接)
* */
class AvatarStrategyHelperTest {

    @Test
    fun `中文角色名会被URL编码`() {
        val url = AvatarStrategyHelper.getStrategyUrl(10000002, "神里绫华")

        assertTrue("应指向B站原神Wiki", url!!.startsWith("https://wiki.biligame.com/ys/"))
        assertTrue("中文名必须被编码", url.contains("%E7%A5%9E%E9%87%8C%E7%BB%AB%E5%8D%8E"))
        assertTrue("应带攻略子页", url.endsWith("%E6%94%BB%E7%95%A5"))
        //确保没有把原始中文直接拼进URL
        assertTrue("不应出现未编码的中文", !url.contains("神里"))
    }

    @Test
    fun `普通中文角色名可拼出链接`() {
        //这几个名字在实测中该Wiki均返回200
        listOf("胡桃", "钟离", "雷电将军", "那维莱特", "芙宁娜").forEach { name ->
            val url = AvatarStrategyHelper.getStrategyUrl(1, name)
            assertTrue("$name 应能拼出链接", !url.isNullOrBlank())
            assertTrue("$name 的链接应指向Wiki", url!!.startsWith("https://wiki.biligame.com/ys/"))
        }
    }

    @Test
    fun `英文角色名不做多余改动`() {
        val url = AvatarStrategyHelper.getStrategyUrl(1, "HuTao")
        assertEquals("https://wiki.biligame.com/ys/HuTao/%E6%94%BB%E7%95%A5", url)
    }

    @Test
    fun `含空格的角色名空格会被编码`() {
        val url = AvatarStrategyHelper.getStrategyUrl(1, "Raiden Shogun")
        //URLEncoder 把空格编成 + (query 语义),这里只是锁定当前行为,
        //Wiki 对 + 与 %20 的容忍度以实测为准;关键是不能出现裸空格断链
        assertTrue("不应有裸空格", !url!!.contains(' '))
    }

    @Test
    fun `空角色名返回null而不是坏链接`() {
        assertNull(AvatarStrategyHelper.getStrategyUrl(1, ""))
        assertNull(AvatarStrategyHelper.getStrategyUrl(1, "   "))
    }
}
