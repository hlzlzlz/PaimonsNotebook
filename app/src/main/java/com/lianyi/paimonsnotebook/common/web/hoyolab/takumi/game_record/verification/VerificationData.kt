package com.lianyi.paimonsnotebook.common.web.hoyolab.takumi.game_record.verification

/*
* 1034风控验证数据
* */
//createVerification返回的极验挑战
data class GeetestVerificationData(
    val success: Int,
    val gt: String,
    val challenge: String,
    val new_captcha: Int
)

//verifyVerification返回的验证结果
data class VerificationResultData(
    val challenge: String?
)
