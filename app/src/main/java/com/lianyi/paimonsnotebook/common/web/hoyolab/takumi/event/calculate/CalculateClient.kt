package com.lianyi.paimonsnotebook.common.web.hoyolab.takumi.event.calculate

import com.lianyi.paimonsnotebook.common.data.hoyolab.user.User
import com.lianyi.paimonsnotebook.common.extension.request.setReferer
import com.lianyi.paimonsnotebook.common.extension.request.setUser
import com.lianyi.paimonsnotebook.common.util.json.JSON
import com.lianyi.paimonsnotebook.common.util.request.buildRequest
import com.lianyi.paimonsnotebook.common.util.request.emptyOkHttpClient
import com.lianyi.paimonsnotebook.common.util.request.getAsJson
import com.lianyi.paimonsnotebook.common.web.ApiEndpoints
import com.lianyi.paimonsnotebook.common.web.hoyolab.cookie.CookieHelper
import okhttp3.RequestBody.Companion.toRequestBody

class CalculateClient {
    suspend fun getCalculateCompute(user: User, promotionDetail: PromotionDetail) =
        buildRequest {
            url(ApiEndpoints.CalculateCompute)

            setUser(user.userEntity, CookieHelper.Type.CookieToken)
            setReferer(ApiEndpoints.WebStaticMihoyoReferer)

            post(JSON.stringify(promotionDetail).toRequestBody())
        }.getAsJson<Consumption>(emptyOkHttpClient)

    suspend fun getCalculateBatchCompute(
        user: User,
        promotionDetail: BatchCalculatePromotionDetail
    ) = buildRequest {
        url(ApiEndpoints.CalculateBatchCompute)

        setUser(user = user.userEntity, CookieHelper.Type.CookieToken)
        setReferer(ApiEndpoints.WebStaticMihoyoReferer)

        post(JSON.stringify(promotionDetail).toRequestBody())
    }.getAsJson<BatchComputeData>(emptyOkHttpClient)

    /*
    * 洞天摹本:按分享码取该摹本需要的家具清单
    *
    * shareCode 必须是**纯码**:整段文本(含"摹本分享码:"或 URL)会让接口 404,
    * 调用方应先用 FurnitureShareCodeParser.extract 抽取。
    * */
    suspend fun getFurnitureBlueprint(
        user: User,
        shareCode: String
    ) = buildRequest {
        url(ApiEndpoints.CalculateFurnitureBlueprint(shareCode))

        setUser(user = user.userEntity, CookieHelper.Type.CookieToken)
        setReferer(ApiEndpoints.WebStaticMihoyoReferer)
    }.getAsJson<FurnitureListData>(emptyOkHttpClient)

    /*
    * 家具计算:给定家具清单,算所需材料与缺口
    * body 形如 {"list":[{"id":1,"cnt":2}]}
    * */
    suspend fun getFurnitureCompute(
        user: User,
        items: List<FurnitureComputeItem>
    ) = buildRequest {
        url(ApiEndpoints.CalculateFurnitureCompute)

        setUser(user = user.userEntity, CookieHelper.Type.CookieToken)
        setReferer(ApiEndpoints.WebStaticMihoyoReferer)

        post(JSON.stringify(FurnitureComputeRequest(items)).toRequestBody())
    }.getAsJson<FurnitureListData>(emptyOkHttpClient)
}

/*
* 家具计算的请求体
*
* ⚠️ 数量字段名是 **cnt**(不是 num)——
*    胡桃 CalculateClient.FurnitureComputeAsync 里用的是 IdCount { Id, Count },
*    而其 JSON 映射为 id/cnt。写成 num 会被服务端当 0 处理。
* */
data class FurnitureComputeRequest(
    @com.google.gson.annotations.SerializedName("list")
    val list: List<FurnitureComputeItem>
)

data class FurnitureComputeItem(
    @com.google.gson.annotations.SerializedName("id")
    val id: Int,
    @com.google.gson.annotations.SerializedName("cnt")
    val cnt: Int
)