package com.lianyi.paimonsnotebook.common.web.hoyolab.hk4e.event.gacha_info

/*
* 千星奇域(UGC)祈愿记录的一页
*
* 端点:GET /gacha_info/api/getBeyondGachaLog
*
* 实测(2026-09-19,uid 338131141 / cn_gf01,该账号无 UGC 记录):
*   原始响应 {"retcode":0,"message":"OK","data":{"total":"0","list":[]}}
*   => data 只有 **total 与 list 两个键**
*
* ⚠️ 与普通祈愿 getGachaLog 的 data **结构不同**,不要照抄 GachaLogData:
*   getGachaLog     data 键 = page / size / total / list / region  (5 个)
*   getBeyondGachaLog data 键 = total / list                       (2 个,无 page/size/region)
*   (两边都有 total 且**都是字符串**;胡桃也把 BeyondGachaLogPage.Total 标为
*    "总是为 0"的过时字段)
*
* list 里的字段类型见 BeyondGachaLogItem 的说明 —— 因无法实测,采用宽松解析。
* */
data class BeyondGachaLogPage(
    val total: String,
    val list: List<BeyondGachaLogItem>
)
