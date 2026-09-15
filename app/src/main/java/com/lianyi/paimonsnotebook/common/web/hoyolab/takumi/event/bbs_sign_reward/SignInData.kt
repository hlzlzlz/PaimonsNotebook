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

//补签信息
data class SignInResignInfoData(
    val resign_cnt_daily: Int = 0,
    val resign_cnt_monthly: Int = 0,
    val resign_limit_daily: Int = 0,
    val resign_limit_monthly: Int = 0,
    val sign_cnt_missed: Int = 0,
    val coin_cnt: Int = 0,
    val coin_cost: Int = 0
) {
    //补签卡足够且未达限额时可以补签
    val canResign: Boolean
        get() = sign_cnt_missed > 0 &&
                resign_cnt_daily < resign_limit_daily &&
                resign_cnt_monthly < resign_limit_monthly &&
                coin_cnt >= coin_cost && coin_cost > 0
}

//签到请求体
data class SignInRequestBody(
    val act_id: String,
    val region: String,
    val uid: String
)
