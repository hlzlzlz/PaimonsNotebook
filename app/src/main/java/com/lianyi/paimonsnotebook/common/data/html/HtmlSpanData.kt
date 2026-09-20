package com.lianyi.paimonsnotebook.common.data.html

import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import com.lianyi.paimonsnotebook.common.util.html.HtmlSpanType
import com.lianyi.paimonsnotebook.ui.theme.Black

/*
* 默认为text
* textList颜色与文本，用于创建不同颜色的字符
* data 用于装载标签的数据,img为src,a为href,linkCard为外部url
* alignment 对齐方式 默认左上
* clickable 是否可点击,img与a标签可点击
* titleList 当标题有多种颜色时,使用此集合来存储
* tableData 表格数据(type == Table 时有效)
* listItems 列表项文本(type == List 时有效,每项为一行,已带序号/项目符号前缀)
* headingLevel 标题级别(type == Heading 时有效,2/3/4)
* */
data class HtmlSpanData(
    val type:HtmlSpanType = HtmlSpanType.P,
    val data:String = "",
    val textList:List<HtmlTextData> = listOf(),
    val alignment: Alignment.Horizontal = Alignment.Start,
    val clickable:Boolean = false,
    val titleList:List<HtmlTextData> = listOf(),
    val tableData: HtmlTableData? = null,
    val listItems: List<String> = listOf(),
    val headingLevel: Int = 0
)
