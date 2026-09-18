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
* 同时负责剥掉正文里的时间占位标签 —— 服务端下发的正文里可能带
* <t time="..." > 这类客户端渲染占位,不处理会原样显示成一串尖括号。
* 胡桃用 AnnouncementRegex.XmlTimeTagRegex 做同样的事。
* */
object AnnouncementHelper {

    /*
    * 匹配 <t ...>内容</t> 形式的时间标签,只保留其文本内容
    *
    * 与胡桃的 XmlTimeTagRegex 等价(它取 Group[1],即标签内的文本)
    * */
    private val xmlTimeTagRegex =
        Regex("""<t\b[^>]*>(.*?)</t>""", RegexOption.DOT_MATCHES_ALL)

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
    * 目前只做一件事:把 <t ...>xxx</t> 折叠成 xxx。
    * 其它标签(<p>/<img>)原样保留,交给 HtmlTextLazyColumn 渲染。
    * */
    fun cleanupContent(content: String): String =
        if (content.isEmpty()) {
            content
        } else {
            xmlTimeTagRegex.replace(content) { it.groupValues[1] }
        }

    /*
    * 统计非空分组数,用于判断"到底有没有公告"
    * */
    fun countItems(groups: List<AnnouncementGroup>): Int =
        groups.sumOf { it.list.size }
}
