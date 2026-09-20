package com.lianyi.paimonsnotebook

import com.lianyi.paimonsnotebook.common.data.ResultData
import com.lianyi.paimonsnotebook.common.util.json.JSON
import com.lianyi.paimonsnotebook.common.util.metadata.genshin.uigf.UIGFHelper
import com.lianyi.paimonsnotebook.common.util.parameter.getParameterizedType
import com.lianyi.paimonsnotebook.common.web.hoyolab.hk4e.event.gacha_info.BeyondGachaLogItem
import com.lianyi.paimonsnotebook.common.web.hoyolab.hk4e.event.gacha_info.BeyondGachaLogPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/*
* 千星奇域(UGC)祈愿记录解析测试
*
* ⚠️ 本文件与 AnnouncementParsingTest 的处境**不同**,必须说清楚:
*
* 公告那次事故的根因是"字段类型写错"(has_content 声明 Int,服务端给 BOOLEAN),
* 而当时测试全用自造数据所以零检出力。按纪律本应喂真实响应字节 ——
* 但千星奇域这条链路**拿不到含数据的真实样本**:本机账号
* (uid 338131141 / cn_gf01)的 6 个 UGC 类型码全部返回 retcode 0 且 list 为空
* (实测记录见夹具 BeyondGachaLog.json 与 AGENTS.md)。
*
* 因此本文件分两层:
*   1. 夹具驱动的**真实响应**测试 —— 验证空响应能被正确解析(信封 + total 类型);
*   2. **类型宽容性**测试 —— 这是本功能真正的风险点:既然无法确知服务端
*      把 id/schedule_id/rank_type/is_up/op_gacha_type 返回成数字还是字符串,
*      就断言**两种形式都能解析成功**,而不是赌其中一种。
*      这正是不重演公告事故的做法:公告是"赌 Int 结果错了",
*      这里是"不赌,两种都收"。
* */
class BeyondGachaLogParsingTest {

    private fun fixture(name: String): File {
        val testDir = sequenceOf(
            File("src/test/java/com/lianyi/paimonsnotebook"),
            File("app/src/test/java/com/lianyi/paimonsnotebook")
        ).firstOrNull { it.isDirectory }
            ?: File(javaClass.protectionDomain.codeSource.location.toURI()).parentFile

        return File(testDir, name)
    }

    /*
    * 真实响应(空)必须能解析
    *
    * 走真实调用链:ResultData 信封 + getParameterizedType,
    * 否则泛型擦除会让 data 退化成 LinkedTreeMap。
    * */
    @Test
    fun `真实空响应可被解析`() {
        val file = fixture("BeyondGachaLog.json")
        assumeTrue("缺少真实响应夹具: ${file.absolutePath}", file.exists())

        val result = JSON.parse<ResultData<BeyondGachaLogPage>>(
            file.readText(),
            getParameterizedType(ResultData::class.java, BeyondGachaLogPage::class.java)
        )

        assertEquals("retcode 应为 0", 0, result.retcode)

        val data = result.data
        assertNotNull("data 不应为 null", data)

        //实测该端点 data 只有 total 与 list(没有 page/size/region)
        assertEquals("total 应为字符串 \"0\"", "0", data!!.total)
        assertTrue("list 应为空", data.list.isEmpty())
    }

    /*
    * 核心:字段类型无论数字还是字符串都必须能解析
    *
    * 服务端把标量返回成数字或字符串两种形式都有可能
    * (胡桃的 C# 模型对这几个字段同时开了 AllowReadingFromString 与
    * NumberString,即它自己也遇到过两种形式)。
    *
    * 这条用例对"类型写错"有真实检出力:若把 BeyondGachaLogItem 的字段
    * 改成 Int/Long,下面的数字用例仍过、但**字符串用例会失败**;
    * 反之改成严格 String 且不做宽松处理,数字用例会失败。
    * 只有"两种都收"的实现才能同时通过 —— 这正是我们要锁住的行为。
    * */
    @Test
    fun `字段为数字时也能解析`() {
        val json = """
        {"retcode":0,"message":"OK","data":{"total":"1","list":[
          {"id":1787764800000066241,"uid":338131141,"region":"cn_gf01",
           "schedule_id":1001,"item_type":"角色","item_id":10000114,
           "item_name":"丝柯克","rank_type":5,"is_up":1,
           "time":"2026-08-27 01:05:16","op_gacha_type":2000}
        ]}}
        """.trimIndent()

        val data = parse(json)

        val item = data.list.single()
        assertEquals("1787764800000066241", item.id)
        assertEquals("338131141", item.uid)
        assertEquals("1001", item.schedule_id)
        assertEquals("10000114", item.item_id)
        assertEquals("5", item.rank_type)
        assertEquals("1", item.is_up)
        assertEquals("2000", item.op_gacha_type)
        assertEquals("丝柯克", item.item_name)
    }

