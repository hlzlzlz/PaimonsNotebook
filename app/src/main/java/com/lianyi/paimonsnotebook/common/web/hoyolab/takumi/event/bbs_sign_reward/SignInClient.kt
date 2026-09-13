package com.lianyi.paimonsnotebook.common.web.hoyolab.takumi.event.bbs_sign_reward

import com.lianyi.paimonsnotebook.common.data.hoyolab.PlayerUid
import com.lianyi.paimonsnotebook.common.database.user.entity.User
import com.lianyi.paimonsnotebook.common.extension.request.setDynamicSecret
import com.lianyi.paimonsnotebook.common.extension.request.setUser
import com.lianyi.paimonsnotebook.common.util.hoyolab.DynamicSecret
import com.lianyi.paimonsnotebook.common.util.request.buildRequest
import com.lianyi.paimonsnotebook.common.util.request.getAsJson
import com.lianyi.paimonsnotebook.common.util.request.post
import com.lianyi.paimonsnotebook.common.web.ApiEndpoints
import com.lianyi.paimonsnotebook.common.web.hoyolab.cookie.CookieHelper

/*
* 米游社签到客户端
* 使用cookie_token鉴权,DS为Gen1+LK2(includeChars),全部请求带x-rpc-signgame头
* */
class SignInClient {

    suspend fun getSignInInfo(
        user: User,
        playerUid: PlayerUid
    ) = buildRequest {
        url(ApiEndpoints.SignInInfo(playerUid))

        setUser(user, CookieHelper.Type.CookieToken)
        addHeader("x-rpc-signgame", ApiEndpoints.SignInGameBiz)

        setDynamicSecret(DynamicSecret.SaltType.LK2, DynamicSecret.Version.Gen1, includeChars = true)

    }.getAsJson<SignInInfoData>()

    suspend fun getSignInReward() = buildRequest {
        url(ApiEndpoints.SignInHome())

        addHeader("x-rpc-signgame", ApiEndpoints.SignInGameBiz)

    }.getAsJson<SignInRewardData>()

    suspend fun sign(
        user: User,
        playerUid: PlayerUid
    ) = buildRequest {
        url(ApiEndpoints.SignInSign)

        setUser(user, CookieHelper.Type.CookieToken)
        addHeader("x-rpc-signgame", ApiEndpoints.SignInGameBiz)

        setDynamicSecret(DynamicSecret.SaltType.LK2, DynamicSecret.Version.Gen1, includeChars = true)

        buildMap {
            put(
                "act_id", ApiEndpoints.SignInActId
            )
            put("region", playerUid.region)
            put("uid", playerUid.value)
        }.post(this)

    }.getAsJson<SignInResultData>()
}
