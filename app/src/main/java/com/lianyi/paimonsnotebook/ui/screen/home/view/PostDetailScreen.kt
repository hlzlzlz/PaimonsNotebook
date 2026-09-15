package com.lianyi.paimonsnotebook.ui.screen.home.view

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.AlertDialog
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.ViewModelProvider
import com.lianyi.paimonsnotebook.common.components.loading.ContentLoadingAnimationPlaceholder
import com.lianyi.paimonsnotebook.common.components.loading.ContentLoadingLayout
import com.lianyi.paimonsnotebook.common.components.media.FullScreenImage
import com.lianyi.paimonsnotebook.common.components.placeholder.EmptyPlaceholder
import com.lianyi.paimonsnotebook.common.components.placeholder.ErrorPlaceholder
import com.lianyi.paimonsnotebook.common.core.base.BaseActivity
import com.lianyi.paimonsnotebook.common.extension.modifier.radius.radius
import com.lianyi.paimonsnotebook.ui.screen.home.components.post.PostStructureContentList
import com.lianyi.paimonsnotebook.ui.screen.home.util.PostHelper
import com.lianyi.paimonsnotebook.ui.screen.home.viewmodel.PostDetailViewModel
import com.lianyi.paimonsnotebook.ui.theme.CardBackGroundColor
import com.lianyi.paimonsnotebook.ui.theme.PaimonsNotebookTheme
import com.lianyi.paimonsnotebook.ui.theme.Primary

class PostDetailScreen : BaseActivity() {
    private val viewModel by lazy { ViewModelProvider(this)[PostDetailViewModel::class.java] }

    private val articleId by lazy {
        intent.getLongExtra(PostHelper.PARAM_POST_ID, 0L)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel.loadArticleContent(articleId)

        setContent {
            PaimonsNotebookTheme(this) {

                ContentLoadingLayout(
                    loadingState = viewModel.postLoadingState,
                    loadingContent = {
                        ContentLoadingAnimationPlaceholder()
                    },
                    emptyContent = {
                        EmptyPlaceholder()
                    },
                    errorContent = {
                        ErrorPlaceholder("文章获取失败")
                    },
                    defaultContent = {
                        EmptyPlaceholder()
                    }
                ) {
                    Content()
                }
            }
        }
    }

    @Composable
    fun Content() {
        Box(modifier = Modifier.fillMaxSize()) {
            if (viewModel.postFullData != null) {
                PostStructureContentList(
                    viewModel.postFullData!!,
                    fontSize = 14.sp,
                    onClickImage = viewModel::showFullScreenImage,
                    onClickLink = viewModel::hyperlinkNavigate,
                    onClickLinkCard = viewModel::hyperlinkNavigate,
                    onClickVideo = viewModel::onClickVideo,
                    onClickTag = viewModel::onClickTag
                )

                //兑换码提取入口(前瞻直播帖子)
                Text(
                    text = "提取兑换码",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Primary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp, 8.dp)
                        .zIndex(1f)
                        .radius(6.dp)
                        .background(CardBackGroundColor)
                        .clickable { viewModel.extractRedeemCodes() }
                        .padding(10.dp, 6.dp)
                )
            }

            if (viewModel.showFullScreenImage) {
                FullScreenImage(
                    url = viewModel.fullScreenImgUrl,
                    onClick = viewModel::dismissFullScreenImage
                )
            }
        }

        if (viewModel.showRedeemCodeDialog) {
            RedeemCodeDialog()
        }
    }

    @Composable
    fun RedeemCodeDialog() {
        AlertDialog(
            onDismissRequest = viewModel::dismissRedeemCodeDialog,
            title = { Text(text = "识别到 ${viewModel.redeemCodes.size} 个兑换码", fontSize = 17.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    viewModel.redeemCodes.forEach { code ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = code,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )

                            TextButton(onClick = { viewModel.copyRedeemCode(code) }) {
                                Text(text = "复制", color = Primary)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.copyRedeemCode(viewModel.redeemCodes.joinToString("\n"))
                }) {
                    Text(text = "全部复制", color = Primary)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissRedeemCodeDialog) {
                    Text(text = "关闭")
                }
            }
        )
    }
}