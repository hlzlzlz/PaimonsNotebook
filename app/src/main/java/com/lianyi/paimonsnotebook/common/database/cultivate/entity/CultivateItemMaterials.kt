package com.lianyi.paimonsnotebook.common.database.cultivate.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Ignore
import com.lianyi.paimonsnotebook.ui.theme.Black
import com.lianyi.paimonsnotebook.ui.theme.Success
import com.lianyi.paimonsnotebook.ui.theme.Warning

/*
* 养成计划物品所需材料表
*
* 使用计划表与材料表的主键作为复合主键
* 删除时一同执行删除
* */
@Entity(
    "cultivate_item_materials",
    foreignKeys = [
        ForeignKey(
            entity = CultivateItems::class,
            parentColumns = arrayOf("item_id", "project_id"),
            childColumns = arrayOf("cultivate_item_id", "project_id"),
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ],
    /*
    * 使用材料的item_id与计算的实体id与计划id组合成一个唯一的主键
    * */
    primaryKeys = [
        "item_id",
        "cultivate_item_id",
        "project_id"
    ]
)
data class CultivateItemMaterials(
    @ColumnInfo("item_id")
    val itemId: Int, //材料id
    @ColumnInfo("cultivate_item_id")
    val cultivateItemId: Int, //所属计算物品的id
    @ColumnInfo("project_id")
    val projectId: Int, //计划id
    val count: Int, //所需数量
    @ColumnInfo("lack_count")
    val lackCount: Int, //缺少数量
    /*
    * 玩家实际持有数量
    *
    * 服务端 batch_compute 在有 uid/region 时返回 num(需要总数)与 lack_num(缺少数),
    * 二者相减即玩家实际持有 —— 与胡桃工具箱 InventoryItem 的算法一致
    * (InventoryService.cs: (int)item.Num - item.LackNum)。
    *
    * 原先 PN 只存 count/lackCount,把"持有数"这条信息丢掉了:用户无法一眼看出
    * "这个材料我有多少",只能靠缺少数反推。
    *
    * 默认 -1 表示未知(服务端未返回库存,如 has_user_info=false 时),
    * UI 据此隐藏该行,而不是显示"持有 0"这种误导性文案。
    * */
    @ColumnInfo("owned_count", defaultValue = "-1")
    val ownedCount: Int = OWNED_COUNT_UNKNOWN,
    val status: Int //状态
) {

    companion object {
        //持有数未知(服务端未返回库存信息)
        const val OWNED_COUNT_UNKNOWN = -1
    }

    /*
    * 用于临时存储完成状态
    * 避免UI与数据库状态不一致
    * */
    @Ignore
    var tempStatus = status
        private set

    //1完成,0未完成
    @Ignore
    var isFinish: Boolean = tempStatus == 1
        private set

    fun switchTempStatus() {
        tempStatus = if (isFinish) 0 else 1
        isFinish = tempStatus == 1
    }

    fun getShowContentAndColor(showLackNum: Boolean) =
        if (showLackNum && isFinish) {
            "完成" to Success
        } else if (showLackNum && lackCount >= 0) {
            "$lackCount" to Warning
        } else if (showLackNum) {
            "$count" to Warning
        } else {
            "$count" to Black
        }

    /*
    * 持有数文案,形如 "持有 123"
    *
    * 未知(服务端未返回库存)时返回 null,由 UI 隐藏该行 ——
    * 显示"持有 0"会让用户以为材料真的一个都没有。
    *
    * 抽成纯函数便于单测:它同时被普通材料行与武器材料行复用。
    * */
    fun getOwnedCountText(): String? =
        if (ownedCount == OWNED_COUNT_UNKNOWN) {
            null
        } else {
            "持有 $ownedCount"
        }
}
