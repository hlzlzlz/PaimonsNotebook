package com.lianyi.paimonsnotebook.common.util.html

enum class HtmlSpanType {
    P,
    SP,
    Span,
    Img,
    Div,
    Br,
    A,
    Video,
    Fold,
    LinkCard,

    /*
    * 以下为 2026-09-20 新增 —— 游戏公告正文实测出现的顶层标签,
    * 此前全部落入 else 分支导致内容丢失(详见 HtmlSpanParser 的说明)。
    * */
    Table,
    List,
    Heading
}
