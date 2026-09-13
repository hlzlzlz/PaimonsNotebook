package com.lianyi.paimonsnotebook.common.web.hoyolab.takumi.game_record.role_combat

import com.lianyi.paimonsnotebook.common.web.hoyolab.takumi.game_record.GameRecordDateTime

/*
* 幻想真境剧诗
* */
data class RoleCombatData(
    val data: List<RoleCombatDataEntry>?,
    val is_unlock: Boolean
) {
    data class RoleCombatDataEntry(
        val detail: Detail?,
        val stat: Stat?,
        val schedule: Schedule,
        val has_data: Boolean,
        val has_detail_data: Boolean
    )

    data class Schedule(
        val start_time: Long,
        val end_time: Long,
        val schedule_type: Int,
        val schedule_id: Int,
        val start_date_time: GameRecordDateTime?,
        val end_date_time: GameRecordDateTime?
    )

    data class Stat(
        val difficulty_id: Int,
        val max_round_id: Int,
        val heraldry: Int,
        val get_medal_round_list: List<Int>,
        val medal_num: Int,
        val coin_num: Int,
        val avatar_bonus_num: Int,
        val rent_cnt: Int,
        val tarot_finished_cnt: Int
    )

    data class Detail(
        val rounds_data: List<RoundData>,
        val detail_stat: Stat?,
        val lineup_link: String,
        val backup_avatars: List<Avatar>,
        val fight_statisic: FightStatistic?
    )

    data class RoundData(
        val avatars: List<Avatar>,
        val choice_cards: List<Buff>,
        val buffs: List<Buff>,
        val is_get_medal: Boolean,
        val round_id: Int,
        val finish_time: Long,
        val finish_date_time: GameRecordDateTime?,
        val enemies: List<Enemy>,
        val is_tarot: Boolean,
        val tarot_serial_no: Int
    )

    data class Avatar(
        val avatar_id: Int,
        val avatar_type: Int,
        val name: String,
        val image: String,
        val level: Int,
        val rarity: Int
    )

    data class Buff(
        val icon: String,
        val name: String,
        val desc: String,
        val is_enhanced: Boolean,
        val id: Int
    )

    data class Enemy(
        val name: String,
        val icon: String,
        val level: Int
    )

    data class FightStatistic(
        val max_defeat_avatar: AvatarStatistics?,
        val max_damage_avatar: AvatarStatistics?,
        val max_take_damage_avatar: AvatarStatistics?,
        val total_coin_consumed: AvatarStatistics?,
        val shortest_avatar_list: List<AvatarStatistics>,
        val total_use_time: Int,
        val is_show_battle_stats: Boolean
    )

    data class AvatarStatistics(
        val avatar_id: Int,
        val avatar_icon: String,
        val value: String,
        val rarity: Int
    )
}
