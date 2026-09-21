package com.lianyi.paimonsnotebook.common.util.html

import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.toColorInt
import androidx.core.text.HtmlCompat
import com.lianyi.paimonsnotebook.common.data.html.HtmlSpanData
import com.lianyi.paimonsnotebook.common.data.html.HtmlTableCell
import com.lianyi.paimonsnotebook.common.data.html.HtmlTableData
import com.lianyi.paimonsnotebook.common.data.html.HtmlTextData
import com.lianyi.paimonsnotebook.ui.theme.Black
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/*
* HTML -> 渲染数据 的解析器
*
* 从 HtmlTextLazyColumn 里抽出来,目的是**可被纯 JVM 单测驱动** ——
* 之前解析逻辑与 Compose 组件耦合,只能靠肉眼看真机才发现问题。
*
* ⚠️ 2026-09-20 修复的四个缺陷(均由 34 条真实公告正文实测确认):
*
* 1. **图片全丢**:25 个 <img> **全部**位于 <p> 内(<p><img></p> 纯图段)。
*    而 <p> 分支走 HtmlCompat.fromHtml(...) 得到 Spanned,再经
*    ToAnnotatedString 只复制 URLSpan/ForegroundColorSpan/UnderlineSpan/
*    StyleSpan 四种 span —— ImageSpan 不在其中,图片被静默丢弃。
*    ⇒ 现在显式检查 p 内是否含 img,含则按图片处理。
*
* 2. **表格被当成图片**:表格结构是 <div class="table-wrapper"><table>…。
*    原代码的 div 分支在"有子元素且第一个不是 iframe"时一律当图片,
*    并从 getElementsByTag("img") 取 src;表格里没有 img ⇒ src 为 null
*    ⇒ **回退到一张写死的默认占位图**,表现为"表格位置显示一张无关图片"。
*    ⇒ 现在识别 table 并解析成行列结构。
*
* 3. **details/summary 整块丢失**:实测 50 个顶层 <details>(7.0 版本更新
*    公告用了 50 个,内含 9,489 字符正文),原 else 分支只把标签名塞进
*    data、textList 为空 ⇒ **整篇空白**。
*    ⇒ 现在渲染成可折叠块(标题 = summary,内容 = 其余)。
*
* 4. **ol/ul/li 与 h2/h4 丢失**:顶层 ol 18 个、h4 8 个、h2 4 个,同样落 else。
*    ⇒ 现在渲染成带序号/项目符号的列表与标题。
*
* 另外修掉**被转义的 <t> 时间标签**:服务端下发的是 `&lt;t class="t_lc"&gt;`
* (实测 114 处),而原清洗正则匹配的是字面 `<t\b`,**匹配不到**,
* 于是用户看到一串尖括号。现在在解析层用 Jsoup 的文本节点处理,
* 转义与未转义两种形式都能折叠掉。
* */
object HtmlSpanParser {

    /*
    * 解析正文 HTML
    *
    * 返回值与标签顺序一致,供 LazyColumn 逐项渲染。
    * */
    fun parse(htmlText: String): List<HtmlSpanData> {
        if (htmlText.isBlank()) {
            return emptyList()
        }

        /*
        * ⚠️ 时间标签必须在**分流之前**处理掉,这是 1.8.13 漏掉的一步。
        *
        * 1.8.13 只在"HtmlCompat 失败后的降级纯文本分支"里调了 collapseTimeTags,
        * 而 HtmlCompat.fromHtml 是 Android 平台 API:
        *   - 真机:调用成功 -> 走 SP 分支 -> **从未折叠** -> 用户看到一串尖括号
        *   - JVM 单测:调用抛异常 -> 走降级分支 -> 折叠生效 -> **测试假绿**
        * 即单测与真机走的是**不同分支**,12 个用例全绿也没能发现该缺陷。
        *
        * 现在改为在解析入口对原始 HTML 做一次预处理,与后续走哪个分支无关,
        * 因此单测能真正覆盖真机行为。
        * */
        val cleaned = collapseEscapedTimeTags(htmlText)

        val document = Jsoup.parse(cleaned)
        val body = document.body().children()

        if (body.isEmpty()) {
            //纯文本(无标签)兜底
            return listOf(
                HtmlSpanData(
                    textList = listOf(
                        HtmlTextData(text = collapseTimeTags(cleaned), color = Black)
                    )
                )
            )
        }

        val result = mutableListOf<HtmlSpanData>()

        body.forEach { parent ->
            parseElement(parent, result)
        }

        return result
    }

