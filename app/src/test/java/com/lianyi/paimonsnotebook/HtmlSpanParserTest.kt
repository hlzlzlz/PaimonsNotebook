package com.lianyi.paimonsnotebook

import com.lianyi.paimonsnotebook.common.util.html.HtmlSpanParser
import com.lianyi.paimonsnotebook.common.util.html.HtmlSpanType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/*
* 公告正文 HTML 渲染的回归测试
*
* 背景(2026-09-20 用户反馈"游戏公告很多东西显示不出来,比如图片和表格"):
*
* 渲染器 HtmlTextLazyColumn 原先只处理顶层 <p> 与 <div>,其余标签一律走
* else 分支丢弃。用 **34 条真实公告正文** 实测,顶层标签分布是:
*   p 603 / details 50 / ol 18 / h4 8 / div 6 / h2 4
* 即 <details>(50 个,内含 9,489 字符正文)、<ol>(18)、<h2>/<h4>(12)
* **全部被丢弃**;而 25 个 <img> **全部位于 <p> 内**,走 HtmlCompat 后
* ImageSpan 不被复制,图片也全丢;6 个表格因为在 <div> 里被当成图片,
* 取不到 src 就回退到一张写死的占位图。
*
* 本文件用**真实公告 HTML 字节**做夹具(不是自造数据),确保这几类内容
* 都能被解析出来 —— 自造数据无法覆盖"服务端实际用了哪些标签"这个关键事实。
* */
class HtmlSpanParserTest {

    private fun fixture(name: String): File {
        val testDir = sequenceOf(
            File("src/test/java/com/lianyi/paimonsnotebook"),
            File("app/src/test/java/com/lianyi/paimonsnotebook")
        ).firstOrNull { it.isDirectory }
            ?: File(javaClass.protectionDomain.codeSource.location.toURI()).parentFile

        return File(testDir, name)
    }

    private fun parseFixture(name: String): List<com.lianyi.paimonsnotebook.common.data.html.HtmlSpanData> {
        val file = fixture(name)
        assumeTrue("缺少真实公告夹具: ${file.absolutePath}", file.exists())

        return HtmlSpanParser.parse(file.readText())
    }

    /*
    * 核心:真实公告里的图片必须被解析成 Img 项
    *
    * 夹具是真实公告 ann_id=21922,正文形如:
    *   <p style="..."><img src="https://sdk-webstatic.mihoyo.com/...jpg" ...></p>
    *
    * 修复前:走 <p> 分支 -> HtmlCompat -> ImageSpan 不被复制 => 图片丢失。
    * */
    @Test
    fun `p内的图片能被解析出来`() {
        val list = parseFixture("AnnContent_img.html")

        val images = list.filter { it.type == HtmlSpanType.Img }

        assertEquals("应解析出 1 张图片", 1, images.size)
        assertTrue(
            "图片地址应是真实 http 地址,实际=${images.first().data}",
            images.first().data.startsWith("https://") || images.first().data.startsWith("http://")
        )
        assertTrue(
            "不应再回退到写死的默认占位图",
            !images.first().data.contains("417976a3dacde790f947f8769d85d55c")
        )
    }

    /*
    * 核心:真实表格必须解析成 Table 且带行列数据
    *
    * 夹具是真实公告 ann_id=21805,含一个 3 列表格(祈愿时间/5星角色/4星角色),
    * 且用了 rowspan=3 合并单元格。
    *
    * 修复前:表格在 <div class="table-wrapper"> 里被当成图片 => 显示占位图。
    * */
    @Test
    fun `表格能被解析成行列结构`() {
        val list = parseFixture("AnnContent_table.html")

        val tables = list.filter { it.type == HtmlSpanType.Table }

        assertEquals("应解析出 1 个表格", 1, tables.size)

        val table = tables.first().tableData
        assertTrue("表格应有行列数据", table != null)

        val rows = table!!.rows
        assertTrue("表格行数应 >= 4(实测 4 行),实际=${rows.size}", rows.size >= 4)
        assertEquals("列数应为 3(实测),实际=${table.columnCount}", 3, table.columnCount)

        //表头文字
        val headerTexts = rows.first().map { it.text }
        assertTrue(
            "第一行应含表头'祈愿时间',实际=$headerTexts",
            headerTexts.any { it.contains("祈愿时间") }
        )

        //rowspan 必须被解析(实测"祈愿时间"单元格 rowspan=3)
        val hasRowSpan = rows.any { row -> row.any { it.rowSpan > 1 } }
        assertTrue("应解析出 rowSpan > 1 的合并单元格", hasRowSpan)
    }

