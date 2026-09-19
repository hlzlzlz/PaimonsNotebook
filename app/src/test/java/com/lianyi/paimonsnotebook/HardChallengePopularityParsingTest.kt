package com.lianyi.paimonsnotebook

import com.lianyi.paimonsnotebook.common.data.ResultData
import com.lianyi.paimonsnotebook.common.util.json.JSON
import com.lianyi.paimonsnotebook.common.util.parameter.getParameterizedType
import com.lianyi.paimonsnotebook.common.web.hoyolab.takumi.game_record.hard_challenge.HardChallengePopularityData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/*
* 幽境危战全服热门角色 接口响应的**真实数据**解析测试
*
* 为什么必须喂真实响应字节(而不是自造对象):
* 2026-09-19 公告接口的 has_content 被按命名惯例写成 Int,服务端实际返回
* JSON 布尔值,导致整个响应解析失败、功能线上不可用;而当时全部用自造数据
* 的测试**一个都没红**。本文件按同一条纪律建立:解析外部响应的 data class
* 必须有真实字节驱动的用例,自造数据对"字段类型写错"零检出力。
*
* 夹具 HardChallengePopularity.json 是 2026-09-19 从真实接口原样保存的
* 响应字节(WebClient 直接写盘,未经任何字符串往返转换):
*   GET https://api-takumi-record.mihoyo.com/game_record/app/genshin/api
*       /hard_challenge/popularity?role_id=<uid>&server=cn_gf01
*   实测 retcode=0 / 3,158 B / avatar_list 16 条
* */
class HardChallengePopularityParsingTest {

    //与 AnnouncementParsingTest 一致:相对模块目录定位,并保留类路径兜底
    private fun fixture(name: String): File {
        val testDir = sequenceOf(
            File("src/test/java/com/lianyi/paimonsnotebook"),
            File("app/src/test/java/com/lianyi/paimonsnotebook")
        ).firstOrNull { it.isDirectory }
            ?: File(javaClass.protectionDomain.codeSource.location.toURI()).parentFile

        return File(testDir, name)
    }

    /*
    * 按真实调用链解析:GameRecordClient 用 getAsJson<HardChallengePopularityData>(),
    * 它把响应包成 ResultData<T>。故必须先解信封再取 data —— 把整个响应体
    * 直接当成 HardChallengePopularityData 会解析出一堆默认值(avatar_list 为 null),
    * 那种"通过"毫无意义。
    *
    * ⚠️ 必须用 getParameterizedType 显式给出嵌套泛型,否则泛型擦除会让
    * data 退化成 LinkedTreeMap 并抛 ClassCastException。
    * */
    private fun parseReal(): HardChallengePopularityData? {
        val file = fixture("HardChallengePopularity.json")
        assumeTrue("缺少真实响应夹具: ${file.absolutePath}", file.exists())

        val result = JSON.parse<ResultData<HardChallengePopularityData>>(
            file.readText(),
            getParameterizedType(
                ResultData::class.java,
                HardChallengePopularityData::class.java
            )
        )

        assertEquals("retcode 应为 0", 0, result.retcode)

        return result.data
    }

    /*
    * 核心用例:真实响应必须能被 Gson 完整解析。
    *
    * avatar_id / rarity 一旦被写成 String,或 image/name/element 被写成非字符串,
    * Gson 会在此抛 IllegalStateException,本用例立刻失败。
    * */
    @Test
    fun `真实热门角色响应可被解析`() {
        val data = parseReal()

        assertNotNull("data 不应为 null", data)

        val list = data!!.avatar_list
        assertNotNull("avatar_list 不应为 null", list)
        assertEquals("实测应有 16 条", 16, list!!.size)
    }

    /*
    * 逐字段核对类型与取值范围。
    *
    * 这些断言同时锁住"类型写错"与"字段名写错"(后者会静默解析成 null,
    * 而 avatar_id 是 Int、name 是 String 非空声明,一旦取不到就会抛异常)。
    * */
    @Test
    fun `每条记录字段完整且类型正确`() {
        val list = parseReal()!!.avatar_list!!

        list.forEachIndexed { index, avatar ->
            assertTrue("第 $index 条 avatar_id 应为正数", avatar.avatar_id > 0)
            assertTrue("第 $index 条 name 不应为空", avatar.name.isNotBlank())
            assertTrue("第 $index 条 element 不应为空", avatar.element.isNotBlank())
            assertTrue(
                "第 $index 条 image 应为完整 http(s) 地址,实际=${avatar.image}",
                avatar.image.startsWith("https://") || avatar.image.startsWith("http://")
            )
            assertTrue(
                "第 $index 条 rarity 应在 1..5,实际=${avatar.rarity}",
                avatar.rarity in 1..5
            )
        }
    }

    /*
    * 该端点自带 name/image/rarity,不需要查本地元数据。
    *
    * 这一点决定了界面实现:可以直接用响应里的 image 喂 NetworkImage、
    * 用 name 显示,不必像剧诗全服统计那样回查 AvatarService。
    * */
    @Test
    fun `响应自带名称与图片无需查元数据`() {
        val list = parseReal()!!.avatar_list!!

        assertTrue("所有条目都应自带非空 name", list.all { it.name.isNotBlank() })
        assertTrue("所有条目都应自带 image", list.all { it.image.isNotBlank() })

        //实测第一条是希诺宁(10000103),用于锁定字段错位(image 串到 name 上等)
        assertEquals(10000103, list.first().avatar_id)
        assertEquals("希诺宁", list.first().name)
        assertEquals("Geo", list.first().element)
        assertEquals(5, list.first().rarity)
    }

    /*
    * 服务端只给有序名单,**不含任何比例数值**。
    *
    * 这条用例是对界面实现的约束:不能像剧诗全服统计那样显示百分比,
    * 否则就是编造数据。若将来服务端加了比例字段,这里会失败并提醒同步界面。
    * */
    @Test
    fun `响应不含比例字段`() {
        val file = fixture("HardChallengePopularity.json")
        assumeTrue(file.exists())

        val raw = file.readText()
        listOf("\"rate\"", "\"Rate\"", "\"percent\"", "\"ratio\"").forEach { key ->
            assertTrue(
                "响应里不应出现 $key,说明服务端不提供比例,界面不得显示百分比",
                !raw.contains(key)
            )
        }
    }
}
