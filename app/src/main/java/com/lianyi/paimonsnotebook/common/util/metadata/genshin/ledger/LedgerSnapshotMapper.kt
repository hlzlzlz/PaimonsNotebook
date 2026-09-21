package com.lianyi.paimonsnotebook.common.util.metadata.genshin.ledger

import com.lianyi.paimonsnotebook.common.database.ledger.entity.LedgerMonthSnapshot
import com.lianyi.paimonsnotebook.common.web.hoyolab.takumi.game_record.ledger.LedgerData
import java.util.Calendar

/*
* 札记月度快照的构建
*
* 抽成纯函数以便单测 —— 年份推导是唯一有逻辑分支的地方:
* 接口的 month 只有 1~12 不带年份,而用户可以翻看**上一年**的月份
* (optional_month 通常含去年 12 月)。年份必须从"date 字段"推导,
* 不能用本地日历(设备时间不可信,且与数据归属年份可能不同)。
* */
object LedgerSnapshotMapper {

    /*
    * 从响应构建快照
    *
    * 年份推导规则:
    *   接口的 date 形如 "2025-09-01 00:00:00"(该月首日,服务端时区)。
    *   直接取它的年份 —— 这就是数据真正归属的年份。
    *   date 解析失败时回退到本地当前年份(保底,不应发生)。
    * */
    fun toSnapshot(data: LedgerData, savedAt: Long): LedgerMonthSnapshot {
        val monthData = data.month_data

        return LedgerMonthSnapshot(
            uid = data.uid.toString(),
            month = data.month,
            year = resolveYear(data),
            nickname = data.nickname,
            region = data.region,
            current_primogems = monthData.current_primogems,
            current_mora = monthData.current_mora,
            last_primogems = monthData.last_primogems,
            last_mora = monthData.last_mora,
            group_by = serializeGroupBy(monthData.group_by),
            saved_at = savedAt
        )
    }

    /*
    * 年份解析
    *
    * ⚠️ 只解析"年份"这一个字段,不构造 Calendar 完整日期 ——
    *    月首日不存在时区歧义问题,而完整解析反而会引入时区偏移风险。
    * */
    fun resolveYear(data: LedgerData): Int {
        //date 形如 "2025-09-01 00:00:00",取前 4 位
        data.date.trim().take(4).toIntOrNull()?.let { return it }

        //回退:本地年份
        return Calendar.getInstance().get(Calendar.YEAR)
    }

    /*
    * group_by 序列化
    *
    * 简单起见用行内 JSON(字段少且固定:action_id/action/num/percent),
    * 不为此建子表 —— 收支分类最多十来条,查询也总是整读整存。
    * */
    fun serializeGroupBy(list: List<LedgerData.GroupBy>): String =
        list.joinToString(separator = "|") { "${it.action_id},${it.action},${it.num},${it.percent}" }

    fun deserializeGroupBy(serialized: String): List<GroupBySnapshot> =
        serialized.split("|")
            .filter { it.isNotBlank() }
            .mapNotNull { entry ->
                val p = entry.split(",")
                if (p.size != 4) return@mapNotNull null
                GroupBySnapshot(
                    action_id = p[0].toIntOrNull() ?: 0,
                    action = p[1],
                    num = p[2].toLongOrNull() ?: 0L,
                    percent = p[3].toIntOrNull() ?: 0
                )
            }

    data class GroupBySnapshot(
        val action_id: Int,
        val action: String,
        val num: Long,
        val percent: Int
    )
}