    /*
    * rowspan 的落位计算:被跨行占住的格子不能挤掉后续单元格
    *
    * 这是表格渲染最容易错的地方 —— 若不算占位,带 rowspan 的行会整体左移。
    * */
    @Test
    fun `rowspan落位计算正确`() {
        val list = parseFixture("AnnContent_table.html")
        val table = list.first { it.type == HtmlSpanType.Table }.tableData!!

        val placed = table.layoutCells()

        //第 2 行(索引1)的第 1 列应是 rowspan=3 的"祈愿时间"
        val row1col0 = placed.firstOrNull { it.row == 1 && it.column == 0 }
        assertTrue("第2行第1列应存在", row1col0 != null)
        assertTrue("该格应是 rowspan>1", row1col0!!.cell.rowSpan > 1)

        /*
        * 第 3 行(索引2)按源 HTML 只有 1 个 td(伊安珊),
        * 但因为第 1、2 列被 rowspan 占住,它应落在 column=2。
        * */
        val row2cells = placed.filter { it.row == 2 }
        assertTrue("第3行应有单元格", row2cells.isNotEmpty())
        assertTrue(
            "第3行的单元格应落在第3列(column=2),实际=${row2cells.map { it.column }}",
            row2cells.any { it.column == 2 }
        )
    }

    /*
    * 核心:真实 <details> 内容必须被解析出来
    *
    * 夹具是真实公告 ann_id=21819(7.0 版本更新),含 50 个 details、
    * 约 9,489 字符正文。修复前整块丢弃 => 用户看到大片空白。
    * */
    @Test
    fun `details折叠块内容被解析出来`() {
        val list = parseFixture("AnnContent_details.html")

        val folds = list.filter { it.type == HtmlSpanType.Fold }

        assertTrue("应解析出多个折叠块(实测 50 个),实际=${folds.size}", folds.size >= 10)

        //标题与内容都不能为空
        val withTitle = folds.count { it.titleList.any { t -> t.text.isNotBlank() } }
        val withContent = folds.count { it.textList.any { t -> t.text.isNotBlank() } }

        assertTrue("多数折叠块应有标题,实际=$withTitle", withTitle >= 5)
        assertTrue("多数折叠块应有正文内容,实际=$withContent", withContent >= 5)
    }

    /*
    * 核心:整篇 details 公告的正文文本量必须保住
    *
    * 这条是"内容有没有丢"的总量断言:修复前 details 全部丢弃,
    * 提取到的文本量会接近 0。
    * */
    @Test
    fun `details公告的正文文本不丢失`() {
        val list = parseFixture("AnnContent_details.html")

        val totalText = list.sumOf { span ->
            span.textList.sumOf { it.text.length } +
                span.listItems.sumOf { it.length } +
                span.titleList.sumOf { it.text.length }
        }

        assertTrue(
            "正文文本总量应 >= 3000 字符(修复前 details 丢弃会接近 0),实际=$totalText",
            totalText >= 3000
        )
    }

    /*
    * 列表 <ol>/<li> 必须解析出来并带序号
    * */
    @Test
    fun `有序列表被解析并带序号`() {
        val list = parseFixture("AnnContent_details.html")

        val lists = list.filter { it.type == HtmlSpanType.List }

        assertTrue("应解析出列表,实际=${lists.size}", lists.isNotEmpty())

        val items = lists.flatMap { it.listItems }
        assertTrue("列表项不应为空", items.isNotEmpty())
        assertTrue(
            "有序列表项应带 '1. ' 这类序号前缀,实际前3项=${items.take(3)}",
            items.any { it.trimStart().startsWith("1.") }
        )
    }

    /*
    * 标题 h2/h4 必须解析出来
    *
    * ⚠️ 用的是**专门含标题的那条公告**的夹具(ann_id=21456,实测含 h2 与 h4)。
    * 起初我把这条断言写在 details 夹具上,结果拿到 0 个标题 ——
    * 因为 7.0 更新公告里其实没有 h2/h4(它的顶层只有 p/details/ol)。
    * 这说明"某个标签在整体统计里出现"不等于"它在你手上这条样本里出现",
    * 夹具必须按要验证的标签来挑。
    * */
    @Test
    fun `标题标签被解析出来`() {
        val list = parseFixture("AnnContent_heading.html")

        val headings = list.filter { it.type == HtmlSpanType.Heading }

        assertTrue("应解析出标题(该公告实测含 h2/h4),实际=${headings.size}", headings.size >= 2)
        assertTrue(
            "标题级别应在 1..6,实际=${headings.map { it.headingLevel }.distinct()}",
            headings.all { it.headingLevel in 1..6 }
        )
        assertTrue(
            "标题文本不应为空",
            headings.any { it.textList.any { t -> t.text.isNotBlank() } }
        )
    }

