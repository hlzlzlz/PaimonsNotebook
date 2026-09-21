package com.lianyi.paimonsnotebook.common.database.abyss.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lianyi.paimonsnotebook.common.database.abyss.entity.AbyssSeasonSnapshot
import kotlinx.coroutines.flow.Flow

@Dao
interface AbyssSeasonSnapshotDao {

    /*
    * 写入快照(同 uid + 期数则覆盖)
    *
    * 为什么这里用 REPLACE 而札记快照用 IGNORE:
    *   札记的 month 数据是**月终结算值**,重复写没有信息增量,故按首次保留。
    *   而深渊的"本期"在期内持续变化(今天 6 星、周末补到 9 星),
    *   后写才是更新的进度。对已结束的历史期数,数据不再变化,覆盖无副作用。
    * */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(snapshot: AbyssSeasonSnapshot)

    //某 uid 的全部期数快照,新的在前
    @Query("SELECT * FROM abyss_season_snapshots WHERE uid = :uid ORDER BY schedule_id DESC")
    fun getSnapshotsByUid(uid: String): Flow<List<AbyssSeasonSnapshot>>

    //只取已经结束的期数(用于趋势统计,排除进行中的本期)
    @Query("SELECT * FROM abyss_season_snapshots WHERE uid = :uid ORDER BY schedule_id DESC LIMIT :limit")
    suspend fun getRecentSnapshots(uid: String, limit: Int): List<AbyssSeasonSnapshot>

    @Query("DELETE FROM abyss_season_snapshots WHERE uid = :uid")
    suspend fun deleteByUid(uid: String)
}