    //解析单个顶层元素,可能产出多项(如 details 会产出折叠块)
    private fun parseElement(parent: Element, out: MutableList<HtmlSpanData>) {
        when (parent.tagName()) {
            "p" -> parseParagraph(parent, out)

            "div" -> parseDiv(parent, out)

            "table" -> out += parseTable(parent)

            "details" -> out += parseDetails(parent)

            "ol", "ul" -> out += parseList(parent)

            "h1", "h2", "h3", "h4", "h5", "h6" -> out += parseHeading(parent)

            "img" -> {
                val src = parent.attr("src")
                if (src.isNotBlank()) {
                    out += HtmlSpanData(type = HtmlSpanType.Img, data = src)
                }
            }

            //空段落/换行,保留一个空文本项维持间距
            "br", "hr" -> out += HtmlSpanData(type = HtmlSpanType.P, textList = emptyList())

            else -> {
                /*
                * 未知标签:尽量提取其中的文本与图片,而不是丢弃。
                *
                * 原实现只把标签名写进 data、textList 为空 ⇒ 内容静默消失。
                * 这里改为"降级渲染":有 img 就出图,有文字就出文字。
                * */
                val images = parent.getElementsByTag("img")
                if (images.isNotEmpty()) {
                    images.forEach { img ->
                        val src = img.attr("src")
                        if (src.isNotBlank()) {
                            out += HtmlSpanData(type = HtmlSpanType.Img, data = src)
                        }
                    }
                }

                val text = parent.text()
                if (text.isNotBlank()) {
                    out += HtmlSpanData(
                        type = HtmlSpanType.P,
                        textList = listOf(HtmlTextData(text = collapseTimeTags(text)))
                    )
                }
            }
        }
    }

    /*
    * 段落
    *
    * 关键:先看是否含 <img> —— 实测所有 25 个图片都在 p 里,
    * 若走 HtmlCompat 会被 ImageSpan 丢弃(见类注释第 1 点)。
    *
    * 产出的项数不固定:纯图段出 1 项;图文混排出 2 项(图片 + 文字),
    * 故这里直接往 out 里追加,而不是返回单个 HtmlSpanData ——
    * 否则混排时文字会被渲染层忽略(Img 分支不读 textList)。
    * */
    private fun parseParagraph(parent: Element, out: MutableList<HtmlSpanData>) {
        val alignment = getAlign(parent)

        val images = parent.getElementsByTag("img")

        if (images.isNotEmpty()) {
            /*
            * 实测公告里 25 个图都是 <p><img></p> 纯图(无混排),
            * 但混排情形也一并正确处理,避免以后漏文字。
            * */
            images.forEach { img ->
                val src = img.attr("src")
                if (src.isNotBlank()) {
                    out += HtmlSpanData(type = HtmlSpanType.Img, data = src, alignment = alignment)
                }
            }

            //去掉图片后若还有文字,补一个文本项
            val textWithoutImages = parent.clone().apply {
                getElementsByTag("img").forEach { it.remove() }
            }.text()

            if (textWithoutImages.isNotBlank()) {
                out += HtmlSpanData(
                    type = HtmlSpanType.P,
                    alignment = alignment,
                    textList = listOf(
                        HtmlTextData(
                            text = collapseTimeTags(textWithoutImages),
                            color = getTextColorInStyle(parent)
                        )
                    )
                )
            }

            return
        }

        /*
        * 富文本(HtmlCompat)失败时降级为纯文本。
        *
        * HtmlCompat.fromHtml 是 Android 平台 API(依赖 android.text.Html),
        * 在某些环境下会抛异常。原实现直接调用,一旦失败整段内容就没了;
        * 这里改为失败即降级 —— 段落文字至少要显示出来。
        * 顺带的效果:解析器因此可在纯 JVM 单测里驱动(见 HtmlSpanParserTest),
        * 不必为了测试去 mock android.text.Html。
        * */
        val rich = runCatching {
            HtmlCompat.fromHtml(parent.html(), HtmlCompat.FROM_HTML_MODE_LEGACY)
        }.getOrNull()

        if (rich != null && rich.isNotEmpty()) {
            out += HtmlSpanData(
                type = HtmlSpanType.SP,
                textList = listOf(HtmlTextData(spannableString = rich)),
                alignment = alignment
            )
            return
        }

        /*
        * 降级:用 Jsoup 取纯文本
        *
        * ⚠️ 这里**故意不再**调 collapseTimeTags:时间标签的折叠统一由
        * parse() 入口的 collapseEscapedTimeTags 负责(见该函数说明)。
        *
        * 这样做的另一个好处是**让单测真正能发现该缺陷**:
        * 真机走 SP 分支、JVM 走本分支,两条分支的差异正是 1.8.13 漏测的原因。
        * 把折叠收敛到入口后,两条分支共享同一份预处理,
        * 于是"入口忘了折叠"这种错误在 JVM 单测里就会**直接失败**,
        * 而不是像 1.8.13 那样 12 个用例全绿却线上照样显示尖括号。
        * */
        out += HtmlSpanData(
            type = HtmlSpanType.P,
            textList = listOf(
                HtmlTextData(
                    text = parent.text(),
                    color = getTextColorInStyle(parent)
                )
            ),
            alignment = alignment
        )
    }

