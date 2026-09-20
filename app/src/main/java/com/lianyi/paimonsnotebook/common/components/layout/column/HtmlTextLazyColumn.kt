package com.lianyi.paimonsnotebook.common.components.layout.column

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.text.toSpannable
import com.lianyi.paimonsnotebook.common.components.layout.FoldTextContent
import com.lianyi.paimonsnotebook.common.components.layout.table.HtmlTableContent
import com.lianyi.paimonsnotebook.common.components.lazy.ContentSpacerLazyColumn
import com.lianyi.paimonsnotebook.common.components.media.FullScreenImage
import com.lianyi.paimonsnotebook.common.components.media.NetworkImage
import com.lianyi.paimonsnotebook.common.components.placeholder.TextPlaceholder
import com.lianyi.paimonsnotebook.common.components.placeholder.VideoPlayerPlaceholder
import com.lianyi.paimonsnotebook.common.data.html.HtmlTextData
import com.lianyi.paimonsnotebook.common.database.disk_cache.entity.DiskCache
import com.lianyi.paimonsnotebook.common.extension.string.toAnnotatedString
import com.lianyi.paimonsnotebook.common.util.html.HtmlSpanParser
import com.lianyi.paimonsnotebook.common.util.html.HtmlSpanType
import com.lianyi.paimonsnotebook.ui.theme.Black
import com.lianyi.paimonsnotebook.ui.theme.LinkColor

/*
* 此组件仅用于转换文章详情页的html文本
* 显示其他html需要特定的转换规则
*
* htmlText:解析的html文本,仅支持解析米游社文章详情接口获取的html文本,其他文本需要设置特定的解析规则
* horizontalPadding:item的水平外边距
* verticalPadding:item的垂直外边距
* onHyperlinkClick:当超链接被点击时
* content
*
* */
@Composable
fun HtmlTextLazyColumn(
    htmlText: String,
    diskCacheTemplate: DiskCache,
    horizontalPadding: Dp = 10.dp,
    verticalPadding: Dp = 5.dp,
    fontSize: TextUnit = 16.sp,
    videoCover: String = "",
    onHyperlinkClick: (url: String) -> Unit = {},
    onVideoClick: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    //解析逻辑已抽到 HtmlSpanParser(纯函数,可被单测驱动)
    val htmlSpanData = HtmlSpanParser.parse(htmlText)

    var imageFullScreen by remember {
        mutableStateOf(false)
    }
    var imageUrl by remember {
        mutableStateOf("")
    }

    ContentSpacerLazyColumn(
        modifier = Modifier
            .fillMaxWidth()
    ) {

        item {
            Column(
                modifier = Modifier.padding(
                    horizontalPadding,
                    verticalPadding,
                    horizontalPadding,
                    0.dp
                )
            ) {
                content()
            }
        }

        if (videoCover.isNotBlank()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontalPadding,
                            verticalPadding / 2
                        )
                ) {
                    VideoPlayerPlaceholder(
                        cover = videoCover
                    ) {
                        onVideoClick()
                    }
                }
            }
        }

        items(htmlSpanData) { item ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontalPadding,
                        if (item.type == HtmlSpanType.Img) verticalPadding else verticalPadding / 2
                    ),
                horizontalAlignment = item.alignment
            ) {
                when (item.type) {
                    HtmlSpanType.Img -> {

                        NetworkImage(
                            url = item.data,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .clickable {
                                    imageUrl = item.data
                                    imageFullScreen = true
                                },
                            contentScale = ContentScale.FillWidth,
                            diskCache = diskCacheTemplate.copy(
                                url = item.data,
                                name = "文章详情图片",
                                description = "阅读文章时加载的配图"
                            )
                        )
                    }

                    HtmlSpanType.A, HtmlSpanType.SP -> {

                        TextBuildAnnotatedSpannableString(
                            data = item.textList,
                            fontSize = fontSize
                        ) {
                            onHyperlinkClick(it)
                        }
                    }

                    HtmlSpanType.P -> {
                        TextBuildAnnotatedString(data = item.textList, fontSize = fontSize)
                    }

                    HtmlSpanType.Video -> {
                        VideoPlayerPlaceholder(
                            cover = videoCover
                        ) {
                            onVideoClick()
                        }
                    }

                    HtmlSpanType.Fold -> {

                        FoldTextContent(titleSlot = {
                            TextBuildAnnotatedString(data = item.titleList, fontSize = fontSize)
                        }, contentSlot = {
                            TextBuildAnnotatedString(data = item.textList, fontSize = fontSize)
                        })

                    }

                    HtmlSpanType.LinkCard -> {

                    }

                    /*
                    * 表格(2026-09-20 新增)
                    *
                    * 此前表格被当作图片渲染,表格位置会显示一张无关的默认占位图。
                    * */
                    HtmlSpanType.Table -> {
                        item.tableData?.let { table ->
                            HtmlTableContent(table = table)
                        }
                    }

                    /*
                    * 列表(2026-09-20 新增)
                    *
                    * 实测顶层 ol 18 个(104 个 li),此前整块丢弃。
                    * 序号/项目符号已在解析阶段拼进文本,这里按行显示。
                    * */
                    HtmlSpanType.List -> {
                        if (item.listItems.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                item.listItems.forEach { line ->
                                    Text(
                                        text = line,
                                        fontSize = fontSize,
                                        color = Black
                                    )
                                }
                            }
                        }
                    }

                    /*
                    * 标题(2026-09-20 新增)
                    *
                    * 实测顶层 h4 8 个、h2 4 个,此前丢弃。
                    * 按级别给不同字号,让公告的小节标题有层次。
                    * */
                    HtmlSpanType.Heading -> {
                        val size = when (item.headingLevel) {
                            1 -> 20.sp
                            2 -> 18.sp
                            3 -> 17.sp
                            else -> 16.sp
                        }

                        TextBuildAnnotatedString(
                            data = item.textList,
                            fontSize = size,
                            bold = true
                        )
                    }

                    else -> {
                        TextPlaceholder("此处使用了一个预料外的标签:[${item.data}]。\n向开发者反馈以解决此问题")
                    }
                }
            }
        }
    }

    if (imageFullScreen) {
        Dialog(
            onDismissRequest = { imageFullScreen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                FullScreenImage(url = imageUrl, onClick = {
                    imageFullScreen = false
                })
            }
        }
    }
}

@Composable
private fun TextBuildAnnotatedSpannableString(
    data: List<HtmlTextData>,
    fontSize: TextUnit,
    block: (String) -> Unit = {},
) {

    val text = buildAnnotatedString {
        data.forEach { item ->
            withStyle(style = SpanStyle(fontSize = fontSize, color = item.color)) {
                append(item.spannableString.toSpannable().toAnnotatedString(LinkColor))
            }
        }
    }
    ClickableText(text = text, onClick = { index ->
        val annotatedString = text.getStringAnnotations("URL", 0, text.length)

        annotatedString.forEach {
            if (index in (it.start..it.end)) {
                block(it.item)
                return@forEach
            }
        }
    })
}


@Composable
private fun TextBuildAnnotatedString(
    data: List<HtmlTextData>,
    fontSize: TextUnit,
    bold: Boolean = false,
) {
    val text = buildAnnotatedString {
        data.forEach { item ->
            withStyle(
                style = SpanStyle(
                    color = item.color,
                    fontSize = fontSize,
                    fontWeight = if (bold) FontWeight.Bold else null
                )
            ) {
                append(item.text)
            }
        }
    }
    Text(text = text)
}
