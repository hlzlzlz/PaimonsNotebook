package com.lianyi.paimonsnotebook.common.web.hoyolab.takumi.event.miyolive

/*
* 前瞻直播兑换码(event/miyolive/refreshCode,header x-rpc-act_id)
* */
data class MiyoliveCodeData(
    val code_list: List<CodeWrapper>?
) {
    data class CodeWrapper(
        val title: String?,
        val code: String?,
        val img: String?,
        val to_get_time: Long = 0L
    )
}
