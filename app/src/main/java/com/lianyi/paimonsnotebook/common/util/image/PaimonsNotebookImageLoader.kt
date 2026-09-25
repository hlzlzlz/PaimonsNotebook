package com.lianyi.paimonsnotebook.common.util.image

import coil.request.ImageRequest
import com.lianyi.paimonsnotebook.common.application.PaimonsNotebookApplication
import com.lianyi.paimonsnotebook.common.web.static_resources.StaticResourceBundle
import okio.ByteString.Companion.encodeUtf8
import java.io.File

/*
* 自定义image loader
* 设置指定的图片存储位置
* */
object PaimonsNotebookImageLoader {

    private val context by lazy {
        PaimonsNotebookApplication.context
    }

    private val imageCache by lazy {
        context.filesDir.resolve("image_cache")
    }

    //获得加载网络图片的request
    fun getImageRequest(url: String): ImageRequest {
        val imageFile = getCacheImageFileByUrl(url)

        return ImageRequest.Builder(context)
            /*
            * 三档优先级:Coil 磁盘缓存 -> 离线图标包 -> 网络。
            *
            * 离线图标包(`static_bundle/{分类}/{文件名}`)是 2026-09-26 新增的,
            * 用于绕开极慢的主图床(实测单张 72KB 图标 21.8s~137.5s)。
            * 详见 StaticResourceBundle 的注释。
            * */
            .data(imageFile ?: StaticResourceBundle.localFileForUrl(url) ?: url)
            .crossfade(true)
            .diskCacheKey(url)
//            .memoryCacheKey(url)
            .build()
    }

    fun getCacheImageFileByName(name: String) = File(imageCache, name)

    fun getCacheImageFileByUrl(url: String): File? {
        val file = File(imageCache, "${url.encodeUtf8().sha256().hex()}.1")
        return if (file.exists()) {
            file
        } else {
            null
        }
    }

    fun getCacheImageMetadataFileByUrl(url: String): File? {
        val file = File(imageCache, "${url.encodeUtf8().sha256().hex()}.0")
        return if (file.exists()) {
            file
        } else {
            null
        }
    }
}