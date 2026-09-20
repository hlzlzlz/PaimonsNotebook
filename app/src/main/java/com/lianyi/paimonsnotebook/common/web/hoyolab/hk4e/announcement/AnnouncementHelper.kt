package com.lianyi.paimonsnotebook.common.web.hoyolab.hk4e.announcement

/*
* 公告数据的合并与清洗
*
* 接口设计是"列表"与"正文"两个端点分开返回:
*   getAnnList    -> 标题/时间/分类等元信息,content 为空或占位
*   getAnnContent -> 按 ann_id 对应的正文 HTML
* 服务端不发"取某一条"的接口,只能把整份正文拉下来本地合并
* (胡桃的 AnnouncementService 也是这么做的:取全部 content 建
*  Dictionary<AnnId, Content> 再回填到列表条目)。
*
* 正文里的 <t> 时间占位标签由 HtmlSpanParser 负责折叠,本类不再做正则清洗
* (原因见 cleanupContent 的说明)。
* */
object AnnouncementHelper {

    /*
    * 合并列表与正文,并清洗
    *
    * groups:getAnnList 的分组
    * contents:getAnnContent 的条目(可为 null,此时只返回列表不带正文)
    *
    * 返回:按原分组顺序、组内按开始时间倒序(最新的在前)的分组列表
    * */
    fun merge(
        groups: List<AnnouncementGroup>,
        contents: List<AnnouncementContentItem>?
    ): List<AnnouncementGroup> {
        //ann_id -> 正文 HTML
        val contentMap = contents.orEmpty().associate { it.ann_id to it.content }

        return groups.map { group ->
            group.copy(
                list = group.list
                    .map { item ->
                        item.copy(
                            content = cleanupContent(contentMap[item.ann_id].orEmpty())
                        )
                    }
                    //时间字符串形如 "2026-09-12 21:15:00",同格式可直接按字符串倒序
                    .sortedByDescending { it.start_time }
            )
        }
    }

    /*
    * 清洗正文 HTML
    *
    * ⚠️ 2026-09-20 更正:此前这里用正则把 `<t ...>xxx</t>` 折叠成 xxx,
    * 但**服务端下发的是 HTML 转义形式** `&lt;t class="t_lc"&gt;2026/09/01 18:00&lt;/t&gt;`
    * (34 条真实公告里实测 114 处),而正则匹配的是字面 `<t\b`,**根本匹配不到**
    * ⇒ 用户看到的就是一串 `&lt;t class="t_lc"...&gt;` 尖括号。
    *
    * 现在不再在这里做正则替换:转义实体交给 HtmlSpanParser 处理 ——
    * Jsoup 解析时会把 `&lt;t&gt;` 还原成字面文本,再由
    * HtmlSpanParser.collapseTimeTags 折叠掉,转义与未转义两种形式都能覆盖。
    *
    * 保留本函数是为了不破坏既有调用点与测试,现在它只做"原样返回"。
    * */
    fun cleanupContent(content: String): String = content

    /*
    * 统计非空分组数,用于判断"到底有没有公告"
    * */
    fun countItems(groups: List<AnnouncementGroup>): Int =
        groups.sumOf { it.list.size }
}
