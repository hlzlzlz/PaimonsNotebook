package com.lianyi.paimonsnotebook.common.database.gacha.entity

import androidx.room.Entity
import androidx.room.Index

/*
* 千星奇域(UGC)祈愿记录实体表
*
* 为什么不复用 gacha_items:
* 两边的字段集**本质不同**,硬塞进一张表会同时破坏已有逻辑与数据语义:
*   gacha_items     : count / gacha_type / uigf_gacha_type / name / ...
*   beyond_gacha_items: schedule_id / item_name / op_gacha_type / is_up / ...
* 具体冲突点:
*   1. gacha_items 的 uigf_gacha_type 是保底统计的分组键(100/200/301/302/500),
*      而千星奇域的 op_gacha_type 是 1000/2000/20011/20012/20021/20022,
*      混入后会污染 GachaRecordService 里按 uigf_gacha_type 分组的 SQL;
*   2. UIGF v4.2 规范本身就把 hk4e_ugc 与 hk4e 定义为**两个独立顶层数组**;
*   3. 参考实现(胡桃)也是独立表 beyond_gacha_items。
*
* 因此独立建表,与胡桃保持一致的建模。
*
* 字段类型全部为 String:与 UIGF v4.2 官方 schema 对 hk4e_ugc 的定义一致
* (id/schedule_id/item_id/rank_type/op_gacha_type 在规范里都是 string 且带
* ^[0-9]+$ pattern),也避免服务端数字/字符串混用时解析失败
* (详见 BeyondGachaLogItem 里对公告事故的说明)。
* */
@Entity(
    "beyond_gacha_items",
    //与 gacha_items 一样按 uid + 类型建索引,便于按卡池类型查询
    indices = [
        Index(
            "op_gacha_type",
            "uid",
            name = "index_beyond_gacha_items_type"
        )
    ],
    primaryKeys = ["id", "uid"]
)
data class BeyondGachaItems(
    val id: String,
    val uid: String,
    //服务器区域(如 cn_gf01),导出时用于推断时区
    val region: String,
    //卡池排期 ID
    val schedule_id: String,
    val item_type: String,
    val item_id: String,
    val item_name: String,
    val rank_type: String,
    //是否 UP 物品(0/1);UIGF 规范不导出该字段,仅本地保留
    val is_up: String,
    val time: String,
    //实际抽到的卡池类型(1000/2000/20011/...)
    val op_gacha_type: String,
    val lang: String
)
