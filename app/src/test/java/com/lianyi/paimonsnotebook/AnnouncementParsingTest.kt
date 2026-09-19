package com.lianyi.paimonsnotebook

import com.lianyi.paimonsnotebook.common.data.ResultData
import com.lianyi.paimonsnotebook.common.util.json.JSON
import com.lianyi.paimonsnotebook.common.util.parameter.getParameterizedType
import com.lianyi.paimonsnotebook.common.web.hoyolab.hk4e.announcement.AnnouncementListData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/*
* 公告接口响应的**真实数据**解析测试
*
* 为什么必须用真实响应而不是自造对象:
* 2026-09-19 线上事故 —— has_content 被声明成 Int,而服务端返回的是 JSON
* 布尔值 true,Gson 抛 "Expected an int but was BOOLEAN at ...has_content",
* 公告列表整个加载失败(界面报"公告获取失败")。
*
* 而当时的 AnnouncementHelperTest 全部用自造数据(has_content = 1),
* 类型怎么写都能过,**完全掩盖了这个错误**。教训:凡是解析外部响应的
* data class,回归测试必须喂真实响应字节,自造数据只能验证纯逻辑。
*
* 夹具 AnnList.json 是 2026-09-19 从真实接口原样保存的响应字节
* (未做任何往返转换,保留服务端原始类型表示):
*   https://hk4e-ann-api.mihoyo.com/common/hk4e_cn/announcement/api/getAnnList
*   ?game=hk4e&game_biz=hk4e_cn&lang=zh-cn&bundle_id=hk4e_cn
*   &platform=pc&region=cn_gf01&level=60&uid=100000000
* */
class AnnouncementParsingTest {

    //与 ExampleUnitTest 一致:相对模块目录定位,并保留类路径兜底
    private fun fixture(name: String): File {
        val testDir = sequenceOf(
            File("src/test/java/com/lianyi/paimonsnotebook"),
            File("app/src/test/java/com/lianyi/paimonsnotebook")
        ).firstOrNull { it.isDirectory }
            ?: File(javaClass.protectionDomain.codeSource.location.toURI()).parentFile

        return File(testDir, name)
    }

    /*
    * 按真实调用链解析:AnnouncementClient 用 getAsJson<AnnouncementListData>(),
    * 它把响应包成 ResultData<T>(见 requests.kt:188)。
    * 所以必须先解信封再取 data —— 直接把整个响应体当成 AnnouncementListData
    * 会解析出一堆默认值(list 为 null),那种"通过"毫无意义。
    *
    * ⚠️ 必须用 getParameterizedType 显式给出嵌套泛型:
    * JSON.parse<T> 的 reified 只能拿到 ResultData::class.java,内层 T 会被擦除,
    * 导致 data 退化成 LinkedTreeMap 并抛 ClassCastException。
    * 真实代码也是这么传的(getAsJson -> getParameterizedType(ResultData, T))。
    * */
    private fun parseReal(): AnnouncementListData? {
        val file = fixture("AnnList.json")
        assumeTrue("缺少真实响应夹具: ${file.absolutePath}", file.exists())

        val result = JSON.parse<ResultData<AnnouncementListData>>(
            file.readText(),
            getParameterizedType(ResultData::class.java, AnnouncementListData::class.java)
        )

        assertEquals("retcode 应为 0", 0, result.retcode)

        return result.data
    }

    /*
    * 核心用例:真实响应必须能被 Gson 完整解析。
    *
    * 这一条就能挡住本次事故 —— has_content 一旦写回 Int,
    * Gson 会在解析该字段时抛 IllegalStateException,这里立刻失败。
    * */
    @Test
    fun `真实公告响应可被解析`() {
        val data = parseReal()

        assertNotNull("data 不应为 null", data)

        assertEquals("总条数应为 33", 33, data!!.total)
        assertEquals("时区应为 8", 8, data.timezone)

        val items = data.list.flatMap { it.list }
        assertEquals("展平后应有 33 条公告", 33, items.size)

        //分组:游戏公告 / 活动公告 / 千星奇域
        assertEquals(listOf(2, 1, 26), data.list.map { it.type_id })
    }

    /*
    * has_content 必须是布尔值可解析,且解析后与响应一致。
    *
    * 实测 33/33 条全为 true。
    * */
    @Test
    fun `has_content按布尔值解析`() {
        val items = parseReal()!!.list.flatMap { it.list }

        assertTrue("真实数据里应至少有一条 has_content=true", items.any { it.hasContent })
        assertTrue(
            "hasContent 应与 has_content 字段一致",
            items.all { it.hasContent == it.has_content })
    }

    /*
    * 关键字段不能因解析而丢失。
    *
    * 这些字段是列表界面直接要用的,缺任何一个都会显示异常。
    * */
    @Test
    fun `关键字段解析完整`() {
        val first = parseReal()!!.list.flatMap { it.list }.first()

        assertTrue("ann_id 应大于 0", first.ann_id > 0)
        assertTrue("title 不应为空", first.title.isNotBlank())
        assertTrue("type_label 不应为空", first.type_label.isNotBlank())
        assertTrue("start_time 不应为空", first.start_time.isNotBlank())
        assertTrue("end_time 不应为空", first.end_time.isNotBlank())
    }
}
