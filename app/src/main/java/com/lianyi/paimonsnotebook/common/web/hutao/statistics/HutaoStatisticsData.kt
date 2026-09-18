package com.lianyi.paimonsnotebook.common.web.hutao.statistics

/*
* 胡桃API 全服统计数据
* 注意:响应JSON字段为帕斯卡命名,与C#服务端一致
* */

//胡桃API响应信封 retcode为0时成功
data class HutaoResponseData<T>(
    val retcode: Int,
    val message: String,
    val data: T?
)

//深渊总览
data class HutaoOverviewData(
    val ScheduleId: Int,
    val RecordTotal: Int,
    val SpiralAbyssTotal: Int,
    val SpiralAbyssPassed: Int,
    val SpiralAbyssStarTotal: Long,
    val SpiralAbyssFullStar: Int,
    val SpiralAbyssBattleTotal: Long,
    val Timestamp: Long,
    val TimeTotal: Double,
    val TimeAverage: Double
)

//按楼层的角色比率列表 Item为角色Id
data class HutaoAvatarFloorRateData(
    val Floor: Int,
    val Ranks: List<Rate>
) {
    data class Rate(
        val Item: Int,
        val Rate: Double
    )
}

//配队 Item为逗号分隔的角色Id串
data class HutaoTeamCombinationData(
    val Floor: Int,
    val Up: List<Rate>,
    val Down: List<Rate>
) {
    data class Rate(
        val Item: String,
        val Rate: Double
    )
}

//持有率与命座分布
data class HutaoHoldingRateData(
    val HoldingRate: Double,
    val Constellations: List<Rate>,
    val AvatarId: Int = 0
) {
    data class Rate(
        val Item: Int,
        val Rate: Double
    )
}

//本期持有率与上期环比的连接条目(客户端本地计算,非服务端响应)
data class HutaoHoldingRateEntry(
    val AvatarId: Int,
    val HoldingRate: Double,
    //与上期的差值(小数),无上期数据时为null
    val HoldingDelta: Double?,
    val Constellations: List<HutaoHoldingRateData.Rate>,
    //命座持有率环比,与Constellations同序
    val ConstellationDeltas: List<Double?>
)

//剧诗统计
data class HutaoRoleCombatStatisticsData(
    val ScheduleId: Int,
    val RecordTotal: Int,
    val Timestamp: Long,
    val BackupAvatarRates: List<Rate>
) {
    data class Rate(
        val Item: Int,
        val Rate: Double
    )
}