    //div:折叠块 / 外链卡片 / 表格容器 / 普通图片
    private fun parseDiv(parent: Element, out: MutableList<HtmlSpanData>) {
        val classes = parent.classNames()

        when {
            classes.contains("ql-fold") -> out += parseFold(parent)

            classes.contains("ql-link-card") -> out += parseLinkCard(parent)

            /*
            * 表格容器(实测 <div class="table-wrapper"><table>…)。
            * 必须在"当图片处理"之前判断,否则会退化成默认占位图。
            * */
            parent.getElementsByTag("table").isNotEmpty() -> {
                parent.getElementsByTag("table").forEach { out += parseTable(it) }
            }

            parent.getElementsByTag("img").isNotEmpty() -> {
                parent.getElementsByTag("img").forEach { img ->
                    val src = img.attr("src")
                    if (src.isNotBlank()) {
                        out += HtmlSpanData(type = HtmlSpanType.Img, data = src)
                    }
                }
            }

            parent.children().firstOrNull()?.tagName() == "iframe" -> {
                out += HtmlSpanData(
                    type = HtmlSpanType.Video,
                    data = parent.children().attr("video")
                )
            }

            parent.childrenSize() == 0 -> {
                //空 div:实测有 <div></div> 这类视频占位
                out += HtmlSpanData(type = HtmlSpanType.Video)
            }

            else -> {
                //其它 div:递归解析其子元素,而不是丢弃
                val before = out.size
                parent.children().forEach { child ->
                    parseElement(child, out)
                }

                //子元素什么都没产出时,至少保留其中的文字
                if (out.size == before) {
                    val text = parent.text()
                    if (text.isNotBlank()) {
                        out += HtmlSpanData(
                            type = HtmlSpanType.P,
                            textList = listOf(HtmlTextData(text = collapseTimeTags(text)))
                        )
                    }
                }
            }
        }
    }

