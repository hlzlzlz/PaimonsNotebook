package com.lianyi.paimonsnotebook.common.web.hoyolab.takumi.game_record.act_calendar

import com.lianyi.paimonsnotebook.common.web.hoyolab.takumi.game_record.GameRecordDateTime

/*
* 米游社活动与卡池日历(act_calendar,POST)
* status: 1即将开始 2进行中
* countdown_seconds为服务器计算的剩余秒数,直接信任展示
* */
data class ActCalendarData(
    val avatar_card_pool_list: List<CardPool>,
    val weapon_card_pool_list: List<CardPool>,
    val mixed_card_pool_list: List<CardPool>,
    val selected_avatar_card_pool_list: List<CardPool>,
    val selected_mixed_card_pool_list: List<CardPool>,
    val act_list: List<Act>,
    val fixed_act_list: List<Act>,
    val selected_act_list: List<Act>
) {

    data class CardPool(
        val pool_id: Int,
        val version_name: String,
        val pool_name: String,
        val pool_type: Int,
        val avatars: List<PoolItem>,
        val weapon: List<PoolItem>,
        val start_timestamp: String,
        val start_time: GameRecordDateTime?,
        val end_timestamp: String,
        val end_time: GameRecordDateTime?,
        val jump_url: String,
        val pool_status: Int,
        val countdown_seconds: Long
    )

    data class PoolItem(
        val id: Int,
        val icon: String,
        val name: String,
        val element: String = "",
        val rarity: Int = 5,
        val is_invisible: Boolean = false,
        val wiki_url: String = ""
    )

    data class Act(
        val id: Int,
        val name: String,
        val type: String,
        val start_timestamp: String,
        val start_time: GameRecordDateTime?,
        val end_timestamp: String,
        val end_time: GameRecordDateTime?,
        val desc: String,
        val strategy: String,
        val countdown_seconds: Long,
        val status: Int,
        val reward_list: List<Any>,
        val is_finished: Boolean
    )
}
