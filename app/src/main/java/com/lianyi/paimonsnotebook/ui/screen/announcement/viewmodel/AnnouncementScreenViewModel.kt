package com.lianyi.paimonsnotebook.ui.screen.announcement.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lianyi.paimonsnotebook.common.extension.string.errorNotify
import com.lianyi.paimonsnotebook.common.util.disk_cache.DiskCacheHelper
import com.lianyi.paimonsnotebook.common.util.enums.LoadingState
import com.lianyi.paimonsnotebook.common.web.hoyolab.hk4e.announcement.AnnouncementClient
import com.lianyi.paimonsnotebook.common.web.hoyolab.hk4e.announcement.AnnouncementGroup
import com.lianyi.paimonsnotebook.common.web.hoyolab.hk4e.announcement.AnnouncementHelper
import com.lianyi.paimonsnotebook.common.web.hoyolab.hk4e.announcement.AnnouncementItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/*
* 游戏内公告
*
* 两个端点均为**免登录**可用(实测未带任何 cookie 返回 200),
* 所以本页不做登录态判断、不触发 1034 风控。
*
* 数据是"列表 + 正文"两段,本地按 ann_id 合并后再展示;
* 合并与清洗逻辑在 AnnouncementHelper(纯函数,已有单测)。
* */
class AnnouncementScreenViewModel : ViewModel() {

    private val client = AnnouncementClient()

    /*
    * 注意:Compose 状态的写入必须在主线程。
    * 本页的状态写入全部发生在 viewModelScope.launch 的主线程回调里,
    * 仅网络请求切到 IO(不用 viewModelScope.launch(Dispatchers.IO) 直接写状态)。
    * */
    var loadingState by mutableStateOf(LoadingState.Loading)
        private set

    //按 type_id 分组后的公告(组内已按开始时间倒序)
    var groups by mutableStateOf<List<AnnouncementGroup>>(listOf())
        private set

    //当前展开的分类,默认第一个分组
    var currentGroupIndex by mutableStateOf(0)

    //当前查看的公告(详情页),null 表示在列表页
    var currentItem by mutableStateOf<AnnouncementItem?>(null)
        private set

    var showDetail by mutableStateOf(false)
        private set

    //正文图片的缓存描述,交给 HtmlTextLazyColumn 的 diskCacheTemplate
    val diskCacheTemplate by lazy {
        DiskCacheHelper.getDefault("announcement")
    }

    init {
        load()
    }

    fun load() {
        loadingState = LoadingState.Loading

        viewModelScope.launch {
            //网络请求切 IO;下面的状态赋值回到主线程
            val listResult = withContext(Dispatchers.IO) {
                client.getAnnouncementList()
            }

            if (!listResult.success) {
                loadingState = LoadingState.Error
                "获取公告失败:${listResult.message}[${listResult.retcode}]".errorNotify()
                return@launch
            }

            //data 声明非空但服务端可能返回 null,必须判空
            val data = listResult.data
            if (data == null) {
                loadingState = LoadingState.Error
                "公告数据为空".errorNotify()
                return@launch
            }

            /*
            * 正文接口失败不算整体失败 —— 列表信息(标题/时间)本身可用,
            * 只是没有正文可看(详情页会显示"没有正文内容"),比整页报错体验好。
            * */
            val contents = withContext(Dispatchers.IO) {
                client.getAnnouncementContent().data?.list
            }

            val merged = AnnouncementHelper.merge(
                groups = data.list,
                contents = contents
            )

            groups = merged
            currentGroupIndex = 0

            loadingState = if (AnnouncementHelper.countItems(merged) == 0) {
                LoadingState.Empty
            } else {
                LoadingState.Success
            }
        }
    }

    //当前分组(越界时回退到第一个)
    val currentGroup: AnnouncementGroup?
        get() = groups.getOrNull(currentGroupIndex) ?: groups.firstOrNull()

    fun onGroupSelect(index: Int) {
        currentGroupIndex = index
    }

    fun showDetail(item: AnnouncementItem) {
        currentItem = item
        showDetail = true
    }

    fun dismissDetail() {
        showDetail = false
        currentItem = null
    }
}
