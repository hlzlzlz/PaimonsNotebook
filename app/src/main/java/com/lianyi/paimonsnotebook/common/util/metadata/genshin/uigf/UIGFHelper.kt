package com.lianyi.paimonsnotebook.common.util.metadata.genshin.uigf

object UIGFHelper {

    const val UIGF_HOME_PAGE = "https://uigf.org/"

    /*
    * 支持的 UIGF 版本
    *
    * UIGF 官方规范(uigf.org/zh/standards/uigf.html)的版本说明:
    *   v4.0  合并 SRGF,新增绝区零抽卡格式支持
    *   v4.1  新增对星穹铁道 v3.4 新卡池类型的支持  —— 兼容 v4.1/v4.0*
    *   v4.2  新增对于千星奇域的支持                —— 兼容 v4.1
    * 官方原话:"对于无需处理星穹铁道的应用,v4.1 与 v4.0 兼容。"
    *
    * 本项目只做原神,不涉及星穹铁道/绝区零,所以 v4.1 与 v4.0 的**原神部分
    * 完全同构**,唯一差异是 info.version 字符串(这与胡桃的实现一致:
    * 它的 UIGF41ExportService 只是继承 40 并把 Version 改成 "v4.1")。
    *
    * v4.2 比 v4.1 多一个顶层 hk4e_ugc 数组(千星奇域)。本项目目前不采集
    * 千星奇域祈愿数据,故导出 4.2 时该字段输出为空数组 —— 符合规范里
    * "导出方可以选择性地填充针对每个游戏的字段或直接忽略"。
    * */
    enum class UIGFVersion(val value: String, val label: String, val description: String) {
        V4_0(
            value = "v4.0",
            label = "UIGF v4.0",
            description = "兼容性最好,支持绝大多数第三方工具"
        ),
        V4_1(
            value = "v4.1",
            label = "UIGF v4.1",
            description = "相比 v4.0 仅新增星穹铁道卡池支持;原神部分与 v4.0 完全相同"
        ),
        V4_2(
            value = "v4.2",
            label = "UIGF v4.2",
            description = "最新标准,新增千星奇域字段(本应用暂不采集该数据,该字段将导出为空)"
        );

        companion object {
            val default = V4_0

            //按版本字符串查找(用于解出已保存的设置值)
            fun fromValue(value: String?): UIGFVersion =
                entries.firstOrNull { it.value == value } ?: default
        }
    }

    //当前默认导出的版本(保留旧常量名以兼容既有引用)
    const val UIGF_VERSION = "v4.0"

    private const val NOVICE_WISH = "100"
    private const val PERMANENT_WISH = "200"
    private const val AVATAR_WISH_1 = "301"
    private const val AVATAR_WISH_2 = "400"
    private const val WEAPON_WISH = "302"

    //集录祈愿的类型码,公开以供保底计算等模块引用,避免各自硬编码造成漂移
    const val CHRONICLED_WISH = "500"


    //当更新祈愿卡池类型时,需要在此处同步添加
    val gachaList = arrayOf(
        NOVICE_WISH,
        PERMANENT_WISH,
        AVATAR_WISH_1,
        AVATAR_WISH_2,
        WEAPON_WISH,
        CHRONICLED_WISH
    )

    val uigfGachaTypeCount: Int
        get() = gachaList.size

    fun getUIGFType(type: String) = when (type) {
        AVATAR_WISH_1, AVATAR_WISH_2 -> AVATAR_WISH_1
        else -> type
    }

    //返回UIGF名称,未知时直接返回类型
    fun getUIGFName(type: String) = when (type) {
        NOVICE_WISH -> "新手祈愿"
        AVATAR_WISH_1, AVATAR_WISH_2 -> "角色活动"
        WEAPON_WISH -> "神铸赋形"
        PERMANENT_WISH -> "奔行世间"
        CHRONICLED_WISH -> "集录祈愿"
        else -> type
    }

    //根据uid获取时区
    fun getRegionTimeZoneByUid(uid: String) =
        when (uid.first()) {
            '6' -> -5L //os_usa
            '7' -> 1L //os_euro
            else -> 8L //os_cht, os_asia, cn_gf01, cn_qd01
        }

    object ItemType {
        const val Weapon = "武器"
        const val Avatar = "角色"
    }

    /*
    * 顶层游戏字段名
    *
    * hk4e = 原神;v4.2 起另有 hk4e_ugc(千星奇域)
    * */
    object GameField {
        const val Hk4e = "hk4e"
        const val Hk4eUgc = "hk4e_ugc"
    }

    //UIGF 字段
    object Field {
        object Info {
            const val Uid = "uid"
            const val Lang = "lang"
            const val ExportTimestamp = "export_timestamp"
            const val ExportTime = "export_time"
            const val ExportApp = "export_app"
            const val ExportAppVersion = "export_app_version"
            const val UIGFVersion = "uigf_version"
            const val Version = "version"
            const val RegionTimeZone = "region_time_zone"
            const val TimeZone = "timezone"

            //必须有的字段
            val requiredFields = arrayOf(
                Uid,
                UIGFVersion,
            )

            val requiredFieldsV4 = arrayOf(
                ExportTimestamp,
                ExportApp,
                ExportAppVersion,
                Version
            )

//            val fields = arrayOf(
//                Uid,
//                Lang,
//                ExportTimestamp,
//                ExportTime,
//                ExportApp,
//                ExportAppVersion,
//                UIGFVersion,
//                RegionTimeZone //时区不是必须有的
//            )
        }

        object Item {
            const val UigfGachaType = "uigf_gacha_type"
            const val GachaType = "gacha_type"
            const val ItemId = "item_id"
            const val Count = "count"
            const val Time = "time"
            const val Name = "name"
            const val ItemType = "item_type"
            const val RankType = "rank_type"
            const val Id = "id"

            val requiredFields = arrayOf(
                UigfGachaType,
                GachaType,
                ItemId,
                Id,
                Time
            )
//
//            val fields = arrayOf(
//                UigfGachaType,
//                GachaType,
//                ItemId,
//                Count,
//                Time,
//                Name,
//                ItemType,
//                RankType,
//                Id
//            )
        }
    }

}