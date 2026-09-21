package com.lianyi.paimonsnotebook.common.util.metadata.genshin.ledger

import com.lianyi.paimonsnotebook.common.database.ledger.entity.LedgerMonthSnapshot

/*
* 札记历史列表的展示数据构建
*
* 抽成纯函数以便单测 —— 环比计算里有一条必须守住的分支:
* **月份不连续时不显示环比**。
*
* 原因:快照只在"用户实际打开过首页/札记页"时才产生,
* 中间停用几个月就会出现空档。若把空档两端直接相减,
* 会显示成一个巨大的跌幅(例如 8 月 12000 → 11 月 3000 被算成 -75%),
* 而真实情况是"10 月根本没记录"。这种误导性数字不如不显示。
* */
object LedgerHistoryFormatter {

    data class HistoryRow(
        //形如 "2025年9月"
        val label: String,
        val primogems: Int,
        val mora: Int,
        //与**紧邻上一月**的差值;月份不连续或没有上一月时为 null
        val primogemsDelta: Int?,
        val moraDelta: Int?
    )

    /*
    * 构建历史行
    *
    * 输入按 (year, month) 倒序(最新在前),与 DAO 的排序一致。
    * 环比取"列表中的下一条"——即时间上更早的那一月。
    * */
    fun buildRows(snapshots: List<LedgerMonthSnapshot>): List<HistoryRow> =
        snapshots.mapIndexed { index, snapshot ->
            val older = snapshots.getOrNull(index + 1)

            //只有恰好是"紧邻上一月"时才计算环比
            val adjacent = older?.takeIf {
                isPreviousMonth(
                    olderYear = it.year,
                    olderMonth = it.month,
                    newerYear = snapshot.year,
                    newerMonth = snapshot.month
                )
            }

            HistoryRow(
                label = monthLabel(snapshot.year, snapshot.month),
                primogems = snapshot.current_primogems,
                mora = snapshot.current_mora,
                primogemsDelta = adjacent?.let { snapshot.current_primogems - it.current_primogems },
                moraDelta = adjacent?.let { snapshot.current_mora - it.current_mora }
            )
        }

    fun monthLabel(year: Int, month: Int): String = "${year}年${month}月"

    /*
    * older 是否为 newer 的紧邻上一月
    * 跨年:2026年1月 的上一月是 2025年12月
    * */
    fun isPreviousMonth(olderYear: Int, olderMonth: Int, newerYear: Int, newerMonth: Int): Boolean =
        if (newerMonth == 1) {
            olderYear == newerYear - 1 && olderMonth == 12
        } else {
            olderYear == newerYear && olderMonth == newerMonth - 1
        }

    /*
    * 环比文案
    * 无环比数据时返回空串(UI 据此隐藏该行)
    * */
    fun deltaText(delta: Int?): String = when {
        delta == null -> ""
        delta > 0 -> "较上月 +$delta"
        delta < 0 -> "较上月 $delta"
        else -> "较上月 持平"
    }
}
