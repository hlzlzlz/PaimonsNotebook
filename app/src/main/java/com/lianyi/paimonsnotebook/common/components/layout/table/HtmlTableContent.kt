package com.lianyi.paimonsnotebook.common.components.layout.table

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lianyi.paimonsnotebook.common.data.html.HtmlTableCell
import com.lianyi.paimonsnotebook.common.data.html.HtmlTableData
import com.lianyi.paimonsnotebook.ui.theme.Black
import com.lianyi.paimonsnotebook.ui.theme.CardBackGroundColor

/*
* 公告正文里的表格渲染
*
* 为什么需要它:实测 34 条公告中有 6 条含真实 <table>(最大 18 行 3 列),
* 原渲染器把 <div class="table-wrapper"><table> 当成 <img> 处理,
* 取不到 src 就回退到一张写死的默认占位图 ⇒ **表格位置显示一张无关图片**。
*
* 实现要点:
*  - 用 Row + weight 按"列跨度"分配宽度,而不是固定列宽 ——
*    这样 3 列表格能占满可用宽度、不同表格列数不同也不会溢出;
*  - **支持 rowspan/colspan**(实测公告确实用了:8 处 rowspan、4 处 colspan,
*    如"祈愿时间"单元格 rowspan=3)。做法是按 HtmlTableData.layoutCells()
*    算出的落位,为"被跨行占住"的位置补一个等宽空占位,
*    保证各行的列数一致、纵向能对齐;
*  - 单元格背景色取自 td 的 style(公告表头是 rgb(255,215,185));
*  - 空单元格也要画边框,否则合并单元格处会缺线。
* */
@Composable
fun HtmlTableContent(
    table: HtmlTableData,
    modifier: Modifier = Modifier
) {
    val columnCount = table.columnCount

    //列数异常时不做表格,直接把文本铺出来(避免除零/空白)
    if (columnCount <= 0 || table.rows.isEmpty()) {
        Text(text = table.rows.joinToString("\n") { row -> row.joinToString(" ") { it.text } })
        return
    }

    val placed = table.layoutCells()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, TableBorderColor, RoundedCornerShape(2.dp))
    ) {
        table.rows.indices.forEach { rowIndex ->
            val rowCells = placed.filter { it.row == rowIndex }.sortedBy { it.column }

            Row(modifier = Modifier.fillMaxWidth()) {
                var cursor = 0

                rowCells.forEach { placedCell ->
                    //补上"被上方 rowspan 占住"的空位,否则该行会左移错位
                    while (cursor < placedCell.column) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(1.dp)
                                .background(TableBorderColor)
                        )
                        cursor++
                    }

                    TableCell(
                        cell = placedCell.cell,
                        weight = placedCell.cell.colSpan.toFloat(),
                        modifier = Modifier.weight(placedCell.cell.colSpan.toFloat())
                    )

                    cursor += placedCell.cell.colSpan
                }

                //补足剩余列,保证每行宽度一致
                while (cursor < columnCount) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(1.dp)
                            .background(TableBorderColor)
                    )
                    cursor++
                }
            }
        }
    }
}

@Composable
private fun TableCell(
    cell: HtmlTableCell,
    weight: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .border(0.5.dp, TableBorderColor)
            .background(cell.backgroundColor ?: CardBackGroundColor)
            .padding(horizontal = 6.dp, vertical = 5.dp),
        contentAlignment = if (cell.alignCenter) Alignment.Center else Alignment.CenterStart
    ) {
        Text(
            text = cell.text,
            fontSize = 13.sp,
            color = Black,
            textAlign = if (cell.alignCenter) TextAlign.Center else TextAlign.Start
        )
    }
}

//表格线颜色(与公告正文的浅灰边框一致)
private val TableBorderColor = androidx.compose.ui.graphics.Color(0xFFC1C7D0)