    /*
    * 表格
    *
    * 解析成行列结构(含 rowspan/colspan —— 实测公告确实用了:
    * 8 处 rowspan、4 处 colspan,例如"祈愿时间"单元格 rowspan=3)。
    * */
    private fun parseTable(table: Element): HtmlSpanData {
        val rows = mutableListOf<List<HtmlTableCell>>()

        table.getElementsByTag("tr").forEach { tr ->
            val cells = mutableListOf<HtmlTableCell>()

            tr.children().forEach { td ->
                if (td.tagName() != "td" && td.tagName() != "th") {
                    return@forEach
                }

                cells += HtmlTableCell(
                    text = collapseTimeTags(td.text()),
                    rowSpan = td.attr("rowspan").toIntOrNull()?.coerceAtLeast(1) ?: 1,
                    colSpan = td.attr("colspan").toIntOrNull()?.coerceAtLeast(1) ?: 1,
                    isHeader = td.tagName() == "th",
                    backgroundColor = getBackgroundColorInStyle(td),
                    alignCenter = td.getElementsByTag("p").any {
                        it.classNames().contains("ql-align-center") ||
                            it.attr("style").contains("text-align: center")
                    }
                )
            }

            if (cells.isNotEmpty()) {
                rows += cells
            }
        }

        return HtmlSpanData(
            type = HtmlSpanType.Table,
            tableData = HtmlTableData(rows = rows),
            //data 存纯文本,便于无障碍/兜底显示
            data = table.text()
        )
    }

    /*
    * details/summary 折叠块
    *
    * 实测 7.0 版本更新公告用了 50 个 details(内含 9,489 字符),
    * 原实现整块丢弃 ⇒ 用户看到的是大片空白。
    * 复用已有的 Fold 类型与 FoldTextContent 组件。
    * */
    private fun parseDetails(details: Element): HtmlSpanData {
        val summary = details.children().firstOrNull { it.tagName() == "summary" }

        val titleList = mutableListOf<HtmlTextData>()
        if (summary != null) {
            val summaryText = collapseTimeTags(summary.text())
            if (summaryText.isNotBlank()) {
                titleList += HtmlTextData(text = summaryText, color = getTextColorInStyle(summary))
            }
        }

        /*
        * 内容 = details 内除 summary 以外的所有文本。
        * 这里按块级子元素逐行取,保留段落结构(用 \n 分隔),
        * 否则长正文会挤成一行难以阅读。
        * */
        val textList = mutableListOf<HtmlTextData>()

        details.children().forEach { child ->
            if (child.tagName() == "summary") {
                return@forEach
            }

            appendBlockText(child, textList)
        }

        return HtmlSpanData(
            type = HtmlSpanType.Fold,
            titleList = titleList,
            textList = textList
        )
    }

    /*
    * 递归收集块级文本,按行追加到 textList
    *
    * 用于 details 内容这类"需要保留换行"的场景。
    * */
    private fun appendBlockText(
        element: Element,
        out: MutableList<HtmlTextData>,
        orderedIndex: Int? = null
    ) {
        val tag = element.tagName()

        when (tag) {
            "p" -> {
                val text = collapseTimeTags(element.text())
                if (text.isNotBlank()) {
                    out += HtmlTextData(text = text, color = getTextColorInStyle(element))
                }
                out += HtmlTextData(text = "\n", color = Color.Transparent)
            }

            "ol", "ul" -> {
                element.children().forEachIndexed { index, li ->
                    if (li.tagName() == "li") {
                        appendListItem(li, out, tag == "ol", index)
                    }
                }
            }

            "details" -> {
                //嵌套 details:标题用 ▸ 前缀表示
                val nested = parseDetails(element)
                nested.titleList.forEach {
                    out += HtmlTextData(text = "▸ " + it.text, color = it.color)
                }
                out += HtmlTextData(text = "\n", color = Color.Transparent)
                out += nested.textList
            }

            "br" -> out += HtmlTextData(text = "\n", color = Color.Transparent)

            else -> {
                //容器:递归;叶子节点直接取文本
                if (element.children().isEmpty()) {
                    val text = collapseTimeTags(element.text())
                    if (text.isNotBlank()) {
                        out += HtmlTextData(text = text, color = getTextColorInStyle(element))
                    }
                } else {
                    element.children().forEach { appendBlockText(it, out) }
                }
            }
        }
    }

