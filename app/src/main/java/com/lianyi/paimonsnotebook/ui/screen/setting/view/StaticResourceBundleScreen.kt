package com.lianyi.paimonsnotebook.ui.screen.setting.view

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import com.lianyi.core.ui.components.text.InfoText
import com.lianyi.core.ui.components.text.PrimaryText
import com.lianyi.paimonsnotebook.common.components.layout.column.TopSlotColumnLayout
import com.lianyi.paimonsnotebook.common.components.lazy.ContentSpacerLazyColumn
import com.lianyi.paimonsnotebook.common.core.base.BaseActivity
import com.lianyi.paimonsnotebook.common.extension.modifier.radius.radius
import com.lianyi.paimonsnotebook.common.web.static_resources.StaticResourceBundle
import com.lianyi.paimonsnotebook.ui.screen.setting.viewmodel.StaticResourceBundleViewModel
import com.lianyi.paimonsnotebook.ui.theme.BackGroundColor
import com.lianyi.paimonsnotebook.ui.theme.CardBackGroundColor
import com.lianyi.paimonsnotebook.ui.theme.PaimonsNotebookTheme
import com.lianyi.paimonsnotebook.ui.theme.Primary
import com.lianyi.paimonsnotebook.ui.theme.Success
import com.lianyi.paimonsnotebook.ui.theme.Warning

/*
* 离线图标包(把官方打包的图标一次性下到本地,之后不再走网络)
*
* 背景:2026-09-26 实测主图床加载单张 72KB 图标要 21.8s~137.5s,
* 而图标是一张一个请求的 ⇒ 页面像卡死。详见 StaticResourceBundle 注释。
* */
class StaticResourceBundleScreen : BaseActivity() {

    private val viewModel by lazy {
        ViewModelProvider(this)[StaticResourceBundleViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            PaimonsNotebookTheme(this) {
                TopSlotColumnLayout(
                    topSlot = {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PrimaryText(text = "离线图标包", textSize = 18.sp)
                        }
                    }
                ) {
                    ContentSpacerLazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(BackGroundColor)
                    ) {
                        item {
                            ExplainCard(usedBytes = viewModel.usedBytes)
                        }

                        items(StaticResourceBundle.AVAILABLE_BUNDLES.size) { index ->
                            val info = StaticResourceBundle.AVAILABLE_BUNDLES[index]

                            BundleRow(
                                info = info,
                                ready = viewModel.readyMap[info.category] == true,
                                progress = viewModel.progressMap[info.category],
                                downloading = viewModel.isDownloading,
                                isCurrent = viewModel.currentCategory == info.category,
                                onDownload = { viewModel.download(info.category) },
                                onDelete = { viewModel.delete(info.category) }
                            )
                        }

                        item {
                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExplainCard(usedBytes: Long) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp, 4.dp)
            .radius(6.dp)
            .background(CardBackGroundColor)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        PrimaryText(text = "为什么要下载图标包", textSize = 15.sp)

        InfoText(
            text = "当前图片源(图床)很不稳定,实测加载一张几十 KB 的图标要 20 秒以上," +
                "而图标是一张一个请求的,所以列表会显得很卡。\n\n" +
                "下载对应的图标包后,该类图标会完全从本地读取,不再走网络。",
            fontSize = 13.sp
        )

        if (usedBytes > 0) {
            Text(
                text = "已占用:${formatSize(usedBytes)}",
                fontSize = 13.sp,
                color = Primary,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun BundleRow(
    info: StaticResourceBundle.BundleInfo,
    ready: Boolean,
    progress: Float?,
    downloading: Boolean,
    isCurrent: Boolean,
    onDownload: () -> Unit,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp, 4.dp)
            .radius(6.dp)
            .background(CardBackGroundColor)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PrimaryText(text = info.label, textSize = 15.sp)

            Spacer(modifier = Modifier.width(8.dp))

            InfoText(text = "${info.sizeMb} MB", fontSize = 12.sp)

            Spacer(modifier = Modifier.weight(1f))

            when {
                ready -> Text(
                    text = "已离线",
                    fontSize = 13.sp,
                    color = Success,
                    fontWeight = FontWeight.SemiBold
                )

                progress != null -> Text(
                    text = "${(progress * 100).toInt()}%",
                    fontSize = 13.sp,
                    color = Warning,
                    fontWeight = FontWeight.SemiBold
                )

                else -> Text(
                    text = "下载",
                    fontSize = 13.sp,
                    color = Primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable(enabled = !downloading) { onDownload() }
                )
            }
        }

        /*
        * 已离线时给"删除"入口(释放空间);
        * 未下载时说明收益,让用户知道值不值得下。
        * */
        if (ready) {
            Text(
                text = "删除(释放 ${info.sizeMb} MB)",
                fontSize = 12.sp,
                color = Warning,
                modifier = Modifier.clickable(enabled = !downloading) { onDelete() }
            )
        } else if (isCurrent && progress != null) {
            InfoText(text = "正在下载…大文件请保持网络连接", fontSize = 12.sp)
        } else {
            InfoText(
                text = "下载后该分类图标从本地读取,不再受图床速度影响",
                fontSize = 12.sp
            )
        }
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1024L * 1024L * 1024L -> "%.2f GB".format(bytes / 1024.0 / 1024.0 / 1024.0)
    bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
    bytes >= 1024L -> "%.0f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
