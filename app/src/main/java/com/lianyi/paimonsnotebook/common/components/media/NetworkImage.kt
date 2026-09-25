package com.lianyi.paimonsnotebook.common.components.media

import android.graphics.BitmapFactory
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.lianyi.paimonsnotebook.common.database.disk_cache.entity.DiskCache
import com.lianyi.paimonsnotebook.common.database.util.PaimonsNoteBookDatabaseHelper
import com.lianyi.paimonsnotebook.common.util.builder.requestOf
import com.lianyi.paimonsnotebook.common.util.image.BuiltInThumbnails
import com.lianyi.paimonsnotebook.common.util.image.ImageErrorLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Headers

/*
* 网络图片
*
* url:图片链接
* contentScale:图片缩放模式
* diskCacheData:本地存储数据
* tint:
* placeholder:自定义的图片占位符
*
* ## 内置缩略图:先显示小图,高清下载完再换上
*
* 2026-09-26 实测图床极慢(单张 70KB 图标 7.7s~52s,同一张图重复五次),
* 而图标是一张一个请求的 ⇒ 列表页几十个请求、每个几十秒,页面像卡死。
*
* 对策:APK 内置 3367 张 48px WebP 缩略图(见 [BuiltInThumbnails]),
* 加载时**先画本地小图**,等高清图就绪后 `AsyncImage` 自动替换 ——
* 于是页面立刻有内容,不再白屏等网络。
*
* ⚠️ 实现要点:用 Coil 的 `placeholder` 参数而非自己叠两层 Box。
*    Coil 会在内存/磁盘缓存命中或网络成功后**自动**用真图覆盖占位图,
*    并由 crossfade 平滑过渡;手写两层反而要自己判断"何时算加载完成",
*    还要处理两者的 contentScale/tint 一致性,更容易出偏差。
*
* ⚠️ placeholder 用的是 `ImageRequest` 而不是 Bitmap:
*    这样它同样走 Coil 的内存缓存与采样(48px 图会被采样到显示尺寸),
*    而直接传 Bitmap 会绕过这些、并在每个 item 里持有一份位图。
* */
@Composable
fun NetworkImage(
    url: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    diskCache: DiskCache = DiskCache(url = url),
    tint: Color? = null,
    headers: Headers? = null,
    alignment: Alignment = Alignment.Center,
) {
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            PaimonsNoteBookDatabaseHelper.updateImageUseInfo(diskCache)
        }
    }

    /*
    * 取内置缩略图(可能为 null —— 未内置该图或包不可用)。
    *
    * ⚠️ **先用同步缓存**兜住"已经读过一次的图":
    *    produceState 的初值是 null,列表滚动时 item 会回收重组,
    *    若每次都从 null 开始,用户会看到占位图闪一下才出现。
    *    `cachedThumbnailForUrl` 只读内存(不碰 IO),可安全在组合期调用,
    *    命中时首帧就有图,不闪。
    *
    * ⚠️ 未命中才走 IO 异步读取(key 用 url,列表复用时不串图)。
    * */
    val thumbnail by produceState<ByteArray?>(
        initialValue = BuiltInThumbnails.cachedThumbnailForUrl(url),
        key1 = url
    ) {
        //已有缓存就不必再读 zip
        if (value != null) return@produceState

        value = withContext(Dispatchers.IO) {
            BuiltInThumbnails.thumbnailBytesForUrl(url)
        }
    }

    /*
    * 把内置小图解码成 Painter 供 Coil 的 `placeholder` 用。
    *
    * ⚠️ AsyncImage 的 placeholder 参数类型是 **Painter** 而不是 ImageRequest
    *    (已从 coil-compose-2.6.0.aar 反编译确认签名),故这里手动解码。
    *    解码同样必须在 IO 线程。
    *
    * ⚠️ 用 remember(thumbnail) 缓存:thumbnail 变化时重新解码,
    *    否则每次重组都解码一遍 48px 图,滚动时会有可观开销。
    *
    * ⚠️ 解码失败返回 null —— 此时 Coil 就当没有占位图(退化为原有行为),
    *    不能因为占位图坏了而让整张图显示不出来。
    * */
    val placeholderPainter by produceState<Painter?>(initialValue = null, key1 = thumbnail) {
        val bytes = thumbnail
        value = if (bytes == null) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching {
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()?.let {
                        BitmapPainter(it)
                    }
                }.getOrNull()
            }
        }
    }

    Box(modifier = modifier) {
        AsyncImage(
            model = requestOf(url = url, headers = headers),
            modifier = Modifier.fillMaxWidth(),
            contentDescription = null,
            contentScale = contentScale,
            //内置小图占位:高清图就绪后 Coil 自动替换并 crossfade
            placeholder = placeholderPainter,
            /*
            * ⚠️ 失败时也显示内置小图,而不是全局的"图片加载失败"图标。
            *
            * 理由:既然本地已经有一张能看的图(48px 小图),在网络失败时
            * 拿它顶上**严格优于**显示一个错误占位 —— 用户至少还认得出这是谁。
            * 这正是本功能的目的:图床再慢/再挂,列表也不该"什么都没有"。
            *
            * 仅当确实取到小图时才覆盖;没有小图时保持全局 error drawable
            * (此时"加载失败"的提示才有意义)。
            * */
            error = placeholderPainter,
            onError = { result ->
                ImageErrorLogger.log(url, result.result.throwable)
            },
            onSuccess = {
            },
            colorFilter = if (tint != null) ColorFilter.tint(tint) else null,
            alignment = alignment,
        )
    }
}

@Composable
fun NetworkImage(
    modifier: Modifier = Modifier,
    diskCache: DiskCache,
    contentScale: ContentScale = ContentScale.Fit,
) {
    NetworkImage(
        url = diskCache.url,
        modifier = modifier,
        contentScale = contentScale,
        diskCache = diskCache
    )
}