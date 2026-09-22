package com.lianyi.paimonsnotebook.common.database.ledger.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lianyi.paimonsnotebook.common.database.ledger.entity.LedgerMonthSnapshot
import kotlinx.coroutines.flow.Flow

@Dao
interface LedgerMonthSnapshotDao {

    /*
    * 按首次成功保留(IGNORE)
    *
    * ⚠️ 主键是 (uid, year, month) —— **year 必须参与去重**,
    *    否则去年 12 月与今年 12 月会被当成同一条,今年的数据被静默丢弃。
    *    (1.8.18 前主键漏了 year,已由 8->9 迁移修正。)
    * */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(snapshot: LedgerMonthSnapshot)

    //某 uid 的全部历史快照,近的月份在前
    @Query("SELECT * FROM ledger_month_snapshots WHERE uid = :uid ORDER BY year DESC, month DESC")
    fun getSnapshotsByUid(uid: String): Flow<List<LedgerMonthSnapshot>>

    @Query("SELECT COUNT(*) FROM ledger_month_snapshots WHERE uid = :uid")
    suspend fun countByUid(uid: String): Int

    /*
    * 删除指定 uid 的快照
    *
    * ⚠️ 当前 AccountHelper.deleteUser 不调用它(账号删除时无法枚举其游戏 uid,
    * 见该方法内注释)。保留此方法供后续"数据管理"类入口使用。
    * */
    @Query("DELETE FROM ledger_month_snapshots WHERE uid = :uid")
    suspend fun deleteByUid(uid: String)
}
