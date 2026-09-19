package com.lianyi.paimonsnotebook

import com.lianyi.paimonsnotebook.common.util.metadata.genshin.uigf.UIGFExportVersion
import com.lianyi.paimonsnotebook.common.util.metadata.genshin.uigf.UIGFHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/*
* UIGF 导出格式的回归测试
*
* 背景:此前只支持导出 v4.0(v3.0 靠一个 Boolean 开关切换)。
* 现在支持 v4.0 / v4.1 / v4.2 / v3.0,版本选择从 Boolean 改为枚举。
*
* 依据 UIGF 官方规范(uigf.org/zh/standards/uigf.html)的版本说明:
*   v4.0 合并 SRGF,新增绝区零抽卡格式支持
*   v4.1 新增对星穹铁道 v3.4 新卡池类型的支持 —— 兼容 v4.1/v4.0*
*   v4.2 新增对于千星奇域的支持                —— 兼容 v4.1
* 官方原话:"对于无需处理星穹铁道的应用,v4.1 与 v4.0 兼容。"
*
* 本项目只做原神,故 v4.0/v4.1 的原神部分同构、仅 version 串不同
* (与胡桃一致:它 UIGF41ExportService 只是继承 40 并改 Version);
* v4.2 额外多一个顶层 hk4e_ugc 数组。
* */
class UIGFExportVersionTest {

    @Test
    fun `版本字符串符合官方pattern`() {
        //规范要求 version 匹配 ^v\d+\.\d+$
        val pattern = Regex("""^v\d+\.\d+$""")

        UIGFExportVersion.all.forEach { version ->
            assertTrue(
                "${version.label} 的值 '${version.storageValue}' 不符合规范 v{major}.{minor}",
                pattern.matches(version.storageValue)
            )
        }
    }

    @Test
    fun `默认版本是v4_0`() {
        //v4.0 兼容性最好,作为默认值
        assertEquals("v4.0", UIGFExportVersion.default.storageValue)
    }

    @Test
    fun `v3是唯一的legacy版本`() {
        val legacy = UIGFExportVersion.all.filter { it.isLegacyV3 }
        assertEquals(1, legacy.size)
        assertEquals("v3.0", legacy.first().storageValue)

        //v3 没有对应的 v4 版本枚举
        assertNull(legacy.first().uigfVersion)
    }

    @Test
    fun `v4各版本都带uigf版本且非legacy`() {
        val v4List = UIGFExportVersion.all.filterNot { it.isLegacyV3 }

        assertEquals(3, v4List.size)
        assertEquals(
            setOf("v4.0", "v4.1", "v4.2"),
            v4List.map { it.storageValue }.toSet()
        )

        v4List.forEach {
            assertFalse("${it.label} 不应被标记为 legacy", it.isLegacyV3)
            assertTrue("${it.label} 应有对应的 UIGFVersion", it.uigfVersion != null)
        }
    }

    @Test
    fun `全部可选项不含重复`() {
        val values = UIGFExportVersion.all.map { it.storageValue }
        assertEquals("存在重复项:$values", values.size, values.toSet().size)
    }

    @Test
    fun `从持久化值还原`() {
        assertEquals("v3.0", UIGFExportVersion.fromValue("v3.0").storageValue)
        assertEquals("v4.0", UIGFExportVersion.fromValue("v4.0").storageValue)
        assertEquals("v4.1", UIGFExportVersion.fromValue("v4.1").storageValue)
        assertEquals("v4.2", UIGFExportVersion.fromValue("v4.2").storageValue)
    }

    @Test
    fun `未知或空值回退到默认而不是抛异常`() {
        //旧版本存的是 Boolean,不会读到这里;但要保证脏数据不会让导出出错
        assertEquals(UIGFExportVersion.default, UIGFExportVersion.fromValue(null))
        assertEquals(UIGFExportVersion.default, UIGFExportVersion.fromValue(""))
        assertEquals(UIGFExportVersion.default, UIGFExportVersion.fromValue("v9.9"))
        assertEquals(UIGFExportVersion.default, UIGFExportVersion.fromValue("true"))
    }

    @Test
    fun `往返转换保持一致`() {
        UIGFExportVersion.all.forEach { version ->
            val roundTrip = UIGFExportVersion.fromValue(version.storageValue)
            assertEquals(
                "往返后不一致:${version.label}",
                version.storageValue,
                roundTrip.storageValue
            )
        }
    }

    @Test
    fun `v4_2是唯一需要输出hk4e_ugc的版本`() {
        //导出逻辑靠这个判断决定是否补空的 hk4e_ugc 数组
        val field = UIGFHelper.GameField.Hk4eUgc
        assertEquals("hk4e_ugc", field)

        val withUgc = UIGFExportVersion.all.filter {
            it.uigfVersion == UIGFHelper.UIGFVersion.V4_2
        }
        assertEquals(1, withUgc.size)
        assertEquals("v4.2", withUgc.first().storageValue)
    }

    @Test
    fun `enum的value与storageValue一致`() {
        //UIGFHelper.UIGFVersion.value 是写进 json 的字符串,
        //storageValue 是写进设置的字符串,两者必须一致,否则会出现
        //"设置显示 v4.1 但导出 v4.0" 这类错配
        assertEquals("v4.0", UIGFHelper.UIGFVersion.V4_0.value)
        assertEquals("v4.1", UIGFHelper.UIGFVersion.V4_1.value)
        assertEquals("v4.2", UIGFHelper.UIGFVersion.V4_2.value)
        assertEquals(
            UIGFHelper.UIGFVersion.V4_0.value,
            UIGFExportVersion.V4(UIGFHelper.UIGFVersion.V4_0).storageValue
        )
    }
}
