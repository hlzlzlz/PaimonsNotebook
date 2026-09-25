package com.lianyi.paimonsnotebook

import com.lianyi.paimonsnotebook.common.web.static_resources.StaticResourceSources
import org.junit.Assert.assertEquals
import org.junit.Test

/*
* 内置缩略图的**查表契约**
*
* BuiltInThumbnails 本身依赖 Android 的 AssetManager 与 ZipFile,
* 无法在纯 JVM 单测里跑;但它查表用的**键**完全由这两个纯函数决定:
*   1. StaticResourceSources.categoryOf(url)   -> 分类(即 zip 内的一级目录)
*   2. url 的最后一个路径段去掉扩展名           -> 文件名(stem)
* 生成脚本 `_thumbs/build_icon_thumbs.py` 写的条目名必须与之**逐字一致**,
* 否则表现为"内置了但一张都命中不了"(页面依旧等网络),且不会有任何报错。
*
* 故本文件钉住"URL -> 条目名"的换算规则。若哪天改了 URL 结构或
* 生成脚本的目录约定,这里必须同步失败。
* */
class BuiltInThumbnailKeyTest {

    private fun entryNameFor(url: String): String {
        val category = StaticResourceSources.categoryOf(url)
        val fileName = url.substringAfterLast('/').substringBefore('?')
        val stem = fileName.substringBeforeLast('.')
        return "$category/$stem.webp"
    }

    @Test
    fun `角色图标 URL 换算为 AvatarIcon 目录`() {
        val url = "https://static.snaphutaorp.org/static/raw/AvatarIcon/UI_AvatarIcon_Qin.png"
        assertEquals("AvatarIcon/UI_AvatarIcon_Qin.webp", entryNameFor(url))
    }

    @Test
    fun `怪物图标 URL 换算为 MonsterIcon 目录`() {
        val url = "https://static.snaphutaorp.org/static/raw/MonsterIcon/UI_MonsterIcon_Slime_01.png"
        assertEquals("MonsterIcon/UI_MonsterIcon_Slime_01.webp", entryNameFor(url))
    }

    @Test
    fun `武器图标 URL 换算为 EquipIcon 目录`() {
        val url = "https://static.snaphutaorp.org/static/raw/EquipIcon/UI_EquipIcon_Sword_Aether.png"
        assertEquals("EquipIcon/UI_EquipIcon_Sword_Aether.webp", entryNameFor(url))
    }

    @Test
    fun `圣遗物图标 URL 换算为 RelicIcon 目录`() {
        val url = "https://static.snaphutaorp.org/static/raw/RelicIcon/UI_RelicIcon_15001_4.png"
        assertEquals("RelicIcon/UI_RelicIcon_15001_4.webp", entryNameFor(url))
    }

    /*
    * 物品图标:material 元数据里引用 UI_RelicIcon_* 时,
    * ItemIconConverter 会**转交** RelicIcon 分类(见该 converter 的实现)。
    * 生成脚本已按同样规则把这些图放进 RelicIcon/ 目录 ——
    * 本用例保证"URL 换算"与之一致(URL 里的分类本来就是 RelicIcon)。
    * */
    @Test
    fun `转交到圣遗物的物品图标落在 RelicIcon 目录`() {
        val url = "https://static.snaphutaorp.org/static/raw/RelicIcon/UI_RelicIcon_15004_4.png"
        assertEquals("RelicIcon/UI_RelicIcon_15004_4.webp", entryNameFor(url))
    }

    @Test
    fun `物品图标 URL 换算为 ItemIcon 目录`() {
        val url = "https://static.snaphutaorp.org/static/raw/ItemIcon/UI_ItemIcon_100004.png"
        assertEquals("ItemIcon/UI_ItemIcon_100004.webp", entryNameFor(url))
    }

    /*
    * 带查询串的 URL 必须能正确取到 stem(否则条目名会变成
    * "UI_x.png?v=1.webp" 这种不存在的键)。
    * */
    @Test
    fun `带查询串的 URL 仍能正确换算`() {
        val url = "https://static.snaphutaorp.org/static/raw/AvatarIcon/UI_AvatarIcon_Qin.png?v=2"
        assertEquals("AvatarIcon/UI_AvatarIcon_Qin.webp", entryNameFor(url))
    }

    /*
    * 非静态资源(用户帖子外链的图等)不在内置包内 ——
    * categoryOf 取的是"文件名前的那一段",对外链而言是个无意义的目录名
    * (实测某帖图 URL 取到数字目录)。这里钉住"**绝不会**落进图标分类目录",
    * 即查表必然落空并回退网络,不会串到别的图标上。
    * */
    @Test
    fun `外链图片不会命中内置缩略图`() {
        val bbs = "https://upload-bbs.miyoushe.com/upload/2026/09/04/76387920/abc.png"
        val name = entryNameFor(bbs)

        // 关键断言:不落在任何内置分类目录下
        val iconCategories = listOf(
            "AvatarIcon", "MonsterIcon", "EquipIcon", "RelicIcon", "ItemIcon"
        )
        org.junit.Assert.assertFalse(
            "外链不应落在内置图标分类目录下,实际: $name",
            iconCategories.any { name.startsWith("$it/") }
        )

        // 另一条:BBS 图与游戏图标不同域,这一点 StaticResourceSources 已保证
        org.junit.Assert.assertFalse(
            StaticResourceSources.isStaticResource(bbs)
        )
    }
}
