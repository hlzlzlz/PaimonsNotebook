package com.lianyi.paimonsnotebook.common.web.hoyolab.takumi.event.calculate

import com.google.gson.annotations.SerializedName

/*
* 洞天摹本 / 家具计算的数据结构
*
* 对齐胡桃 CalculateClient 与 FurnitureListWrapper / Item:
*   - blueprint 与 compute 返回的都是 ListWrapper<Item>
*   - blueprint 额外带 not_calc_list(不在计算范围内的家具)
*
* ⚠️ 字段名用 @SerializedName 显式声明:本工作区有过 R8 混淆导致
*    Gson 落盘数据读不回来的事故,外部响应解析同样不应依赖字段名巧合。
* */
data class FurnitureListData(
    @SerializedName("list")
    val list: List<FurnitureItem> = emptyList(),

    /*
    * 仅 blueprint 返回:不在计算范围内的家具
    * ⚠️ 必须可空并给默认值 —— 老响应/其它端点没有该字段
    * */
    @SerializedName("not_calc_list")
    val notCalcList: List<FurnitureItem> = emptyList()
)

/*
* 单件家具
*
* num / lack_num / level 三个字段是"有 uid 与 region 时才返回"的
* (与 batch_compute 的 lack_num 同理),因此都可空并给默认值 ——
* 缺失时退化为 0,而不是让整份响应解析失败。
* */
data class FurnitureItem(
    @SerializedName("id")
    val id: Int = 0,

    @SerializedName("name")
    val name: String = "",

    @SerializedName("icon_url")
    val iconUrl: String = "",

    @SerializedName("num")
    val num: Int = 0,

    @SerializedName("level")
    val level: Int = 0,

    @SerializedName("lack_num")
    val lackNum: Int = 0
)
