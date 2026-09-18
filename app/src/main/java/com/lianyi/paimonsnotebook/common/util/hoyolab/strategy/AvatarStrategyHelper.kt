package com.lianyi.paimonsnotebook.common.util.hoyolab.strategy

import java.net.URLEncoder

/*
* 角色攻略链接
*
* 直接返回B站原神Wiki的该角色攻略页。
*
* 历史:原实现先请求胡桃API的 /strategy/all 取"精确攻略帖ID",
* 取不到就返回 null,而调用方写的是 strategyUrl?.let { ... },
* 即按钮直接不渲染 —— 功能静默消失且无任何提示。
*
* 实测(2026-09-18)该路由在两个域上均已下线:
*   api.snaphutaorp.org/strategy/all   -> 404
*   api.hutaorp.org/strategy/all       -> 404
*   api.snaphutaorp.org/strategy/item  -> 404
*   api.hutaorp.org/strategy/item      -> 404
* 同域 git-repository/all -> 200,证明域是活的,属"路由不存在"而非鉴权/网络问题。
*
* 所以"先试接口、失败再退化"是纯浪费 —— 每次冷启动都会白白打一次必然404的请求。
* 现在把B站直链作为唯一路径,不再发该请求(对应方法已从 HutaoStatisticsClient 删除)。
*
* 胡桃工具箱自己的攻略命令也是这个结局:它走 /strategy 的
* ChineseStrategyCommand / OverseaStrategyCommand 同样取不到数据,
* 而它唯一可用的 BilibiliStrategyCommand 用的正是下面这个URL格式
* (见 WikiAvatarStrategyComponent.cs),与本实现一致。
* 详见 memory/hutao-comparison-round2.md 第3节。
* */
object AvatarStrategyHelper {

    //B站原神Wiki的攻略页前缀,实测对中文角色名返回200
    private const val BiliWikiStrategyPrefix = "https://wiki.biligame.com/ys/"

    //攻略子页名(需URL编码后再拼,否则URL里会带裸中文)
    private const val StrategySuffix = "攻略"

    /*
    * 获取角色攻略页链接
    *
    * avatarName:角色名,与其后的"攻略"子页名都会被URL编码
    *
    * 角色名为空时返回null(调用方不渲染按钮)。
    * 返回的是普通网页,交由系统浏览器打开。
    * */
    fun getStrategyUrl(avatarId: Int, avatarName: String): String? {
        //avatarId当前未参与拼URL(胡桃的精确攻略帖接口已下线),
        //保留形参以免调用方与胡桃侧签名脱钩,后续若接口恢复可在此接回
        if (avatarName.isBlank()) {
            return null
        }

        return try {
            //分段编码:斜杠必须保留为路径分隔符,不能一起编成%2F
            val encodedName = URLEncoder.encode(avatarName, "UTF-8")
            val encodedSuffix = URLEncoder.encode(StrategySuffix, "UTF-8")
            "$BiliWikiStrategyPrefix$encodedName/$encodedSuffix"
        } catch (e: Exception) {
            null
        }
    }
}
