package com.lianyi.paimonsnotebook.common.web.hoyolab.hk4e.announcement

/*
* 游戏内公告列表(getAnnList)的响应数据
*
* 接口:https://hk4e-ann-api.mihoyo.com/common/hk4e_cn/announcement/api/getAnnList
* 实测(2026-09-18,未登录可访问):
*   retcode=0,data.list 按 type_id 分 3 组共 33 条
*   (type_id=2 游戏公告 14 / type_id=1 活动公告 14 / type_id=26 千星奇域 5)
*
* ⚠️ 字段类型务必以**真实响应**为准:has_content 实际是 Boolean,
* 当初照"0/1 标记"的惯例写成 Int,直接导致整个列表解析失败(2026-09-19 修)。
* 新增字段前先 curl 一次真实接口确认类型,不要凭命名推断。
*
* 命名沿用接口的 snake_case,不做 @SerializedName 重命名
* (Gson 直接按字段名匹配,与项目其它接口数据类风格一致)
* */

/*
* ⚠️ 响应信封不要在这里定义:真实调用链是
*   getAsJson<T>() -> Gson.fromJson(..., ResultData<T>)
* (见 requests.kt:188),信封已由 common/data/ResultData.kt 承担。
* 这里曾定义过 AnnouncementListResponse / AnnouncementContentResponse,
* 但全树零引用(死代码),且容易让人误以为要自己解一层信封,已删除。
* */

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
* has_content 标明是否有正文,为 false 时不必去取
*
* ⚠️ has_content 是**布尔值**不是整数(2026-09-19 实测 33/33 条全为 true/false)。
* 原先误声明成 Int,Gson 遇到 JSON true/false 会抛
*   IllegalStateException: Expected an int but was BOOLEAN at ... has_content
* 导致公告列表整个加载失败(界面显示"公告获取失败")。
* 接口的 snake_case 命名保留,故这里字段名仍是 has_content,只改类型。
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
    val has_content: Boolean = false,
    val content: String = ""
) {
    //是否有正文可取(直接就是布尔值,不需要再和 1 比较)
    val hasContent: Boolean
        get() = has_content
}

/*
* 公告正文(getAnnContent)的响应数据(信封同上,由 ResultData 承担)
*
* 实测:retcode=0,data.list 共 33 条,与 getAnnList 的条目一一对应
* content 是 HTML 片段,含 <p style=...> 与 <img src=...>,
* 可直接交给 HtmlTextLazyColumn 渲染
* */
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
