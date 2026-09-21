package com.lianyi.paimonsnotebook.common.database.abyss.entity

import androidx.room.Entity
import androidx.room.Index

/*
* 深境螺旋 每期成绩快照
*
* 为什么需要:
*   深渊接口只能取**本期与上期**(schedule_type 1/2),再早的期数服务端不再提供。
*   用户若某期没打开深渊页,那期成绩就永久丢失,也无法做长期趋势。
*   因此每次成功拉取时落库一份 —— 越早升级积累越完整。
*
* 主键 (uid, schedule_id):
*   schedule_id 是服务端给的期数编号,天然唯一且稳定,
*   与"第几期"一一对应,因此同 (uid, 期数) 只会有一条。
*
* ⚠️ 新建表无需 defaultValue(同 5->6 / 6->7 的先例)。
* */
@Entity(
    "abyss_season_snapshots",
    primaryKeys = ["uid", "schedule_id"],
    indices = [Index("uid", name = "index_abyss_snapshot_uid")]
)
data class AbyssSeasonSnapshot(
    val uid: String,
    //服务端期数编号
    val schedule_id: Int,
    //本期开始/结束时间(字符串,原样保留便于展示与排序)
    val start_time: String,
    val end_time: String,
    //总星数
    val total_star: Int,
    //最高到达层数(字符串,接口给的是 "12-3" 这类)
    val max_floor: String,
    //战斗次数与胜场
    val total_battle_times: Int,
    val total_win_times: Int,
    /*
    * 各层星数,序列化为 "index:star:maxStar|..." 
    * 不建子表:层数固定(12 层)、始终整读整写。
    * */
    val floor_stars: String,
    //快照时间(本地毫秒)
    val saved_at: Long
)
