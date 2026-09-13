package com.lianyi.paimonsnotebook.common.web.hoyolab.takumi.game_record.hard_challenge

import com.lianyi.paimonsnotebook.common.web.hoyolab.takumi.game_record.GameRecordDateTime

/*
* 幽境危战
* */
data class HardChallengeData(
    val data: List<Entry>?,
    val is_unlock: Boolean
) {
    data class Entry(
        val schedule: Schedule,
        val single: Single?,
        val blings: List<Bling>
    )

    data class Schedule(
        val schedule_id: Int,
        val start_time: Long,
        val end_time: Long,
        val start_date_time: GameRecordDateTime?,
        val end_date_time: GameRecordDateTime?,
        val is_valid: Boolean,
        val name: String
    )

    data class Single(
        val best: Best?,
        val challenge: List<Challenge>,
        val has_data: Boolean
    )

    data class Best(
        val difficulty: Int,
        val second: Int,
        val icon: String
    )

    data class Challenge(
        val name: String,
        val second: Int,
        val teams: List<SimpleAvatar>,
        val best_avatar: List<BestAvatar>,
        val monster: Monster
    )

    data class SimpleAvatar(
        val avatar_id: Int,
        val name: String,
        val element: String,
        val image: String,
        val rarity: Int,
        val level: Int,
        val rank: Int
    )

    data class BestAvatar(
        val avatar_id: Int,
        val side_icon: String,
        val dps: Int,
        val type: String
    )

    data class Monster(
        val name: String,
        val level: Int,
        val icon: String,
        val desc: List<String>,
        val monster_id: Int
    )

    data class Bling(
        val is_plus: Boolean,
        val side_icon: String
    )
}
