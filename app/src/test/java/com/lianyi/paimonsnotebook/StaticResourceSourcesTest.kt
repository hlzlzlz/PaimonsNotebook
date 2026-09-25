package com.lianyi.paimonsnotebook

import com.lianyi.paimonsnotebook.common.web.static_resources.StaticResourceSources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/*
* 静态资源多源候选
*
* 本文件要钉住的是"**兜底到底会不会被用到**"这个前提 ——
* 原实现的兜底因为不捕获超时异常而**从未生效过**,所以候选列表的正确性
* 是本次修复能起作用的前提。
* */
class StaticResourceSourcesTest {

    private val primary =
        "https://static.snaphutaorp.org/static/raw/AvatarIcon/UI_AvatarIcon_Qin.png"

    @Test
    fun `主图床地址被识别为静态资源`() {
        assertTrue(StaticResourceSources.isStaticResource(primary))
    }

    @Test
    fun `非静态资源地址不被识别`() {
        assertFalse(
            StaticResourceSources.isStaticResource("https://bbs-api.miyoushe.com/x.png")
        )
        assertFalse(
            StaticResourceSources.isStaticResource("https://enka.network/ui/UI_AvatarIcon_Qin.png")
        )
    }

    /*
    * 🔴 核心用例:候选里必须**包含镜像**,且顺序是 主 -> 镜像 -> enka。
    *
    * 镜像与主图床路径结构一致,因此能覆盖全部分类;enka 只能覆盖 UI_ 那批,
    * 放最后。若实现把 enka 提前,分类缺失的图(MonsterIcon)会先走一个
    * 必然失败的源,白白增加一次等待。
    * */
    @Test
    fun `候选顺序为主图床再镜像最后enka`() {
        val candidates = StaticResourceSources.candidateUrls(primary)

        assertEquals(3, candidates.size)
        assertEquals(primary, candidates[0])
        assertTrue("第2个应为镜像", candidates[1].contains("static.hutaorp.org"))
        assertTrue("第3个应为 enka", candidates[2].startsWith("https://enka.network/ui/"))
    }

    @Test
    fun `镜像保留原路径结构`() {
        val candidates = StaticResourceSources.candidateUrls(primary)

        assertTrue(candidates[1].endsWith("/static/raw/AvatarIcon/UI_AvatarIcon_Qin.png"))
    }

    /*
    * enka 不提供 MonsterIcon / LoadingPic —— 这两类不该把它列为候选
    * (原代码注释这么说,但实现里没有任何过滤,注释与实现不符)。
    * */
    @Test
    fun `enka不提供的分类不列入候选`() {
        val monster = "https://static.snaphutaorp.org/static/raw/MonsterIcon/UI_MonsterIcon_X.png"
        val candidates = StaticResourceSources.candidateUrls(monster)

        assertTrue(
            "MonsterIcon 不应包含 enka 候选: $candidates",
            candidates.none { it.contains("enka.network") }
        )
        //但仍应有镜像候选
        assertTrue(candidates.any { it.contains("static.hutaorp.org") })
    }

    @Test
    fun `LoadingPic分类同样不列enka`() {
        val loading = "https://static.snaphutaorp.org/static/raw/LoadingPic/UI_Loading_Qin.png"
        val candidates = StaticResourceSources.candidateUrls(loading)

        assertTrue(candidates.none { it.contains("enka.network") })
    }

    @Test
    fun `AvatarIcon分类支持enka`() {
        assertTrue(StaticResourceSources.enkaSupportsCategory("AvatarIcon"))
        assertFalse(StaticResourceSources.enkaSupportsCategory("MonsterIcon"))
        assertFalse(StaticResourceSources.enkaSupportsCategory("LoadingPic"))
    }

    @Test
    fun `分类名从路径中正确取出`() {
        assertEquals("AvatarIcon", StaticResourceSources.categoryOf(primary))
        assertEquals(
            "EquipIcon",
            StaticResourceSources.categoryOf(
                "https://static.snaphutaorp.org/static/raw/EquipIcon/UI_EquipIcon_Sword.png"
            )
        )
    }

    /*
    * 非静态资源地址原样返回单元素 —— 不去改写别人的 URL。
    * */
    @Test
    fun `非静态资源原样返回`() {
        val other = "https://bbs-api.miyoushe.com/avatar.png"
        val candidates = StaticResourceSources.candidateUrls(other)

        assertEquals(1, candidates.size)
        assertEquals(other, candidates[0])
    }

    /*
    * 替换 host 时不能碰 scheme、端口、查询串与路径。
    * (字符串拼接替换 host 极易写错,故单独钉住。)
    * */
    @Test
    fun `替换host保留scheme路径与查询串`() {
        val withQuery =
            "https://static.snaphutaorp.org/static/raw/AvatarIcon/x.png?v=1&a=2"
        val mirror = StaticResourceSources.candidateUrls(withQuery)[1]

        assertTrue(mirror.startsWith("https://static.hutaorp.org/"))
        //scheme 只能出现一次(拼接式替换极易写成 https://https://...)
        assertEquals(
            "scheme 应恰好出现一次",
            1,
            Regex("https://").findAll(mirror).count()
        )
        assertFalse("不应残留原 host", mirror.contains("snaphutaorp.org"))
        assertTrue("查询串应保留", mirror.endsWith("?v=1&a=2"))
        assertTrue("路径应保留", mirror.contains("/static/raw/AvatarIcon/x.png"))
    }
}
