package com.lianyi.paimonsnotebook.common.util.hoyolab.strategy

import com.lianyi.paimonsnotebook.common.web.hutao.statistics.HutaoAvatarStrategyData
import com.lianyi.paimonsnotebook.common.web.hutao.statistics.HutaoStatisticsClient

/*
* 角色攻略链接
*
* 优先使用胡桃API的strategy接口拿"精确攻略帖"链接(需联网且接口可用);
* 该接口目前实测返回404(同域其它路由正常,属路由下线而非鉴权),
* 此时退化为可直接打开的攻略站直链。
*
* 注意:退化链接是必要的 —— 原实现取不到strategyId就返回null,
* 而调用方是 strategyUrl?.let { ... } ,即按钮直接不渲染,
* 功能静默消失且无任何提示(见 AvatarInformationContent:126)。
* */
object AvatarStrategyHelper {

    private val client = HutaoStatisticsClient()

    private var strategies: Map<String, HutaoAvatarStrategyData>? = null
    private var fetched = false

    private suspend fun ensureFetched() {
        if (fetched) {
            return
        }

        //先取数据再置位:置位必须放在成功之后,
        //否则首次请求因网络抖动失败后fetched已为true,本安装内永久不再重试
        val data = try {
            client.getAvatarStrategies()?.data
        } catch (e: Exception) {
            null
        }

        if (data != null) {
            strategies = data
            fetched = true
        }
    }

    /*
    * 获取角色攻略页链接
    *
    * avatarName:角色名,用于退化为攻略站直链
    *
    * 接口可用时返回米游社精确攻略帖,否则返回B站原神Wiki的该角色攻略页。
    * 两者都是普通网页,交由系统浏览器打开。
    * */
    suspend fun getStrategyUrl(avatarId: Int, avatarName: String): String? {
        ensureFetched()

        val strategyId = strategies?.get("$avatarId")?.mys_strategy_id

        if (strategyId != null && strategyId > 0) {
            return "https://bbs.mihoyo.com/ys/strategy/channel/map/39/$strategyId?bbs_presentation_style=no_header"
        }

        //接口不可用时退化为Wiki直链(实测该站点对中文角色名返回200)
        if (avatarName.isBlank()) {
            return null
        }

        val encodedName = try {
            java.net.URLEncoder.encode(avatarName, "UTF-8")
        } catch (e: Exception) {
            return null
        }

        return "https://wiki.biligame.com/ys/$encodedName/攻略"
    }
}