    //列表项:加序号或项目符号
    private fun appendListItem(
        li: Element,
        out: MutableList<HtmlTextData>,
        ordered: Boolean,
        index: Int
    ) {
        val prefix = if (ordered) "${index + 1}. " else "• "

        //li 内可能是 <p>,取其文本
        val paragraphs = li.getElementsByTag("p")

        if (paragraphs.isNotEmpty()) {
            paragraphs.forEachIndexed { pIndex, p ->
                val text = collapseTimeTags(p.text())
                if (text.isNotBlank()) {
                    out += HtmlTextData(
                        text = if (pIndex == 0) prefix + text else text,
                        color = getTextColorInStyle(p)
                    )
                }
            }
        } else {
            val text = collapseTimeTags(li.text())
            if (text.isNotBlank()) {
                out += HtmlTextData(text = prefix + text, color = getTextColorInStyle(li))
            }
        }

        out += HtmlTextData(text = "\n", color = Color.Transparent)
    }

    /*
    * ol/ul 列表
    *
    * 实测顶层 ol 18 个(共 104 个 li),原实现全部丢弃。
    * 列表项文本直接放进 textList,由渲染层按行显示。
    * */
    private fun parseList(list: Element): HtmlSpanData {
        val ordered = list.tagName() == "ol"
        val items = mutableListOf<String>()

        list.children().forEachIndexed { index, li ->
            if (li.tagName() != "li") {
                return@forEachIndexed
            }

            val prefix = if (ordered) "${index + 1}. " else "• "
            val text = collapseTimeTags(li.text())

            if (text.isNotBlank()) {
                items += prefix + text
            }
        }

        return HtmlSpanData(
            type = HtmlSpanType.List,
            listItems = items,
            data = list.text()
        )
    }

    /*
    * 标题 h1~h6
    *
    * 实测顶层 h4 8 个、h2 4 个,原实现丢弃。
    * 保留级别供渲染层决定字号。
    * */
    private fun parseHeading(heading: Element): HtmlSpanData {
        val level = heading.tagName().removePrefix("h").toIntOrNull() ?: 2
        val text = collapseTimeTags(heading.text())

        return HtmlSpanData(
            type = HtmlSpanType.Heading,
            headingLevel = level,
            textList = listOf(HtmlTextData(text = text, color = getTextColorInStyle(heading)))
        )
    }

    //ql-fold 折叠文本(米游社帖子里的原有类型,保持兼容)
    private fun parseFold(parent: Element): HtmlSpanData {
        val titleList = mutableListOf<HtmlTextData>()
        val textList = mutableListOf<HtmlTextData>()

        parent.getElementsByClass("ql-fold-title-content").first()
            ?.getElementsByTag("span")
            ?.forEach { span ->
                titleList += HtmlTextData(
                    text = collapseTimeTags(span.text()),
                    color = getTextColorInStyle(span)
                )
            }

        parent.getElementsByClass("ql-fold-content").first()
            ?.getElementsByTag("p")?.forEach { p ->
                p.getElementsByTag("span").forEach { text ->
                    textList += HtmlTextData(
                        text = collapseTimeTags(text.text()),
                        color = getTextColorInStyle(text)
                    )
                }
                textList += HtmlTextData(text = "\n", color = Color.Transparent)
            }

        return HtmlSpanData(
            type = HtmlSpanType.Fold,
            titleList = titleList,
            textList = textList
        )
    }

    //ql-link-card 外链卡片(保持原有行为)
    private fun parseLinkCard(parent: Element): HtmlSpanData {
        var data = ""

        val cover = parent.getElementsByClass("card-cover").firstOrNull()
        cover?.attr("style")?.split(";")?.forEach { kv ->
            val index = kv.indexOf(':')
            if (index != -1) {
                val k = kv.substring(0, index).trim()
                val v = kv.substring(index + 1).trim()
                if (k == "background-image") {
                    data = v.removePrefix("url(\"").removeSuffix("\")")
                }
            }
        }

        return HtmlSpanData(
            type = HtmlSpanType.LinkCard,
            data = data,
            titleList = listOf(
                HtmlTextData(text = parent.getElementsByClass("card-title").text())
            )
        )
    }

    /*
    * 折叠 <t> 时间标签
    *
    * 实测服务端下发的是**转义形式** &lt;t class="t_lc"&gt;2026/09/01 18:00&lt;/t&gt;
    * (114 处)。Jsoup 解析后 element.text() 已经把这些实体还原成
    * 字面 <t …>…</t> 文本,故这里用正则把字面标签去掉、只留时间。
    *
    * 转义与未转义两种形式都能覆盖(未转义时 Jsoup 会当成未知元素,
    * text() 同样只留文本,不进入这里)。
    * */
    fun collapseTimeTags(text: String): String =
        timeTagRegex.replace(text) { it.groupValues[1] }