    /*
    * 被转义的 &lt;t&gt; 时间标签必须折叠成纯文本
    *
    * ⚠️ 服务端下发的是**转义形式**
    *   &lt;t class="t_lc" contenteditable="false"&gt;2026/09/01 18:00&lt;/t&gt;
    * (34 条公告里 114 处),而旧清洗正则匹配字面 `<t\b`,**根本匹配不到**,
    * 于是用户看到一串尖括号。
    *
    * ⚠️⚠️ 本条用例曾经**只检查 span.textList[].text,漏掉了真机路径**,
    * 这正是它当时没能拦住"真机显示尖括号"的原因(见下一条用例)。
    * */
    @Test
    fun `转义的时间标签被折叠为纯文本`() {
        val list = parseFixture("AnnContent_table.html")

        val allText = list.flatMap { span ->
            span.textList.map { it.text } +
                span.listItems +
                span.titleList.map { it.text } +
                (span.tableData?.rows?.flatten()?.map { it.text } ?: emptyList())
        }

        //不应残留转义后的尖括号
        assertTrue(
            "不应残留 &lt;t 转义标签,实际文本片段=${allText.filter { it.contains("&lt;t") }.take(2)}",
            allText.none { it.contains("&lt;t") }
        )
        assertTrue(
            "不应残留字面 <t 标签,实际=${allText.filter { it.contains("<t ") }.take(2)}",
            allText.none { it.contains("<t ") }
        )

        //但时间内容要保留(该表格里含 2026/09/01 这类时间)
        assertTrue(
            "时间文本应被保留,实际片段=${allText.filter { it.contains("2026/") }.take(2)}",
            allText.any { it.contains("2026/") }
        )
    }

    /*
    * ⚠️ 真机路径必须也干净 —— 这是 1.8.13 漏掉的缺陷
    *
    * 用户真机截图(2026-09-21)显示 7.1 版本更新维护预告里
    * `<t class="t_gl" contenteditable="false">2026/09/23 06:00</t>`
    * **原样显示成了一串尖括号**,而 1.8.13 自称修好了这个问题。
    *
    * 根因(已实测确认):HtmlCompat.fromHtml 是 Android 平台 API,
    *   - 真机:调用成功 -> 走 SP 分支 -> 该分支**从未折叠** -> 显示尖括号
    *   - JVM:调用抛异常 -> 走降级分支 -> 该分支当时**有**折叠 -> 测试假绿
    * 单测与真机走的是**不同分支**,所以 12 个用例全绿也没发现。
    *
    * 修复方式:把折叠提到 parse() 入口(collapseEscapedTimeTags)。
    *
    * ⚠️ 为什么断言"降级分支的输出"就能证明真机分支也干净(非循环论证):
    *   两条分支都从 parse() 里**同一个** `cleaned` 变量派生 ——
    *   Jsoup 解析的是 `cleaned`,而真机分支拿的 `parent.html()`
    *   正是这个 Jsoup 文档的产物。所以只要输出里没有转义标签,
    *   就说明**入口**确实洗过(因为降级分支自己已经不再折叠了,
    *   见 parseParagraph 末尾的说明),入口洗过 => 两条分支都干净。
    *
    *   ⚠️ 我最初写了个 `deviceBranchInput()` 直接调 collapseEscapedTimeTags
    *   来"模拟真机输入",那是**循环论证**:helper 自己调了折叠函数,
    *   无论 parse() 有没有用它都会通过(实测确认:把入口改回不折叠时,
    *   那条用例照样绿)。已删除,改用下面这种从 parse() 输出反推的写法。
    * */
    @Test
    fun `真机分支收到的HTML不含转义时间标签`() {
        val html = """<p style="white-space: pre-wrap;">制作组预计将于&lt;t class="t_gl" contenteditable="false"&gt;2026/09/23 06:00&lt;/t&gt;进行版本更新维护。</p>"""

        val list = HtmlSpanParser.parse(html)

        /*
        * 在 JVM 里 parse() 必然走降级分支(SP 分支需要 Android 的
        * HtmlCompat),所以这里拿到的是降级分支的输出。
        * 按上面的推理,它干净 <=> 入口洗过 <=> 真机分支也干净。
        * */
        val fallbackText = list.flatMap { it.textList.map { t -> t.text } }.joinToString("")

        assertTrue("应显示时间,实际=$fallbackText", fallbackText.contains("2026/09/23 06:00"))
        assertTrue(
            "入口未清洗转义标签(真机也会因此显示尖括号),实际=$fallbackText",
            !fallbackText.contains("&lt;t") &&
                !fallbackText.contains("contenteditable") &&
                !fallbackText.contains("&lt;/t")
        )
    }

