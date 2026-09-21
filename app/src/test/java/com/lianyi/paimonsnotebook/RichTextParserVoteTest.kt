package com.lianyi.paimonsnotebook

import com.lianyi.paimonsnotebook.common.util.html.RichTextParser
import com.lianyi.paimonsnotebook.common.web.hoyolab.bbs.post.StructuredContentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/*
* 帖子结构化内容解析的回归测试
*
* 钉住的真实缺陷:含投票的帖子此前整块不可见 ——
* Insert.vote 虽在数据类里有字段,但 RichTextParser 不处理 "vote" key,
* 该块被标为 Error,渲染层的 Error 走 `else -> {}` 整块消失。
*
* 走 RichTextParser.parse(structuredContent) ——
* 与真机渲染走的同一条入口(PostStructureContentList -> remember -> parse)。
* */
class RichTextParserVoteTest {

    @Test
    fun 投票块被识别为Vote类型() {
        val result = RichTextParser.parse(
            structuredContent = """[{"insert":{"vote":{"id":"12345","uid":"67890"}}}]"""
        )

        assertEquals(
            "vote key 必须映射到 StructuredContentType.Vote,而不是 Error",
            StructuredContentType.Vote,
            result.first().type
        )
    }

    @Test
    fun 投票的id与uid被正确解析() {
        val result = RichTextParser.parse(
            structuredContent = """[{"insert":{"vote":{"id":"987","uid":"654"}}}]"""
        )

        val vote = result.first().insert.vote

        assertEquals("987", vote?.id)
        assertEquals("654", vote?.uid)
    }

    @Test
    fun 投票块与文本块共存时各自保持类型() {
        val result = RichTextParser.parse(
            structuredContent = """
            [
             {"insert":"投票前文字\n"},
             {"insert":{"vote":{"id":"1","uid":"2"}}},
             {"insert":"\n投票后文字"}
            ]
            """.trimIndent()
        )

        assertTrue(
            "应同时包含文本与投票两种类型",
            result.any { it.type == StructuredContentType.Text } &&
                    result.any { it.type == StructuredContentType.Vote }
        )
    }

    @Test
    fun 不含投票的普通内容不受影响() {
        val result = RichTextParser.parse(
            structuredContent = """[{"insert":"普通文本"},{"insert":{"image":"https://x/a.png"}}]"""
        )

        assertTrue(result.any { it.type == StructuredContentType.Text })
        assertTrue(result.any { it.type == StructuredContentType.Image })
    }

    @Test
    fun 未知类型仍降级为Error而非抛异常() {
        val result = RichTextParser.parse(
            structuredContent = """[{"insert":{"未知类型":{"a":1}}}]"""
        )

        assertEquals(
            "未知 key 仍应走 Error 降级(防崩溃),投票是显式新增",
            StructuredContentType.Error,
            result.first().type
        )
    }
}