    /*
    * 折叠**尚未解析**的 HTML 源码里的转义时间标签
    *
    * 输入形如:
    *   制作组预计将于&lt;t class="t_gl" contenteditable="false"&gt;2026/09/23 06:00&lt;/t&gt;进行…
    * 输出:
    *   制作组预计将于2026/09/23 06:00进行…
    *
    * 为什么必须在 Jsoup 之前做:
    *   转义形式在 Jsoup 眼里只是**普通文本**,不是元素,所以
    *   element.text() 会原样保留 "&lt;t …&gt;"。而真机路径
    *   (HtmlCompat.fromHtml 成功)根本不经过 text(),它直接把
    *   富文本交给渲染层 —— 于是尖括号就显示到了界面上。
    *   在这里先把源码里的转义标签替换成纯文本,两条分支就都能拿到干净内容。
    *
    * 同时兼容未转义写法(<t ...>…</t>),避免服务端两种风格混用时漏掉。
    * */
    fun collapseEscapedTimeTags(html: String): String =
        escapedTimeTagRegex.replace(html) { it.groupValues[1] }

    //匹配字面 <t ...>内容</t>
    private val timeTagRegex =
        Regex("""<t\b[^>]*>(.*?)</t>""", RegexOption.DOT_MATCHES_ALL)

    /*
    * 匹配**转义**形式 &lt;t ...&gt;内容&lt;/t&gt;
    *
    * 属性部分用 (?:&gt;|[^&]) 这类宽松匹配:转义后 '>' 变成 "&gt;",
    * 属性里也可能含其它实体,所以不能简单用 [^>]*。
    * */
    private val escapedTimeTagRegex =
        Regex("""&lt;t\b(?:(?!&lt;).)*?&gt;(.*?)&lt;/t&gt;""", RegexOption.DOT_MATCHES_ALL)

    //对齐方式
    private fun getAlign(element: Element): Alignment.Horizontal {
        val classes = element.classNames()
        val style = element.attr("style")

        return if ("ql-align-center" in classes || style.contains("text-align: center")) {
            Alignment.CenterHorizontally
        } else {
            Alignment.Start
        }
    }

    //取 style 里的 background-color
    private fun getBackgroundColorInStyle(element: Element): Color? {
        val style = element.attr("style")

        style.split(";").forEach { kv ->
            val index = kv.indexOf(':')
            if (index != -1) {
                val k = kv.substring(0, index).trim()
                val v = kv.substring(index + 1).trim()

                if (k == "background-color") {
                    return parseColor(v)
                }
            }
        }

        return null
    }

    //取 style 里的 color
    private fun getTextColorInStyle(element: Element): Color {
        val style = element.attr("style")

        style.split(";").forEach { kv ->
            if (kv.trim().startsWith("color")) {
                val value = kv.substringAfter(':').trim()
                return parseColor(value) ?: Black
            }
        }

        return Black
    }

    /*
    * 解析颜色字符串
    *
    * 支持 rgb(r, g, b) 与 #RRGGBB / #AARRGGBB;
    * 无法识别时返回 null(调用方决定兜底颜色),**不抛异常** ——
    * 原实现对颜色单词走 toColorInt 并在失败时弹提示,会让一条颜色异常
    * 影响整段渲染。
    * */
    private fun parseColor(value: String): Color? {
        val v = value.trim()

        if (v.startsWith("rgb")) {
            val parts = v.removePrefix("rgb").removePrefix("rgba")
                .removePrefix("(").removeSuffix(")").split(",")

            val r = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: return null
            val g = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: return null
            val b = parts.getOrNull(2)?.trim()?.toIntOrNull() ?: return null

            return Color(r, g, b)
        }

        return try {
            Color(v.toColorInt())
        } catch (e: Exception) {
            null
        }
    }
}