    /*
    * 字段为字符串时同样要能解析
    *
    * 这也是 UIGF v4.2 规范定义的形态:规范里 hk4e_ugc 的
    * id/schedule_id/item_id/rank_type/op_gacha_type 全部是 string
    * 且带 ^[0-9]+$ 的 pattern。
    * */
    @Test
    fun `字段为字符串时也能解析`() {
        val json = """
        {"retcode":0,"message":"OK","data":{"total":"1","list":[
          {"id":"1787764800000066241","uid":"338131141","region":"cn_gf01",
           "schedule_id":"1001","item_type":"角色","item_id":"10000114",
           "item_name":"丝柯克","rank_type":"5","is_up":"1",
           "time":"2026-08-27 01:05:16","op_gacha_type":"2000"}
        ]}}
        """.trimIndent()

        val data = parse(json)

        val item = data.list.single()
        assertEquals("1787764800000066241", item.id)
        assertEquals("1001", item.schedule_id)
        assertEquals("5", item.rank_type)
        assertEquals("2000", item.op_gacha_type)
    }

    /*
    * 缺字段 / null 不应抛异常,而是退化为空串
    *
    * 服务端在不同卡池/版本下可能省略 is_up 之类的字段;
    * 若实现里用了非空声明 + 严格解析,这里会抛 NPE 或
    * IllegalStateException,把整个响应拖垮(公告事故的同类风险)。
    * */
    @Test
    fun `缺字段或null不抛异常`() {
        val json = """
        {"retcode":0,"message":"OK","data":{"total":"1","list":[
          {"id":"1","uid":"2","item_name":"测试","time":"2026-01-01 00:00:00",
           "op_gacha_type":"1000","is_up":null}
        ]}}
        """.trimIndent()

        val data = parse(json)

        val item = data.list.single()
        assertEquals("1", item.id)
        assertEquals("测试", item.item_name)
        assertEquals("", item.is_up)
        assertEquals("", item.rank_type)
        assertEquals("", item.schedule_id)
    }

    private fun parse(json: String): BeyondGachaLogPage =
        JSON.parse<ResultData<BeyondGachaLogPage>>(
            json,
            getParameterizedType(ResultData::class.java, BeyondGachaLogPage::class.java)
        ).data!!

    /*
    * 千星奇域类型码必须与官方规范一致
    *
    * 规范 uigf.html 里 hk4e_ugc 条目的 op_gacha_type enum 明确列了这 6 个值;
    * 若代码里写错(例如漏掉 20011 这类细分档位),导入别人的文件时会
    * 归错卡池、导出时也会写出非法值。
    * */
    @Test
    fun `千星奇域类型码与规范一致`() {
        val expected = setOf("1000", "2000", "20011", "20012", "20021", "20022")

        assertEquals(
            "类型码集合必须与 UIGF v4.2 规范的 op_gacha_type enum 完全一致",
            expected,
            UIGFHelper.BeyondGachaType.all.toSet()
        )

        assertEquals("类型码数量应为 6", 6, UIGFHelper.BeyondGachaType.all.size)
    }

    /*
    * 拉取用的类型码只应是 1000 / 2000
    *
    * 20011/20012/20021/20022 是 2000 的细分档位(胡桃
    * GachaConfigTypeExtension 把它们归一化到 UGCAvatarEventWish),
    * 2000 会一并返回;若 queryList 里塞进 4 个细分码,会重复拉取同样数据。
    * */
    @Test
    fun `拉取类型码只有常驻与角色活动`() {
        assertEquals(
            setOf("1000", "2000"),
            UIGFHelper.BeyondGachaType.queryList.toSet()
        )
    }

    /*
    * 每个类型码都要有可读名称(界面上要显示)
    * */
    @Test
    fun `所有类型码都有名称`() {
        UIGFHelper.BeyondGachaType.all.forEach { type ->
            val name = UIGFHelper.getBeyondGachaName(type)
            assertTrue(
                "类型码 $type 不应回退成它自身(说明缺少名称映射)",
                name != type
            )
            assertTrue("类型码 $type 的名称不应为空", name.isNotBlank())
        }
    }

    /*
    * 数据类字段与规范要求的 8 个字段一一对应
    *
    * 规范 required: id/schedule_id/item_type/item_id/item_name/rank_type/time/op_gacha_type
    * 若代码里字段名写错(例如把 item_name 写成 name),
    * 导出时会缺字段、导入别人的文件时会读不到。
    * */
    @Test
    fun `条目字段名与规范一致`() {
        val fieldNames = BeyondGachaLogItem::class.java.declaredFields
            .map { it.name }
            .toSet()

        listOf(
            "id", "schedule_id", "item_type", "item_id",
            "item_name", "rank_type", "time", "op_gacha_type"
        ).forEach { field ->
            assertTrue(
                "BeyondGachaLogItem 应声明字段 $field(规范要求),实际字段=$fieldNames",
                fieldNames.contains(field)
            )
        }

        //规范里没有 name/gacha_type/count,不应出现(那是 hk4e 的字段)
        assertTrue("不应有 name 字段(规范用 item_name)", !fieldNames.contains("name"))
        assertTrue("不应有 count 字段", !fieldNames.contains("count"))
    }
}