    /*
    * 用真实 7.1 公告(含 4 处转义时间标签)验证
    *
    * 夹具 AnnContent_time71.html 是用户截图那条公告(ann_id=21928)的原始正文字节。
    * 这条公告是用户真机反馈的直接来源,必须锁住。
    *
    * ⚠️ 这是本次**唯一能拦住该缺陷**的用例:把 parse() 入口改回
    * "不折叠"(即 1.8.13 的写法)后,它立刻失败(已实测)。
    * */
    @Test
    fun `真实7_1公告不再显示尖括号`() {
        val file = fixture("AnnContent_time71.html")
        assumeTrue("缺少夹具: ${file.absolutePath}", file.exists())

        val raw = file.readText()
        assertTrue(
            "夹具本身应含转义标签(否则这条用例没有意义)",
            raw.contains("&lt;t")
        )

        //parse() 的输出(真机分支与降级分支同源,见上一条用例的推理)
        val shown = HtmlSpanParser.parse(raw)
            .flatMap { it.textList.map { t -> t.text } + it.listItems + it.titleList.map { t -> t.text } }
            .joinToString("")

        assertTrue(
            "不应残留转义标签,实际片段=${shown.take(200)}",
            !shown.contains("&lt;t") && !shown.contains("contenteditable")
        )
        assertTrue("应保留时间文本,实际=${shown.take(200)}", shown.contains("2026/09/23"))
    }

    /*
    * 未知标签必须降级渲染(提取文本/图片),而不是静默丢弃
    *
    * 这是防回归的兜底:以后公告再引入新标签(如 <figure>/<section>),
    * 至少要把文字和图片显示出来,而不是整块空白。
    * */
    @Test
    fun `未知标签降级提取文本与图片`() {
        val html = """<section><p>段落文字</p><img src="https://example.com/x.png"></section>"""

        val list = HtmlSpanParser.parse(html)

        val hasImage = list.any { it.type == HtmlSpanType.Img && it.data.contains("x.png") }
        val hasText = list.any { span ->
            span.textList.any { it.text.contains("段落文字") }
        }

        assertTrue("未知容器内的图片应被提取", hasImage)
        assertTrue("未知容器内的文字应被提取", hasText)
    }

    /*
    * 图文混排时文字不能丢
    *
    * 实测公告的 25 张图都是 <p><img></p> 纯图,但混排是合法写法。
    * 这条用例锁定:图片与文字都要保留 —— 若实现把两者塞进同一个
    * Img 项,渲染层不读 Img 的 textList,文字就会静默消失。
    * */
    @Test
    fun `图文混排时文字与图片都保留`() {
        val html = """<p>前面的说明文字<img src="https://example.com/pic.png">后面的文字</p>"""

        val list = HtmlSpanParser.parse(html)

        assertTrue(
            "应解析出图片",
            list.any { it.type == HtmlSpanType.Img && it.data.contains("pic.png") }
        )

        val text = list.flatMap { it.textList }.joinToString("") { it.text }
        assertTrue("图片前的文字应保留,实际=$text", text.contains("前面的说明文字"))
        assertTrue("图片后的文字应保留,实际=$text", text.contains("后面的文字"))
    }

    /*
    * 空/纯文本输入不应抛异常
    * */
    @Test
    fun `空与纯文本输入不抛异常`() {
        assertTrue("空串应返回空列表", HtmlSpanParser.parse("").isEmpty())
        assertTrue("空白串应返回空列表", HtmlSpanParser.parse("   ").isEmpty())

        val plain = HtmlSpanParser.parse("这是一段没有任何标签的纯文本")
        assertTrue("纯文本应被保留", plain.any { span -> span.textList.any { it.text.contains("纯文本") } })
    }

    /*
    * 颜色解析必须容错
    *
    * 原实现对颜色单词走 toColorInt 并在失败时弹提示;公告里出现了
    * rgb(...) 与 #rrggbb 两种写法,不能因为一个颜色异常影响整段渲染。
    * */
    @Test
    fun `异常颜色不影响解析`() {
        val html = """<p style="color: notacolor;">文字</p>"""

        val list = HtmlSpanParser.parse(html)

        assertTrue("异常颜色不应导致解析失败", list.isNotEmpty())
        assertTrue(
            "文字仍应被保留",
            list.any { span -> span.textList.any { it.text.contains("文字") } }
        )
    }
}
