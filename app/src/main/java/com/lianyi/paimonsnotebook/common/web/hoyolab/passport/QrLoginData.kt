package com.lianyi.paimonsnotebook.common.web.hoyolab.passport

/*
* 通行证扫码登录数据
* */
data class QrLoginData(
    val url: String,
    val ticket: String
)

/*
* 扫码状态查询结果
* status: Created(未扫码) / Scanned(已扫码待确认) / Confirmed(已确认)
* tokens中token_type为1的是stoken(v2)
* */
data class QrLoginStatusData(
    val status: String,
    val app_id: String,
    val client_type: Int,
    val created_at: String,
    val scanned_at: String,
    val tokens: List<Token>,
    val user_info: UserInfo,
    val need_realperson: Boolean
) {
    data class Token(
        val token: String,
        val token_type: Int
    )

    data class UserInfo(
        val aid: String,
        val mid: String
    )
}
