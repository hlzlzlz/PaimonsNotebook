package com.lianyi.paimonsnotebook.common.web.hoyolab.hk4e.announcement

import com.lianyi.paimonsnotebook.common.util.request.buildRequest
import com.lianyi.paimonsnotebook.common.util.request.getAsJson
import com.lianyi.paimonsnotebook.common.web.ApiEndpoints

/*
* 游戏内公告客户端
*
* 端点早已在 ApiEndpoints 声明(AnnList / AnnContent),但此前全项目零引用 ——
* AnnouncementScreen 是空壳残骸(内容全被注释、未注册 manifest)。
* 本次把它接上。
*
* 关键性质:两个端点**无需登录**即可访问。实测(2026-09-18)未带任何 cookie:
*   getAnnList    -> 200,28702 B,retcode=0,3 组共 33 条
*   getAnnContent -> 200,220821 B,retcode=0,33 条正文
* 所以本功能对未登录用户同样可用,不依赖 device_fp/DS 签名。
*
* 注意 AnnouncementQuery 里 region/uid 是固定占位值(cn_gf01 / 100000000),
* 公告是按游戏+语言下发而非按账号,故不需要真实 uid。
* */
class AnnouncementClient {

    /*
    * 公告列表(按 type_id 分组)
    *
    * 返回 groups 是响应里 data.list 的分组列表。
    * data 声明非空但服务端可能返回 null(见 ResultData 注释),故调用方需判空。
    * */
    suspend fun getAnnouncementList() =
        buildRequest {
            url(ApiEndpoints.AnnList)
        }.getAsJson<AnnouncementListData>()

    /*
    * 公告正文(全部条目,含 content HTML)
    *
    * 与 getAnnouncementList 的条目按 ann_id 一一对应,由
    * AnnouncementService 负责合并,本客户端不做合并。
    * */
    suspend fun getAnnouncementContent() =
        buildRequest {
            url(ApiEndpoints.AnnContent)
        }.getAsJson<AnnouncementContentListData>()
}
