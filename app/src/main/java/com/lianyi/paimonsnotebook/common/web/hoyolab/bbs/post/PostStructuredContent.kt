package com.lianyi.paimonsnotebook.common.web.hoyolab.bbs.post

data class PostStructuredContent(
    val type: StructuredContentType,
    val attributes: StructuredContentAttributes?,
    val insert: Item,
) {
    companion object {
        fun getTextItem(content: String?, attributes: StructuredContentAttributes? = null) =
            PostStructuredContent(
                type = StructuredContentType.Text,
                attributes = attributes,
                insert = Item(
                    content = content
                )
            )

        fun getImageItem(url: String?, attributes: StructuredContentAttributes? = null) =
            PostStructuredContent(
                type = StructuredContentType.Image,
                attributes = attributes,
                insert = Item(
                    url = url
                )
            )

        fun getVodItem(
            structuredVod: PostFullData.Post.Vod?,
            attributes: StructuredContentAttributes? = null
        ) =
            PostStructuredContent(
                type = StructuredContentType.Vod,
                attributes = attributes,
                insert = Item(
                    vod = structuredVod
                )
            )

    }

    data class Item(
        val vod: PostFullData.Post.Vod? = null,
        val linkCard: PostFullData.Post.LinkCard? = null,
        val backupText: StructuredBackupText? = null,
        val url: String? = null,
        val content: String? = null,
        /*
        * 投票块
        *
        * ⚠️ 只有 id 与 uid 两个字段(服务端在结构化内容里只给标识),
        *    具体标题/选项需要用 getVotes(owner_uid, vote_ids) 另取。
        *    本类只承载**已确认存在**的字段,不臆测投票正文结构。
        * */
        val vote: PostStructuredContentData.Insert.Vote? = null
    )
}
