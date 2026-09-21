package com.lianyi.paimonsnotebook.common.database.ledger.entity

import androidx.room.Entity

/*
* 旅行者札记 月度收支快照
*
* 为什么存快照:
*   米游社的札记接口只保留近期月份(optional_month 通常只有 4 个月),
*   过期月份**无法再从服务端取回** —— 用户若长期不打开札记页,
*   当月数据就永久丢了。所以每次成功拉取某月数据时都落库一份,
*   越早开始存,历史越完整。
*
* 主键 (uid, month):一个月一条快照,重复拉取同月按**首次成功**保留。
*   不用"后写覆盖"的理由:month_data.last_* 是"上月同期"对照值,
*   会在月初与月末之间波动;首次拉到的是最接近真实月末值的快照,
*   而且重复写入没有信息增量。
*
* ⚠️ 新建表无需 defaultValue(AutoMigration 生成 CREATE TABLE 即可),
*    与 5->6 的 beyond_gacha_items 同理。
* */
@Entity("ledger_month_snapshots", primaryKeys = ["uid", "month"])
data class LedgerMonthSnapshot(
    //玩家 uid
    val uid: String,
    //月份,格式与接口一致:1~12(不存年份 —— 见下方说明)
    val month: Int,
    /*
    * 数据归属的年份。
    * 接口的 month 只有 1~12,不带年份;当前年份由"哪年拉到的"决定。
    * 跨年场景(12 月拉 1 月、1 月拉 12 月)必须显式记录,
    * 否则历史快照会与今年的 1 月撞主键。
    * */
    val year: Int,
    val nickname: String,
    val region: String,
    //当月原石
    val current_primogems: Int,
    //当月摩拉
    val current_mora: Int,
    //上月原石(接口给的对照值)
    val last_primogems: Int,
    //上月摩拉
    val last_mora: Int,
    //当月原石构成(group_by 的 JSON 序列化,见 GroupBySnapshot)
    val group_by: String,
    //快照时间(本地毫秒)
    val saved_at: Long
)
