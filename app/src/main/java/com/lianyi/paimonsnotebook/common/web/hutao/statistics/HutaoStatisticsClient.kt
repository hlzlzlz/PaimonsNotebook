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
        //主域名与备用域名
        private val hosts = listOf(
            "https://homa.snaphutaorp.org",
            "https://api.snaphutaorp.org"
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
