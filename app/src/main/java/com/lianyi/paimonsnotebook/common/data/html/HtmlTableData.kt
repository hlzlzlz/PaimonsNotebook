package com.lianyi.paimonsnotebook.common.data.html

import androidx.compose.ui.graphics.Color

/*
* 表格数据
*
* 为什么需要它:游戏公告正文里有真实的 <table>(实测 34 条公告中 6 条含表格,
* 最大 18 行 3 列,且用了 rowspan/colspan),而原渲染器把
* <div class="table-wrapper"><table>…</table></div> 当成 <img> 处理,
* 取不到 src 时回退到一张默认占位图 —— 结果是**表格位置显示一张无关图片**。
* */
data class HtmlTableData(
    val rows: List<List<HtmlTableCell>>
) {
    /*
    * 表格总列数
    *
    * 取每个单元格 (起始列 + colSpan) 的最大值。
    * 渲染时各行都用这个总数算权重,否则有 rowspan 的行会因为"少一列"
    * 而整体左移、与其它行对不齐。
    * */
    val columnCount: Int
        get() {
            val placed = layoutCells()
            return placed.maxOfOrNull { it.column + it.cell.colSpan } ?: 0
        }

    val rowCount: Int
        get() = rows.size

    /*
    * 计算每个单元格的落位(row/column)
    *
    * 用"占位表"逐格跳过已被 rowspan/colspan 占住的位置,
    * 这是渲染带合并单元格的表格的标准做法。
    * */
    fun layoutCells(): List<PlacedCell> {
        val occupied = mutableSetOf<Pair<Int, Int>>()
        val placed = mutableListOf<PlacedCell>()

        rows.forEachIndexed { rowIndex, row ->
            var column = 0

            row.forEach { cell ->
                // 跳过被上方 rowspan 占用的格子
                while (occupied.contains(rowIndex to column)) {
                    column++
                }

                for (dr in 0 until cell.rowSpan) {
                    for (dc in 0 until cell.colSpan) {
                        occupied.add((rowIndex + dr) to (column + dc))
                    }
                }

                placed.add(PlacedCell(row = rowIndex, column = column, cell = cell))
                column += cell.colSpan
            }
        }

        return placed
    }

    //某个单元格的落位
    data class PlacedCell(
        val row: Int,
        val column: Int,
        val cell: HtmlTableCell
    )
}

/*
* 表格单元格
*
* text:纯文本内容(公告表格里是角色名/时间,无富文本需求)
* rowSpan/colSpan:合并单元格,实测公告里确有使用(8 处 rowspan、4 处 colspan)
* isHeader:th 标签
* backgroundColor:取自 td 的 style(实测形如 "background-color: rgb(255, 215, 185)")
* alignCenter:单元格内容是否居中(取自内层 p 的 text-align)
* */
data class HtmlTableCell(
    val text: String,
    val rowSpan: Int = 1,
    val colSpan: Int = 1,
    val isHeader: Boolean = false,
    val backgroundColor: Color? = null,
    val alignCenter: Boolean = false
)
