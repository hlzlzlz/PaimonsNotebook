package com.lianyi.paimonsnotebook.common.web.hoyolab.hk4e.event.gacha_info

import com.lianyi.paimonsnotebook.common.util.request.buildRequest
import com.lianyi.paimonsnotebook.common.util.request.getAsJson
import com.lianyi.paimonsnotebook.common.web.ApiEndpoints

class GachaInfoClient {

    suspend fun getGachaLogPage(configData: GachaQueryConfigData) =
        buildRequest {
            url(ApiEndpoints.GachaInfoGetGachaLog(configData.asQueryParameter))
        }.getAsJson<GachaLogData>()

    /*
    * 千星奇域(UGC)祈愿记录
    *
    * 与普通祈愿共用同一套 authkey 查询参数,只是端点路径不同
    * (/getBeyondGachaLog),故复用 GachaQueryConfigData。
    *
    * 注意 data 结构与 GachaLogData 不同(无 page/size/region),
    * 见 BeyondGachaLogPage 的说明。
    * */
    suspend fun getBeyondGachaLogPage(configData: GachaQueryConfigData) =
        buildRequest {
            url(ApiEndpoints.GachaInfoGetBeyondGachaLog(configData.asQueryParameter))
        }.getAsJson<BeyondGachaLogPage>()

}