package com.lianyi.paimonsnotebook

import com.lianyi.paimonsnotebook.common.web.hoyolab.hk4e.announcement.AnnouncementContentItem
import com.lianyi.paimonsnotebook.common.web.hoyolab.hk4e.announcement.AnnouncementGroup
import com.lianyi.paimonsnotebook.common.web.hoyolab.hk4e.announcement.AnnouncementHelper
import com.lianyi.paimonsnotebook.common.web.hoyolab.hk4e.announcement.AnnouncementItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/*
* 公告合并与清洗的回归测试
*
* 背景:公告是"列表"与"正文"两个端点分开返回的,必须本地按 ann_id 合并;
* 而正文里可能带 <t ...> 时间占位标签,不剥掉会原样显示成尖括号。
* 这两件事都在 AnnouncementHelper 里,属纯逻辑,适合锁死。
* */
class AnnouncementHelperTest {

    private fun item(
        annId: Int,
        title: String = "标题$annId",
        startTime: String = "2026-09-12 21:15:00",
        hasContent: Boolean = true
    ) = AnnouncementItem(
        ann_id = annId,
        title = title,
        subtitle = title,
        banner = "",
        type_label = "游戏公告",
        tag_label = "",
        tag_icon = "",
        start_time = startTime,
        end_time = "2026-11-03 00:00:00",
        has_content = hasContent
    )

    private fun content(annId: Int, html: String) = AnnouncementContentItem(
        ann_id = annId,
        title = "标题$annId",
        subtitle = "",
        banner = "",
        content = html
    )

    @Test
    fun `正文按ann_id回填到对应条目`() {
        val groups = listOf(
            AnnouncementGroup(
                list = listOf(item(100), item(200)),
                type_id = 1,
                type_label = "活动公告"
            )
        )
        val contents = listOf(
            content(100, "<p>正文一百</p>"),
            content(200, "<p>正文两百</p>")
        )

        val merged = AnnouncementHelper.merge(groups, contents)

        assertEquals(1, merged.size)
        assertEquals("<p>正文一百</p>", merged[0].list.first { it.ann_id == 100 }.content)
        assertEquals("<p>正文两百</p>", merged[0].list.first { it.ann_id == 200 }.content)
    }

    @Test
    fun `t时间标签被折叠为纯文本`() {
        val groups = listOf(
            AnnouncementGroup(list = listOf(item(1)), type_id = 1, type_label = "活动公告")
        )
        val contents = listOf(
            content(1, """活动时间：<t time="1234567890">2026/09/12 21:15</t> 开始""")
        )

        val merged = AnnouncementHelper.merge(groups, contents)

        val html = merged[0].list.first().content
        assertTrue("不应残留 <t 标签", !html.contains("<t "))
        assertTrue("不应残留 </t>", !html.contains("</t>"))
        assertTrue("应保留标签内文本", html.contains("2026/09/12 21:15"))
        assertTrue("应保留标签外文本", html.contains("活动时间："))
    }

    @Test
    fun `其它html标签原样保留交给渲染层`() {
        val html = """<p style="x"><img src="https://example.com/a.jpg"></p>"""
        val groups = listOf(
            AnnouncementGroup(list = listOf(item(1)), type_id = 1, type_label = "活动公告")
        )

        val merged = AnnouncementHelper.merge(groups, listOf(content(1, html)))

        val out = merged[0].list.first().content
        assertTrue("p 标签应保留", out.contains("<p"))
        assertTrue("img 标签应保留", out.contains("<img"))
        assertEquals(html, out)
    }

    @Test
    fun `组内按开始时间倒序`() {
        val groups = listOf(
            AnnouncementGroup(
                list = listOf(
                    item(1, startTime = "2026-01-01 00:00:00"),
                    item(2, startTime = "2026-09-12 21:15:00"),
                    item(3, startTime = "2026-05-05 05:05:05")
                ),
                type_id = 1,
                type_label = "活动公告"
            )
        )

        val merged = AnnouncementHelper.merge(groups, null)

        assertEquals(listOf(2, 3, 1), merged[0].list.map { it.ann_id })
    }

    @Test
    fun `正文缺失时不崩且内容为空`() {
        val groups = listOf(
            AnnouncementGroup(list = listOf(item(1), item(2)), type_id = 1, type_label = "活动公告")
        )

        //contents 为 null(接口失败或未返回)
        val merged = AnnouncementHelper.merge(groups, null)

        assertEquals(2, merged[0].list.size)
        assertTrue(merged[0].list.all { it.content.isEmpty() })
    }

    @Test
    fun `部分条目无正文时只回填有正文的`() {
        val groups = listOf(
            AnnouncementGroup(list = listOf(item(1), item(2)), type_id = 1, type_label = "活动公告")
        )

        //只给了 ann_id=2 的正文
        val merged = AnnouncementHelper.merge(groups, listOf(content(2, "<p>只有我有正文</p>")))

        assertEquals("", merged[0].list.first { it.ann_id == 1 }.content)
        assertEquals("<p>只有我有正文</p>", merged[0].list.first { it.ann_id == 2 }.content)
    }

    @Test
    fun `分组顺序保持不变`() {
        val groups = listOf(
            AnnouncementGroup(list = listOf(item(1)), type_id = 2, type_label = "游戏公告"),
            AnnouncementGroup(list = listOf(item(2)), type_id = 1, type_label = "活动公告"),
            AnnouncementGroup(list = listOf(item(3)), type_id = 26, type_label = "千星奇域")
        )

        val merged = AnnouncementHelper.merge(groups, null)

        assertEquals(listOf(2, 1, 26), merged.map { it.type_id })
        assertEquals(3, AnnouncementHelper.countItems(merged))
    }

    @Test
    fun `hasContent标记按has_content解析`() {
        //has_content 是布尔值(服务端返回 true/false),不是 0/1
        assertTrue(item(1, hasContent = true).hasContent)
        assertTrue(!item(2, hasContent = false).hasContent)
    }
}
