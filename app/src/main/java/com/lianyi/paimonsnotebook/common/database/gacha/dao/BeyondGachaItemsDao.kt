package com.lianyi.paimonsnotebook.common.database.gacha.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lianyi.paimonsnotebook.common.database.gacha.entity.BeyondGachaItems
import kotlinx.coroutines.flow.Flow

/*
* 千星奇域(UGC)祈愿记录 DAO
*
* 与 GachaItemsDao 分表,故此处只放 UGC 需要的查询。
* */
@Dao
interface BeyondGachaItemsDao {

    //插入数据,主键重复则替换
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(list: List<BeyondGachaItems>)

    //根据UID删除记录
    @Query("delete from beyond_gacha_items where uid = :uid")
    fun deleteByUid(uid: String)

    //根据UID查询记录
    @Query("select * from beyond_gacha_items where uid = :uid order by id")
    fun getByUid(uid: String): List<BeyondGachaItems>

    @Query("select * from beyond_gacha_items where uid = :uid order by id")
    fun getByUidFlow(uid: String): Flow<List<BeyondGachaItems>>

    /*
    * 分页查询(导出时按页取,避免一次性载入全部记录)
    * page:页码,从0开始
    * */
    @Query("select * from beyond_gacha_items where uid = :uid order by id limit :pageSize offset :pageSize * :page")
    fun getByUidPage(uid: String, page: Int, pageSize: Int): List<BeyondGachaItems>

    //获取卡池记录中出现的UID
    @Query("select uid from beyond_gacha_items group by uid")
    fun getAllGameUidFlow(): Flow<List<String>>

    //按 uid 与卡池类型取最后一条记录ID(拉取时判断是否已到本地已有记录处)
    @Query("select id from beyond_gacha_items where uid = :uid and op_gacha_type = :gachaType order by id desc limit 1")
    fun getLastIdByUidAndGachaType(uid: String, gachaType: String): String?

    //记录条数
    @Query("select count(id) from beyond_gacha_items where uid = :uid")
    fun getCountByUid(uid: String): Int

    @Query("select count(id) from beyond_gacha_items")
    fun getSize(): Flow<Int>

    /*
    * 通知room表更新
    *
    * 与 GachaItemsDao 同样的手法:更新一个恒真的表达式来触发 Room 通知,
    * 因为直接插入不会让已订阅的 Flow 发出新值。
    * */
    @Query("update beyond_gacha_items set lang = lang where '-1' = '-1'")
    fun notifyRoomUpdate()
}
