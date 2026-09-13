package com.lianyi.paimonsnotebook.common.util.coil

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/*
* 静态资源图片加载失败兜底拦截器
*
* 主图床(static.snaphutaorp.org)不可达或返回错误时,改用Enka.Network的图片源重试
* Enka.Network按原始文件名提供UI_开头的图片(不含MonsterIcon与LoadingPic分类)
* */
object ImageFallbackInterceptor : Interceptor {

    private const val StaticHost = "static.snaphutaorp.org"

    private const val EnkaUiUrl = "https://enka.network/ui/"

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        val response = chain.proceed(request)

        if (request.url.host != StaticHost || response.isSuccessful) {
            return response
        }

        response.close()

        val fileName = request.url.pathSegments.lastOrNull()
        if (fileName.isNullOrBlank() || !fileName.endsWith(".png")) {
            throw IOException("static resource request failed: ${response.code}")
        }

        val fallbackRequest = request.newBuilder()
            .url(EnkaUiUrl + fileName)
            .build()

        return chain.proceed(fallbackRequest)
    }
}
