package com.lianyi.paimonsnotebook.common.web.hoyolab.bbs

/*
* 米游社首页信息(apihub/api/home/new)
* 仅解析前瞻直播兑换码相关字段:直播入口在lives[].data.live_url
* 或navigator[]中名为"直播兑换码/前瞻直播"的app_path
* */
data class NewHomeNewInfo(
    val navigator: List<AppNavigator>?,
    val lives: List<LiveInfo>?
) {
    data class AppNavigator(
        val name: String?,
        val app_path: String?
    )

    data class LiveInfo(
        val data: LiveInfoData?
    )

    data class LiveInfoData(
        val live_url: String?
    )
}
