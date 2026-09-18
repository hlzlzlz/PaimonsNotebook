package com.lianyi.paimonsnotebook.common.web.hutao.statistics

import com.lianyi.paimonsnotebook.common.util.json.JSON
import com.lianyi.paimonsnotebook.common.util.parameter.getParameterizedType
import com.lianyi.paimonsnotebook.common.util.request.applicationOkHttpClient
import com.lianyi.paimonsnotebook.common.util.request.buildRequest
import com.lianyi.paimonsnotebook.common.util.request.getAsText
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

/*
* 胡桃API全服统计数据客户端(只读)
* 主域名失败时自动尝试备用域名
* */
class HutaoStatisticsClient {

    companion object {
        /*
        * 主域名与备用域名
        *
        * 注意:备用域必须是 homa.hutaorp.org,不能写 api.snaphutaorp.org。
        * 实测(2026-09-18)api.snaphutaorp.org 不服务 Statistics 系列路由:
        *   api.snaphutaorp.org/Statistics/Overview             -> 404
        *   api.snaphutaorp.org/Statistics/Avatar/HoldingRate   -> 404
        *   api.snaphutaorp.org/git-repository/all              -> 200(同域其它路由正常)
        * 即该域是活的,只是没有这批路由 —— 写在这里等于备用域永远404,
        * 主域一挂6个统计端点会全部返回空;而404被 getAsJson(永不抛异常)
        * 与 json.isBlank() 静默吞掉,所以一直没被发现。
        *
        * homa.hutaorp.org 是胡桃官方的主/备成对域(见其 Web/ServerDomain.cs:
        * snaphutaorp.org <-> hutaorp.org),实测6个端点在备用域上全部200,
        * 且响应字节数与主域逐一相同。
        * */
        private val hosts = listOf(
            "https://homa.snaphutaorp.org",
            "https://homa.hutaorp.org"
        )

        private const val StrategyHost = "https://api.snaphutaorp.org"

        private fun parameterized(raw: Class<*>, arg: Type): ParameterizedType =
            getParameterizedType(raw, arg)
    }

    //深渊总览
    suspend fun getOverview(last: Boolean = false): HutaoResponseData<HutaoOverviewData>? =
        get("/Statistics/Overview?Last=$last", HutaoOverviewData::class.java)

    //角色出场率
    suspend fun getAvatarAppearanceRate(last: Boolean = false): HutaoResponseData<List<HutaoAvatarFloorRateData>>? =
        getList("/Statistics/Avatar/AttendanceRate?Last=$last", HutaoAvatarFloorRateData::class.java)

    //角色使用率
    suspend fun getAvatarUsageRate(last: Boolean = false): HutaoResponseData<List<HutaoAvatarFloorRateData>>? =
        getList("/Statistics/Avatar/UtilizationRate?Last=$last", HutaoAvatarFloorRateData::class.java)

    //配队
    suspend fun getTeamCombination(last: Boolean = false): HutaoResponseData<List<HutaoTeamCombinationData>>? =
        getList("/Statistics/Team/Combination?Last=$last", HutaoTeamCombinationData::class.java)

    //持有率与命座分布
    suspend fun getHoldingRate(last: Boolean = false): HutaoResponseData<List<HutaoHoldingRateData>>? =
        getList("/Statistics/Avatar/HoldingRate?Last=$last", HutaoHoldingRateData::class.java)

    //剧诗统计
    suspend fun getRoleCombatStatistics(last: Boolean = false): HutaoResponseData<HutaoRoleCombatStatisticsData>? =
        get("/RoleCombat/Statistics?Last=$last", HutaoRoleCombatStatisticsData::class.java)

    //全角色攻略ID
    suspend fun getAvatarStrategies(): HutaoResponseData<Map<String, HutaoAvatarStrategyData>>? {
        val json = buildRequest {
            url("$StrategyHost/strategy/all")
        }.getAsText(applicationOkHttpClient)

        if (json.isBlank()) {
            return null
        }

        return try {
            JSON.parse<HutaoResponseData<Map<String, HutaoAvatarStrategyData>>>(
                json,
                parameterized(
                    HutaoResponseData::class.java,
                    parameterized(Map::class.java, String::class.java)
                )
            )
        } catch (e: Exception) {
            null
        }
    }

    //data为单个对象的请求
    private suspend fun <T : Any> get(
        pathWithQuery: String,
        dataClass: Class<T>
    ): HutaoResponseData<T>? {
        hosts.forEach { host ->
            val response = fetch("$host$pathWithQuery", dataClass)

            if (response != null) {
                return response
            }
        }

        return null
    }

    //data为集合的请求
    private suspend fun <T : Any> getList(
        pathWithQuery: String,
        itemClass: Class<T>
    ): HutaoResponseData<List<T>>? {
        hosts.forEach { host ->
            val responseType = parameterized(
                HutaoResponseData::class.java,
                getParameterizedType(List::class.java, itemClass)
            )

            val json = buildRequest {
                url(host + pathWithQuery)
            }.getAsText(applicationOkHttpClient)

            if (json.isBlank()) {
                return@forEach
            }

            try {
                val response = JSON.parse<HutaoResponseData<List<T>>>(json, responseType)

                if (response != null) {
                    return response
                }
            } catch (e: Exception) {
                return@forEach
            }
        }

        return null
    }

    private suspend fun <T : Any> fetch(
        url: String,
        dataClass: Class<T>
    ): HutaoResponseData<T>? {
        val json = buildRequest {
            url(url)
        }.getAsText(applicationOkHttpClient)

        if (json.isBlank()) {
            return null
        }

        return try {
            JSON.parse<HutaoResponseData<T>>(
                json,
                parameterized(HutaoResponseData::class.java, dataClass)
            )
        } catch (e: Exception) {
            null
        }
    }
}
