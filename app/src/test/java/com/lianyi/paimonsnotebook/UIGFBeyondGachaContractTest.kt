package com.lianyi.paimonsnotebook

import com.lianyi.paimonsnotebook.common.util.metadata.genshin.uigf.UIGFExportVersion
import com.lianyi.paimonsnotebook.common.util.metadata.genshin.uigf.UIGFHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/*
* UIGF v4.2 千星奇域(hk4e_ugc)字段契约测试
*
* 背景:实现千星奇域导出时,官方 schema 的写法**自相矛盾** ——
* hk4e_ugc 声明了 "type": "array",却把 uid/timezone/lang/list 这些
* properties 直接挂在下面、没有 items。只读那一段很容易误判成"对象"。
*
* 判定依据(两条独立证据):
*   1. "type": "array" 是明确的;
*   2. 参考实现(胡桃 UIGF42.cs)声明为
*      `ImmutableArray<UIGFEntry<Hk4eUGCItem>>` —— 与 hk4e 用**同一个**
*      UIGFEntry<T> 模板,说明它就是数组,只是元素类型不同。
* ⇒ 结论:hk4e_ugc 是**数组**,元素为 {uid, timezone, lang, list}。
*
* 这些用例把该结论钉住,避免以后有人照 schema 的 properties 段
* 改回"对象"而写出别人解析不了的文件。
* */
class UIGFBeyondGachaContractTest {

    @Test
    fun `hk4e_ugc字段名与hk4e并列`() {
        assertEquals("hk4e", UIGFHelper.GameField.Hk4e)
        assertEquals("hk4e_ugc", UIGFHelper.GameField.Hk4eUgc)
    }

    /*
    * 条目字段必须是规范要求的 8 个
    *
    * 规范 hk4e_ugc 条目的 required:
    *   id, schedule_id, item_type, item_id, item_name, rank_type, time, op_gacha_type
    * */
    @Test
    fun `条目必需字段与规范一致`() {
        val expected = setOf(
            "id", "schedule_id", "item_type", "item_id",
            "item_name", "rank_type", "time", "op_gacha_type"
        )

        assertEquals(
            "UIGFHelper.Field.Beyond.requiredFields 必须与规范 required 完全一致",
            expected,
            UIGFHelper.Field.Beyond.requiredFields.toSet()
        )
    }

    /*
    * hk4e_ugc 与 hk4e 的条目字段差异必须保持
    *
    * 少了(uigf_gacha_type/gacha_type/count)与多了(schedule_id/op_gacha_type)
    * 是这个字段与 hk4e 的核心区别;若哪天有人"统一"了两边,导出会写出
    * 不符合规范的文件。
    * */
    @Test
    fun `ugc条目不含hk4e专有字段`() {
        val beyondFields = UIGFHelper.Field.Beyond.requiredFields.toSet()

        listOf("uigf_gacha_type", "gacha_type", "count").forEach { hk4eOnly ->
            assertTrue(
                "hk4e_ugc 条目不应包含 hk4e 专有字段 $hk4eOnly",
                !beyondFields.contains(hk4eOnly)
            )
        }

        //名称字段是 item_name,不是 name
        assertTrue(beyondFields.contains("item_name"))
        assertTrue("不应使用 hk4e 的 name 字段", !beyondFields.contains("name"))
    }

    /*
    * 只有 v4.2 才输出 hk4e_ugc
    *
    * v4.0/v4.1 没有该字段;若给它们也写,老工具可能报错。
    * */
    @Test
    fun `只有v4_2导出hk4e_ugc`() {
        val v42 = UIGFExportVersion.all.filter {
            it.uigfVersion == UIGFHelper.UIGFVersion.V4_2
        }

        assertEquals("应只有一个 v4.2 选项", 1, v42.size)
        assertEquals("v4.2", v42.first().storageValue)

        //v4.0 / v4.1 不带该版本
        listOf(
            UIGFHelper.UIGFVersion.V4_0,
            UIGFHelper.UIGFVersion.V4_1
        ).forEach { version ->
            assertTrue(
                "$version 不应被当成 v4.2",
                version != UIGFHelper.UIGFVersion.V4_2
            )
        }
    }

    /*
    * v4.2 的 description 不应再声称"不采集千星奇域"
    *
    * 这是用户可见文案:功能已实现后若还写着"该字段将导出为空",
    * 会误导用户以为导出的文件不含千星奇域数据。
    * */
    @Test
    fun `v4_2描述已反映支持千星奇域`() {
        val description = UIGFHelper.UIGFVersion.V4_2.description

        assertTrue(
            "v4.2 描述应说明会导出千星奇域记录,实际=$description",
            description.contains("千星奇域")
        )
        assertTrue(
            "v4.2 描述不应再声称'暂不采集'或'导出为空',实际=$description",
            !description.contains("暂不采集") && !description.contains("导出为空")
        )
    }
}
