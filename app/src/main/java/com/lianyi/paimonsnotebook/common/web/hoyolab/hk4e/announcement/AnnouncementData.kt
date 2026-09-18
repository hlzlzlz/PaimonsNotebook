package com.lianyi.paimonsnotebook.common.web.hoyolab.hk4e.announcement

/*
* 游戏内公告列表(getAnnList)的响应数据
*
* 接口:https://hk4e-ann-api.mihoyo.com/common/hk4e_cn/announcement/api/getAnnList
* 实测(2026-09-18,未登录可访问):
*   retcode=0,data.list 按 type_id 分 3 组共 33 条
*   (type_id=2 游戏公告 14 / type_id=1 活动公告 14 / type_id=26 千星奇域 5)
*
* 命名沿用接口的 snake_case,不做 @SerializedName 重命名
* (Gson 直接按字段名匹配,与项目其它接口数据类风格一致)
* */

//响应信封
data class AnnouncementListResponse(
    val retcode: Int,
    val message: String,
    val data: AnnouncementListData?
)

data class AnnouncementListData(
    val list: List<AnnouncementGroup>,
    val total: Int,
    val timezone: Int
)

//按 type_id 分组的公告,例如"游戏公告""活动公告"
data class AnnouncementGroup(
    val list: List<AnnouncementItem>,
    val type_id: Int,
    val type_label: String
)

/*
* 单条公告
*
* content 字段在 getAnnList 里是空的(有时是占位),
* 真正的内容要由 getAnnContent 按 ann_id 取,见 AnnouncementContentResponse
* has_content 标明是否有正文,为 0 时不必去取
* */
data class AnnouncementItem(
    val ann_id: Int,
    val title: String,
    val subtitle: String,
    val banner: String,
    val type_label: String,
    val tag_label: String,
    val tag_icon: String,
    val start_time: String,
    val end_time: String,
    val has_content: Int = 0,
    val content: String = ""
) {
    //服务端 has_content 为 1 表示有正文可取
    val hasContent: Boolean
        get() = has_content == 1
}

/*
* 公告正文(getAnnContent)的响应数据
*
* 实测:retcode=0,data.list 共 33 条,与 getAnnList 的条目一一对应
* content 是 HTML 片段,含 <p style=...> 与 <img src=...>,
* 可直接交给 HtmlTextLazyColumn 渲染
* */
data class AnnouncementContentResponse(
    val retcode: Int,
    val message: String,
    val data: AnnouncementContentListData?
)

data class AnnouncementContentListData(
    val list: List<AnnouncementContentItem>
)

data class AnnouncementContentItem(
    val ann_id: Int,
    val title: String,
    val subtitle: String,
    val banner: String,
    val content: String
)
