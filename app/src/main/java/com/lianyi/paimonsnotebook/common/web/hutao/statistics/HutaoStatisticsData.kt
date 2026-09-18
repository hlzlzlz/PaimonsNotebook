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

/*
* 角色配装统计(data数组的一项,每个被统计的角色一条)
*
* 实测端点 /Statistics/Avatar/AvatarCollocation?Last=false,76 条:
*   Avatars      -> 与该角色同队的角色Id及占比(Rate为0~1)
*   Weapons      -> 该角色所持武器Id及占比
*   Reliquaries  -> 该角色所穿圣遗物,Item形如 "2150321-4"
*
* 注:字段为帕斯卡命名,与C#服务端一致,故不用@SerializedName
* */
data class HutaoAvatarCollocationData(
    val AvatarId: Int,
    val Avatars: List<Rate>,
    val Weapons: List<Rate>,
    val Reliquaries: List<ReliquaryRate>
) {
    //同队角色 / 所持武器
    data class Rate(
        val Item: Int,
        val Rate: Double
    )

    //Item为"套装Id-件数"字符串,例如 "2150321-4" 表示套装2150321穿4件
    data class ReliquaryRate(
        val Item: String,
        val Rate: Double
    )
}

/*
* 武器配队统计(data数组的一项,每把被统计的武器一条)
*
* 实测端点 /Statistics/Weapon/WeaponCollocation?Last=false,151 条;
* 每条的 Avatars 是"使用该武器的角色占比"(Rate为0~1)。
*
* Item是角色Id,需经元数据映射成名称/图标。
* */
data class HutaoWeaponCollocationData(
    val WeaponId: Int,
    val Avatars: List<Rate>
) {
    data class Rate(
        val Item: Int,
        val Rate: Double
    )
}
