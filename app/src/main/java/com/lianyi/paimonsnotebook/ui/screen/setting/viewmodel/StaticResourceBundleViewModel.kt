package com.lianyi.paimonsnotebook.ui.screen.setting.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lianyi.paimonsnotebook.common.extension.scope.launchIO
import com.lianyi.paimonsnotebook.common.extension.scope.withContextMain
import com.lianyi.paimonsnotebook.common.extension.string.errorNotify
import com.lianyi.paimonsnotebook.common.extension.string.notify
import com.lianyi.paimonsnotebook.common.extension.string.warnNotify
import com.lianyi.paimonsnotebook.common.web.static_resources.StaticResourceBundle
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/*
* 离线图标包管理
*
* ## 为什么需要这个功能
*
* 2026-09-26 实测:主图床 `static.snaphutaorp.org` 加载单张 72KB 图标耗时
* **21.8s / 62.2s / 137.5s**,甚至直接 60s 超时;而同期本机带宽正常
* (npmmirror 1862 KB/s、baidu 194ms)⇒ 瓶颈在图床。
*
* 图标是**一张一个请求**的,角色资料就有 118 个 ⇒ 一屏几十个并发请求,
* 每个几十秒,页面像卡死。唯一根治办法是把官方打包好的图标 zip
* 一次性下载到本地,之后**完全不走网络**。
*
* ## 设计要点
*
*   - **按分类单独下载,不提供"全部下载"**:实测各包大小差距极大
*     (Talent 11.9MB ~ ItemIcon 134.2MB),全部下载合计 400MB+,
*     对手机存储是不可接受的一键操作。让用户只下自己常用的。
*   - **同一时刻只允许一个下载任务**:包都很大,并发下载既抢带宽又更容易
*     触发图床的超时。用 [currentJob] 串行化。
*   - **进度可见**:大文件下载没有进度用户会以为卡死。
*   - **失败要说原因**:图床本身不稳,失败是常态,必须告诉用户"是网络/图床的问题,
*     可以重试",而不是静默什么都不发生。
* */
class StaticResourceBundleViewModel : ViewModel() {

    /*
    * 各分类的下载进度:category -> 0f..1f
    * 不在 map 里表示未在下载;1f 表示下载完成(解压中)
    * */
    val progressMap = mutableStateMapOf<String, Float>()

    /*
    * 已完成(含本次会话之前就下好的)分类集合
    *
    * ⚠️ 用 mutableStateMapOf 而不是每次读文件系统:
    *    isBundleReady 会走 File.exists(),放在 Compose 组合里每帧调用会拖慢列表。
    * */
    val readyMap = mutableStateMapOf<String, Boolean>()

    var isDownloading by mutableStateOf(false)
        private set

    var currentCategory by mutableStateOf<String?>(null)
        private set

    private var currentJob: Job? = null

    //已占用空间(字节)
    var usedBytes by mutableStateOf(0L)
        private set

    init {
        refreshState()
    }

    /*
    * 从磁盘重新读取各分类的就绪状态与占用空间
    *
    * 进入设置页时调用 —— 用户可能在别处(如清缓存)改动过。
    * */
    fun refreshState() {
        viewModelScope.launchIO {
            val ready = StaticResourceBundle.AVAILABLE_BUNDLES.associate { info ->
                info.category to StaticResourceBundle.isBundleReady(info.category)
            }
            val used = StaticResourceBundle.usedBytes()

            withContextMain {
                readyMap.clear()
                readyMap.putAll(ready)
                usedBytes = used
            }
        }
    }

    fun download(category: String) {
        if (isDownloading) {
            "正在下载其它图标包,请稍候".warnNotify()
            return
        }

        val info = StaticResourceBundle.AVAILABLE_BUNDLES.firstOrNull { it.category == category }
            ?: return

        isDownloading = true
        currentCategory = category
        progressMap[category] = 0f

        currentJob = viewModelScope.launch {
            try {
                val count = StaticResourceBundle.downloadAndExtract(category) { read, total ->
                    if (total > 0) {
                        val p = (read.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                        /*
                        * ⚠️ 进度回调用 OkHttp 的读线程。
                        *    直接写 Compose 状态会违反"状态写入必须在主线程"的项目约定,
                        *    故投递到主线程。
                        * */
                        viewModelScope.launch { progressMap[category] = p }
                    }
                }

                withContextMain {
                    progressMap.remove(category)
                    readyMap[category] = true
                    isDownloading = false
                    currentCategory = null
                }

                usedBytes = StaticResourceBundle.usedBytes()

                "${info.label}已离线(${count} 个文件),之后加载不再走网络".notify()
            } catch (e: Exception) {
                withContextMain {
                    progressMap.remove(category)
                    isDownloading = false
                    currentCategory = null
                }

                /*
                * 图床不稳是当前的主要现实,失败提示必须说清"可以重试"
                * 且给出真实原因,否则用户只会以为应用坏了。
                * */
                "下载失败:${e.message?.take(80) ?: "未知错误"}\n图床较慢,可稍后重试".errorNotify()
            }
        }
    }

    fun cancel() {
        currentJob?.cancel()
        currentJob = null

        val category = currentCategory
        viewModelScope.launch {
            withContextMain {
                if (category != null) progressMap.remove(category)
                isDownloading = false
                currentCategory = null
            }
            "已取消下载".notify()
        }
    }

    fun delete(category: String) {
        if (isDownloading) {
            "正在下载中,请先等待完成".warnNotify()
            return
        }

        viewModelScope.launchIO {
            StaticResourceBundle.deleteBundle(category)
            val used = StaticResourceBundle.usedBytes()

            withContextMain {
                readyMap[category] = false
                usedBytes = used
            }

            "已删除该图标包,恢复走网络加载".notify()
        }
    }
}
