package com.lianyi.paimonsnotebook.common.util.hoyolab.strategy

import com.lianyi.paimonsnotebook.common.web.hutao.statistics.HutaoAvatarStrategyData
import com.lianyi.paimonsnotebook.common.web.hutao.statistics.HutaoStatisticsClient

/*
* 角色攻略链接
* 数据来源于胡桃API的strategy接口,缓存于内存
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

    //获取米游社攻略页链接 无攻略时返回null
    suspend fun getMysStrategyUrl(avatarId: Int): String? {
        ensureFetched()

        val strategyId = strategies?.get("$avatarId")?.mys_strategy_id

        if (strategyId == null || strategyId <= 0) {
            return null
        }

        return "https://bbs.mihoyo.com/ys/strategy/channel/map/39/$strategyId?bbs_presentation_style=no_header"
    }
}
