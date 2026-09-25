package com.lianyi.paimonsnotebook.common.util.coil

import com.lianyi.paimonsnotebook.common.web.static_resources.StaticResourceSources
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/*
* 静态资源图片加载的**多源重试**拦截器
*
* 背景与实测数据见 `StaticResourceSources` 的注释 —— 主图床
* `static.snaphutaorp.org` 实测单张 72KB 图标耗时 21.8s~137.5s、甚至 60s 超时,
* 而本机带宽正常(同期 npmmirror 1862 KB/s)。
*
* ## 本次修的两个真实缺陷(原实现)
*
* ### ① 超时不触发兜底(最关键)
* 原实现是:
* ```
* val response = chain.proceed(request)          // 超时时这里直接抛异常
* if (request.url.host != StaticHost || response.isSuccessful) return response
* ```
* ⇒ **`chain.proceed` 超时是抛 `IOException`,根本走不到 `if`**,
*    而当前图床的主要症状恰恰是超时 ⇒ 兜底从未真正生效过。
*    现在把每次尝试都包在 try/catch 里,**超时与连接失败都会继续试下一个源**。
*
* ### ② 只有 enka 一个兜底,且 enka 自己也很慢
* enka 实测 7.9s / 23.6s / 12.7s。现在先试**路径结构完全一致**的镜像
* (`static.hutaorp.org`,实测 4.1s),enka 退为最后一档,并按分类过滤。
*
* ## 设计约束
*
*   - **只对静态资源生效**:host 不是主图床的请求(用户帖子里外链的图、
*     B 站图等)原样透传,不介入、不重试 —— 否则会改变非本应用资源的加载行为。
*   - **每个候选只试一次**:重试次数由候选数量决定(主 + 镜像 + enka ≤ 3)。
*     不做指数退避 —— 图片加载是用户可感知的等待,宁可快速失败让 Coil 显示占位图。
*   - **失败要抛出**:所有候选都失败时抛 IOException,让 Coil 走它自己的 onError
*     与占位图逻辑(不能静默返回一个空响应,那会变成"加载成功但空白")。
*   - **失败原因逐个记录**在抛出的异常里,便于定位是哪个源的问题
*     (真机日志是本项目排查图床问题的唯一手段)。
* */
object ImageFallbackInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val originalUrl = request.url.toString()

        //非静态资源:原样透传,不介入
        if (!StaticResourceSources.isStaticResource(originalUrl)) {
            return chain.proceed(request)
        }

        val candidates = StaticResourceSources.candidateUrls(originalUrl)

        /*
        * 第一个候选就是原 URL,先直接试它(最常见的情况:主图床正常,一次成功)。
        * 失败后依次尝试其余候选。
        * */
        val failures = mutableListOf<String>()

        candidates.forEachIndexed { index, candidateUrl ->
            try {
                val attempt = if (index == 0) {
                    request
                } else {
                    request.newBuilder().url(candidateUrl).build()
                }

                val response = chain.proceed(attempt)

                if (response.isSuccessful) {
                    return response
                }

                /*
                * HTTP 错误码:关掉响应体再试下一个源。
                * ⚠️ 必须 close —— 否则连接无法复用,最终耗尽连接池。
                * */
                val code = response.code
                response.close()
                failures += "HTTP $code @ $candidateUrl"
            } catch (e: IOException) {
                /*
                * ⚠️ 这里就是原实现的缺陷所在:超时/连接失败走的是这条分支,
                *    原代码没有捕获它,于是"兜底"永远不会被触发。
                * */
                failures += "${e.javaClass.simpleName} @ $candidateUrl: ${e.message}"
            }
        }

        //全部候选都失败:抛出,交给 Coil 的 onError 与占位图
        throw IOException("static resource failed, tried ${candidates.size} source(s): $failures")
    }
}
