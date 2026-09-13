package com.lianyi.paimonsnotebook.common.web.hoyolab.takumi.game_record.ledger

/*
* 旅行者札记 当月数据
* */
data class LedgerData(
    val uid: Int,
    val region: String,
    val nickname: String,
    val date: String,
    val month: Int,
    val optional_month: List<Int>,
    val day_data: DayData,
    val month_data: MonthData
) {
    data class DayData(
        val current_primogems: Int,
        val current_mora: Int,
        val last_primogems: Int,
        val last_mora: Int
    )

    data class MonthData(
        val current_primogems: Int,
        val current_mora: Int,
        val last_primogems: Int,
        val last_mora: Int,
        val current_primogems_level: Int,
        val primogems_rate: Int,
        val mora_rate: Int,
        val group_by: List<GroupBy>
    )

    data class GroupBy(
        val action_id: Int,
        val action: String,
        val num: Int,
        val percent: Int
    )
}
