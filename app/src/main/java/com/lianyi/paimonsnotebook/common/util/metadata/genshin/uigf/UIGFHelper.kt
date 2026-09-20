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
    * v4.2 比 v4.1 多一个顶层 hk4e_ugc 数组(千星奇域)。本项目**已采集**
    * 千星奇域祈愿数据(见 BeyondGachaLogService),导出 4.2 时会写入真实记录;
    * 该 uid 没有千星奇域记录时不写该字段 —— 符合规范里
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
            description = "最新标准,新增千星奇域字段(导出时含已获取的千星奇域记录)"
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

    /*
    * 千星奇域(UGC)卡池类型码
    *
    * 与上面 100~500 那套**不是同一套编码**,是服务端 op_gacha_type 的独立枚举
    * (来源:胡桃 GachaType.cs 的 UGC 段 + UIGF v4.2 官方 schema 的
    *  op_gacha_type enum,两处一致)。
    *
    *   1000  常驻(对应胡桃 UGCStandard)
    *   2000  角色活动(UGCAvatarEventWish)
    *   20011/20012  男性主角活动卡池(一/二)
    *   20021/20022  女性主角活动卡池(一/二)
    *
    * 拉取记录时只需查 1000 与 2000 —— 胡桃 BeyondGachaLog.QueryTypes 也只列
    * 这两个:20011/20012/20021/20022 是 2000 的"细分档位",服务端在
    * 2000 里会一并返回(胡桃 GachaConfigTypeExtension 把 20011~20022 归一化
    * 到 UGCAvatarEventWish 就是证据)。实测 6 个类型码都能返回 retcode 0。
    * */
    object BeyondGachaType {
        const val STANDARD = "1000"
        const val AVATAR_EVENT = "2000"
        const val ACTIVITY_AVATAR_MALE_ONE = "20011"
        const val ACTIVITY_AVATAR_MALE_TWO = "20012"
        const val ACTIVITY_AVATAR_FEMALE_ONE = "20021"
        const val ACTIVITY_AVATAR_FEMALE_TWO = "20022"

        //实际用于拉取的类型(与胡桃 BeyondGachaLog.QueryTypes 一致)
        val queryList = arrayOf(STANDARD, AVATAR_EVENT)

        //全部已知类型,用于名称映射
        val all = arrayOf(
            STANDARD,
            AVATAR_EVENT,
            ACTIVITY_AVATAR_MALE_ONE,
            ACTIVITY_AVATAR_MALE_TWO,
            ACTIVITY_AVATAR_FEMALE_ONE,
            ACTIVITY_AVATAR_FEMALE_TWO
        )
    }

    //千星奇域卡池名称
    fun getBeyondGachaName(type: String) = when (type) {
        BeyondGachaType.STANDARD -> "千星奇域·常驻"
        BeyondGachaType.AVATAR_EVENT -> "千星奇域·角色活动"
        BeyondGachaType.ACTIVITY_AVATAR_MALE_ONE -> "千星奇域·活动(男主一)"
        BeyondGachaType.ACTIVITY_AVATAR_MALE_TWO -> "千星奇域·活动(男主二)"
        BeyondGachaType.ACTIVITY_AVATAR_FEMALE_ONE -> "千星奇域·活动(女主一)"
        BeyondGachaType.ACTIVITY_AVATAR_FEMALE_TWO -> "千星奇域·活动(女主二)"
        else -> type
    }


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

        /*
        * 千星奇域(hk4e_ugc)条目**独有**的字段
        *
        * 与 hk4e 条目的差异(依据 UIGF v4.2 官方 schema 的 hk4e_ugc 段):
        *   少了:uigf_gacha_type / gacha_type / count
        *   多了:schedule_id / op_gacha_type
        *   名称字段叫 item_name,而不是 name
        * */
        object Beyond {
            const val ScheduleId = "schedule_id"
            const val ItemName = "item_name"
            const val OpGachaType = "op_gacha_type"

            /*
            * is_up 不在 UIGF 规范里(规范只要求上面 8 个字段),
            * 但服务端会返回、本地也保留,导入时若存在则读入。
            * */
            const val IsUp = "is_up"

            //规范里 hk4e_ugc 条目要求全部 8 个字段(其中 5 个与 hk4e 同名,复用 Item 的定义)
            val requiredFields = arrayOf(
                Item.Id,
                ScheduleId,
                Item.ItemType,
                Item.ItemId,
                ItemName,
                Item.RankType,
                Item.Time,
                OpGachaType
            )
        }
    }

}