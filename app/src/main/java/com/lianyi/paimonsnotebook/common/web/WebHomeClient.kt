package com.lianyi.paimonsnotebook.common.web

import com.lianyi.paimonsnotebook.common.extension.request.setReferer
import com.lianyi.paimonsnotebook.common.util.request.buildRequest
import com.lianyi.paimonsnotebook.common.util.request.getAsJson
import com.lianyi.paimonsnotebook.common.web.hoyolab.bbs.*
import com.lianyi.paimonsnotebook.common.web.hoyolab.bbs.post.PostFullData
import com.lianyi.paimonsnotebook.common.web.hoyolab.takumi.event.miyolive.MiyoliveCodeData


/*
* 用于请求首页的所需数据
* todo 待删除
* */
class WebHomeClient {

    suspend fun getWebHome() =
        buildRequest {
            url(ApiEndpoints.BbsWebHome)
        }.getAsJson<WebHomeData>()

    suspend fun getOfficialRecommendedPosts() =
        buildRequest {
            url(ApiEndpoints.OfficialRecommendedPosts)
        }.getAsJson<OfficialRecommendedPostsData>()

    suspend fun getPostFull(postId:Long) =
        buildRequest {
            url(ApiEndpoints.getPostFull(postId))

            setReferer("https://bbs.mihoyo.com/")
        }.getAsJson<PostFullData>()

    suspend fun getNearActivity() =
        buildRequest {
            url(ApiEndpoints.NearActivity)
        }.getAsJson<NearActivityData>()

    //米游社首页信息(前瞻直播入口)
    suspend fun getNewHomeInfo() =
        buildRequest {
            url(ApiEndpoints.BbsHomeNew)
        }.getAsJson<NewHomeNewInfo>()

    //前瞻直播兑换码
    suspend fun refreshMiyoliveCode(actId: String) =
        buildRequest {
            url(ApiEndpoints.MiyoliveRefreshCode())

            addHeader("x-rpc-act_id", actId)
        }.getAsJson<MiyoliveCodeData>()

}