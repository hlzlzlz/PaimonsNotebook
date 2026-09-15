package com.lianyi.paimonsnotebook.ui.screen.home.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lianyi.paimonsnotebook.common.application.PaimonsNotebookApplication
import com.lianyi.paimonsnotebook.common.database.disk_cache.entity.DiskCache
import com.lianyi.paimonsnotebook.common.extension.intent.setComponentName
import com.lianyi.paimonsnotebook.common.extension.string.notify
import com.lianyi.paimonsnotebook.common.util.enums.LoadingState
import com.lianyi.paimonsnotebook.common.util.html.RichTextParser
import com.lianyi.paimonsnotebook.common.util.json.JSON
import com.lianyi.paimonsnotebook.common.util.system_service.SystemService
import com.lianyi.paimonsnotebook.common.view.HoyolabWebActivity
import com.lianyi.paimonsnotebook.common.view.VideoPlayScreen
import com.lianyi.paimonsnotebook.common.web.WebHomeClient
import com.lianyi.paimonsnotebook.common.web.hoyolab.bbs.post.PostFullData
import com.lianyi.paimonsnotebook.ui.screen.home.util.HomeHelper
import com.lianyi.paimonsnotebook.ui.screen.home.util.PostHelper
import com.lianyi.paimonsnotebook.ui.screen.home.view.PostDetailScreen
import com.lianyi.paimonsnotebook.ui.screen.home.view.TopicScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PostDetailViewModel : ViewModel() {

    private val webHomeClient by lazy {
        WebHomeClient()
    }

    private val context by lazy {
        PaimonsNotebookApplication.context
    }

    var postLoadingState by mutableStateOf(LoadingState.Loading)

    var postFullData by mutableStateOf<PostFullData?>(null)

    var fullScreenImgUrl = ""
    var showFullScreenImage by mutableStateOf(false)


    fun showFullScreenImage(url: String) {
        fullScreenImgUrl = url
        showFullScreenImage = true
    }

    fun dismissFullScreenImage() {
        showFullScreenImage = false
    }

    fun loadArticleContent(articleId: Long) {
        postLoadingState = LoadingState.Loading

        viewModelScope.launch(Dispatchers.IO) {
            val result = webHomeClient.getPostFull(articleId)

            postLoadingState = if (result.success) {
                postFullData = result.data
                LoadingState.Success
            } else {
                LoadingState.Error
            }
        }
    }

    fun hyperlinkNavigate(url: String) {
        HomeHelper.goActivityByIntentNewTask {
            PostHelper.checkUrlType(url = url, isPostID = {
                putExtra(PostHelper.PARAM_POST_ID, it)
                setComponentName(PostDetailScreen::class.java)
            }, isUrl = {
                setComponentName(HoyolabWebActivity::class.java)
                putExtra(HoyolabWebActivity.EXTRA_URL, it)
            }, isTopic = {
                setComponentName(TopicScreen::class.java)
                putExtra(PostHelper.PARAM_TOPIC_ID,it)
            })
        }
    }

    fun getHtmlImageDiskCacheData(post: PostFullData.Post.Post) =
        DiskCache(
            url = "",
            lastUseFrom = "文章详情",
            createFrom = "文章详情"
        )

    fun onClickVideo(vod: PostFullData.Post.Vod) {
        HomeHelper.goActivityByIntentNewTask {
            setComponentName(VideoPlayScreen::class.java)
            putExtra("video_list", JSON.stringify(vod))
        }
    }

    fun onClickTag(topic: PostFullData.Post.Topic) {
        HomeHelper.goActivityByIntentNewTask {
            setComponentName(TopicScreen::class.java)
            putExtra(PostHelper.PARAM_TOPIC_ID,topic.id.toLong())
        }
    }

    //兑换码识别结果与弹窗
    var showRedeemCodeDialog by mutableStateOf(false)
    var redeemCodes by mutableStateOf<List<String>>(emptyList())

    /*
    * 从帖子结构化正文提取兑换码(前瞻直播帖子)
    * 优先取"兑换码"字样后的连续字母数字段,取不到再兜底扫描全文
    * */
    fun extractRedeemCodes() {
        val post = postFullData?.post ?: return

        val text = runCatching {
            RichTextParser.parsePostStructuredContent(post.post.content)
        }.getOrNull()
            ?.flatten()
            ?.joinToString("\n") { it.insert.insert ?: it.insert.backup_text ?: "" }
            .orEmpty()

        val codes = LinkedHashSet<String>()

        Regex("兑换码[^A-Za-z0-9]{0,8}([A-Za-z0-9]{9,16})").findAll(text).forEach {
            codes += it.groupValues[1]
        }

        if (codes.isEmpty()) {
            //兜底:8-16位字母数字且同时含字母与数字,排除纯英文单词形态
            Regex("(?<![A-Za-z0-9])[A-Za-z0-9]{8,16}(?![A-Za-z0-9])").findAll(text).forEach { match ->
                match.value.takeIf { value ->
                    value.any(Char::isDigit) && value.any(Char::isLetter) &&
                            !value.all(Char::isLowerCase)
                }?.let(codes::add)
            }
        }

        if (codes.isEmpty()) {
            "未在帖子中识别到兑换码".notify()
            return
        }

        redeemCodes = codes.toList()
        showRedeemCodeDialog = true
    }

    fun dismissRedeemCodeDialog() {
        showRedeemCodeDialog = false
    }

    fun copyRedeemCode(code: String) {
        SystemService.setClipBoardText(code)
        "已复制 $code".notify()
    }
}