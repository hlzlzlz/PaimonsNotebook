package com.lianyi.paimonsnotebook.ui.screen.announcement.view

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import com.lianyi.core.ui.components.text.InfoText
import com.lianyi.core.ui.components.text.PrimaryText
import com.lianyi.paimonsnotebook.common.components.layout.column.HtmlTextLazyColumn
import com.lianyi.paimonsnotebook.common.components.layout.column.TabBarColumnLayout
import com.lianyi.paimonsnotebook.common.components.loading.ContentLoadingAnimationPlaceholder
import com.lianyi.paimonsnotebook.common.components.loading.ContentLoadingLayout
import com.lianyi.paimonsnotebook.common.components.placeholder.EmptyPlaceholder
import com.lianyi.paimonsnotebook.common.components.placeholder.ErrorPlaceholder
import com.lianyi.paimonsnotebook.common.core.base.BaseActivity
import com.lianyi.paimonsnotebook.common.database.disk_cache.entity.DiskCache
import com.lianyi.paimonsnotebook.common.extension.modifier.radius.radius
import com.lianyi.paimonsnotebook.common.web.hoyolab.hk4e.announcement.AnnouncementItem
import com.lianyi.paimonsnotebook.ui.screen.announcement.viewmodel.AnnouncementScreenViewModel
import com.lianyi.paimonsnotebook.ui.theme.Black_60
import com.lianyi.paimonsnotebook.ui.theme.CardBackGroundColor
import com.lianyi.paimonsnotebook.ui.theme.PaimonsNotebookTheme

/*
* 游戏内公告
*
* 此前 AnnouncementScreen 是空壳(内容全被注释、未注册 manifest、全项目零引用),
* 本次接通:列表按 type_id 分组,点条目进详情用 HtmlTextLazyColumn 渲染正文 HTML。
*
* 端点免登录可用,故本页不依赖账号,也不需要 1034 验证。
* */
class AnnouncementScreen : BaseActivity() {

    private val viewModel by lazy {
        ViewModelProvider(this)[AnnouncementScreenViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            PaimonsNotebookTheme(this) {
                //详情页优先展示
                if (viewModel.showDetail) {
                    AnnouncementDetailContent(
                        item = viewModel.currentItem,
                        diskCacheTemplate = viewModel.diskCacheTemplate,
                        onBack = viewModel::dismissDetail
                    )
                    return@PaimonsNotebookTheme
                }

                val tabs = viewModel.groups.map { it.type_label }.toTypedArray()

                //分组为空时用单个占位 tab,避免 TabBar 空数组
                TabBarColumnLayout(
                    tabs = if (tabs.isEmpty()) arrayOf("公告") else tabs,
                    onTabBarSelect = viewModel::onGroupSelect,
                    tabBarPaddingHorizontal = 12.dp
                ) {
                    ContentLoadingLayout(
                        loadingState = viewModel.loadingState,
                        loadingContent = {
                            ContentLoadingAnimationPlaceholder()
                        },
                        emptyContent = { EmptyPlaceholder("暂无公告") },
                        errorContent = { ErrorPlaceholder("公告获取失败") },
                        successContent = {
                            val group = viewModel.currentGroup

                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(
                                    items = group?.list.orEmpty(),
                                    //ann_id 是稳定业务主键
                                    key = { it.ann_id }
                                ) { item ->
                                    AnnouncementListItem(
                                        item = item,
                                        onClick = { viewModel.showDetail(item) }
                                    )
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

/*
* 列表条目:标题 + 时间 + 分类标签
* */
@Composable
private fun AnnouncementListItem(
    item: AnnouncementItem,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp, 2.dp)
            .radius(6.dp)
            .background(CardBackGroundColor)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        PrimaryText(
            text = item.title,
            textSize = 15.sp,
            bold = false,
            maxLines = 2
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (item.tag_label.isNotBlank()) {
                Text(
                    text = item.tag_label,
                    fontSize = 11.sp,
                    color = Black_60
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            InfoText(
                text = formatTimeRange(item.start_time, item.end_time),
                fontSize = 12.sp
            )
        }
    }
}

/*
* 详情页:标题 + 时间 + 正文 HTML
*
* 正文走 HtmlTextLazyColumn(与米游社帖子详情同一套渲染),
* 它能处理 <p>/<img> 与超链接,故这里直接传原始 HTML。
* */
@Composable
private fun AnnouncementDetailContent(
    item: AnnouncementItem?,
    diskCacheTemplate: DiskCache,
    onBack: () -> Unit
) {
    if (item == null) {
        EmptyPlaceholder("公告内容为空")
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        //返回 + 标题
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CardBackGroundColor)
                .clickable(onClick = onBack)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "＜ 返回", fontSize = 14.sp)
        }

        if (item.content.isBlank()) {
            //正文接口失败或该条无正文(has_content=false)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PrimaryText(text = item.title, textSize = 18.sp)
                InfoText(text = "本条公告没有正文内容")
            }
            return@Column
        }

        HtmlTextLazyColumn(
            htmlText = item.content,
            diskCacheTemplate = diskCacheTemplate
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PrimaryText(text = item.title, textSize = 18.sp)
                InfoText(text = "发表时间:${item.start_time}")
            }
        }
    }
}

/*
* 时间区间文案
*
* 接口返回形如 "2026-09-12 21:15:00",只取到分钟即可
* */
private fun formatTimeRange(start: String, end: String): String {
    val s = start.take(16)
    val e = end.take(16)

    return when {
        s.isBlank() && e.isBlank() -> ""
        e.isBlank() -> s
        else -> "$s ~ $e"
    }
}
