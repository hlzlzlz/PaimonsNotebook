package com.lianyi.paimonsnotebook.common.util.builder

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import coil.ImageLoader
import coil.request.ImageRequest
import com.lianyi.paimonsnotebook.common.application.PaimonsNotebookApplication
import com.lianyi.paimonsnotebook.common.web.static_resources.StaticResourceBundle
import okhttp3.Headers

inline fun Context.imageLoader(builder: ImageLoader.Builder.() -> Unit) =
    ImageLoader.Builder(this).apply(builder).build()

inline fun Context.imageRequest(
    url: String,
    builder: ImageRequest.Builder.() -> Unit = {}
) =
    ImageRequest.Builder(this).apply {
        /*
        * 离线图标包优先:已下载对应分类时直接读本地文件,完全不发网络请求。
        *
        * ⚠️ 为什么在 request 构造处做而不是在 Interceptor 里做:
        *    OkHttp 的 Interceptor 只能改**网络请求**,而这里要改变的是
        *    "数据源是文件还是 URL" —— 只有 ImageRequest.Builder.data() 能表达。
        *    若改成拦截器,图片仍会先走一次"URL 解析"流程,拿不到"直接读文件"
        *    的收益(而且 Coil 对 File 与 String 的解码器选择也不同)。
        *
        * ⚠️ diskCacheKey 仍设为原 url:即使读的是本地包文件,也保持缓存键不变,
        *    这样同一张图不会因为"这次命中包、那次没命中"而产生两份缓存。
        * */
        val localBundleFile = StaticResourceBundle.localFileForUrl(url)

        data(localBundleFile ?: url)
        memoryCacheKey(url)
        diskCacheKey(url)
    }.apply(builder).build()

fun requestOf(url: String): ImageRequest {
    return PaimonsNotebookApplication.context.imageRequest(url)
}

fun requestOf(url: String, headers: Headers?): ImageRequest {
    /*
    * 此处由原来的LocalContext.current,改为使用全局的context
    * 使用LocalContext.current会使用当前activity的context在图片未加载时activity销毁,可能会造成短期内多次调用GC,导致卡顿,甚至造成内存泄漏
    * 原因通过leakCanary日志推测是:coil启动的imageRequest在activity销毁后依然持有销毁的activity的引用,导致频繁调用GC,GC后可能会销毁这个imageRequest对象,但有相当的概率不会销毁
    * 使用全局的context会在图片显示后加载完成,测试时不再会在短期内频繁调用GC
    * */
    return PaimonsNotebookApplication.context.imageRequest(url) {
        if (headers != null){
            headers(headers)
        }
    }
}