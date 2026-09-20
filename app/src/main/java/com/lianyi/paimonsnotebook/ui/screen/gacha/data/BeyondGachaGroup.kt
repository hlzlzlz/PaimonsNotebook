package com.lianyi.paimonsnotebook.ui.screen.gacha.data

import com.lianyi.paimonsnotebook.common.database.gacha.entity.BeyondGachaItems
import com.lianyi.paimonsnotebook.common.util.metadata.genshin.uigf.UIGFHelper

/*
* 千星奇域祈愿记录的展示数据
*
* 按卡池类型(op_gacha_type)分组。与普通祈愿的展示有个本质差异:
* 千星奇域的物品**没有本地元数据**(Avatar.json/Weapon.json 里都没有它们),
* 所以拿不到图标 URL,只能靠记录自带的 item_name/rank_type 显示文字。
* 这也是为什么这个页面不做图标网格,而是列表。
* */
data class BeyondGachaGroup(
    val gachaType: String,
    val items: List<BeyondGachaItems>
) {
    val gachaTypeName: String
        get() = UIGFHelper.getBeyondGachaName(gachaType)

    val totalCount: Int
        get() = items.size

    //五星条数
    val star5Count: Int
        get() = items.count { it.rank_type == "5" }

    companion object {
        /*
        * 按卡池类型分组
        *
        * 排序依据 BeyondGachaType.all 的固定顺序,保证界面顺序稳定
        * (不能靠数据库返回顺序,否则不同设备可能不一致)。
        * */
        fun from(items: List<BeyondGachaItems>): List<BeyondGachaGroup> {
            val map = items.groupBy { it.op_gacha_type }

            return UIGFHelper.BeyondGachaType.all
                .mapNotNull { type ->
                    map[type]?.takeIf { it.isNotEmpty() }?.let { list ->
                        //时间倒序:最近的抽卡在最前
                        BeyondGachaGroup(
                            gachaType = type,
                            items = list.sortedByDescending { it.time }
                        )
                    }
                }
        }
    }
}
