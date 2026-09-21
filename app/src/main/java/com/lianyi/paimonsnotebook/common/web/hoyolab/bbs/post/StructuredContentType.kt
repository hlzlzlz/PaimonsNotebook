package com.lianyi.paimonsnotebook.common.web.hoyolab.bbs.post

/*
* 结构内容类型
* */
enum class StructuredContentType {
    Text,
    BackupText,
    Image,
    Vod,
    Divider,
    Fold,
    LinkCard,
    Link,
    Error,
    Lottery,
    Empty,

    /*
    * 投票
    *
    * 此前 Insert.vote 已被解析(PostStructuredContentData.Insert.Vote),
    * 但 StructuredContentType 没有对应成员、RichTextParser 也没处理该 key,
    * 于是含投票的帖子走到 else 分支被标成 Error,而
    * PostStructureContentItem 的 when 里 Error 落到 `else -> {}` ⇒
    * **投票块在帖子里完全不可见**(不是渲染成占位,而是整块消失)。
    * */
    Vote
}