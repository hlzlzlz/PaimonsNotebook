package com.lianyi.paimonsnotebook.common.util.metadata.genshin.abyss

import com.lianyi.paimonsnotebook.common.database.abyss.entity.AbyssSeasonSnapshot
import com.lianyi.paimonsnotebook.common.web.hoyolab.takumi.game_record.abyss.SpiralAbyssData

/*
* 深渊成绩快照的构建与展示整理
*
* 抽成纯函数以便单测:层星序列化与趋势计算都有容易写错的分支。
* */
object AbyssSnapshotMapper {

    /*
    * 从响应构建快照
    *
    * floors 的 index 是层号(9~12),star/max_star 是该层星数。
    * 序列化为 "index:star:maxStar",以 | 分隔。
    * */
    fun toSnapshot(
        data: SpiralAbyssData,
        uid: String,
        savedAt: Long
    ): AbyssSeasonSnapshot = AbyssSeasonSnapshot(
        uid = uid,
        schedule_id = data.schedule_id,
        start_time = data.start_time,
        end_time = data.end_time,
        total_star = data.total_star,
        max_floor = data.max_floor,
        total_battle_times = data.total_battle_times,
        total_win_times = data.total_win_times,
        floor_stars = serializeFloors(data.floors),
        saved_at = savedAt
    )

    fun serializeFloors(floors: List<SpiralAbyssData.Floor>): String =
        floors.joinToString("|") { "${it.index}:${it.star}:${it.max_star}" }

    data class FloorStar(
        val index: Int,
        val star: Int,
        val maxStar: Int
    )

    /*
    * 反序列化层星
    *
    * 容错:任一段格式不对就跳过该段(而不是整条记录作废)——
    * 持久化数据可能因历史版本变更而部分不兼容。
    * */
    fun deserializeFloors(serialized: String): List<FloorStar> =
        serialized.split("|")
            .filter { it.isNotBlank() }
            .mapNotNull { segment ->
                val p = segment.split(":")
                if (p.size != 3) return@mapNotNull null

                val index = p[0].toIntOrNull() ?: return@mapNotNull null
                val star = p[1].toIntOrNull() ?: return@mapNotNull null
                val maxStar = p[2].toIntOrNull() ?: return@mapNotNull null

                FloorStar(index = index, star = star, maxStar = maxStar)
            }
            .sortedByDescending { it.index }

    /*
    * 期数展示标签
    * 用开始时间的前 10 位(日期)作为期数标识 ——
    * schedule_id 是内部编号,对用户没有意义。
    * */
    fun seasonLabel(snapshot: AbyssSeasonSnapshot): String {
        val start = snapshot.start_time.take(10)
        return if (start.isBlank()) "第 ${snapshot.schedule_id} 期" else start
    }

    /*
    * 满星判定
    * 12 层每层 3 星 = 36 星为满。接口的 total_star 可能因版本变化,
    * 故以"是否等于历史最大值"之外,仍保留固定阈值判断供展示参考。
    * */
    const val FULL_STAR = 36

    fun isFullStar(totalStar: Int): Boolean = totalStar >= FULL_STAR

    /*
    * 最近几期的星数走势(用于简单趋势展示)
    * 输入为倒序(新在前),返回同样倒序的星数列表。
    * */
    fun starTrend(snapshots: List<AbyssSeasonSnapshot>, limit: Int): List<Int> =
        snapshots.take(limit).map { it.total_star }
}
