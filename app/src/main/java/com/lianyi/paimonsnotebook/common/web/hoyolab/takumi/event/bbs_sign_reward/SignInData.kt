package com.lianyi.paimonsnotebook.common.web.hoyolab.takumi.event.bbs_sign_reward

/*
* 米游社签到 luna 接口数据
* */
data class SignInInfoData(
    val total_sign_day: Int,
    val today: String,
    val is_sign: Boolean,
    val is_sub: Boolean,
    val region: String,
    val sign_cnt_missed: Int,
    val short_sign_day: Int
)

data class SignInResultData(
    val code: String,
    val risk_code: Int,
    val gt: String,
    val challenge: String,
    val success: Int,
    val is_risk: Boolean
)

data class SignInRewardData(
    val month: Int,
    val awards: List<Award>,
    val biz: String,
    val resign: Boolean
) {
    data class Award(
        val icon: String,
        val name: String,
        val cnt: Int
    )
}

//签到请求体
data class SignInRequestBody(
    val act_id: String,
    val region: String,
    val uid: String
)
