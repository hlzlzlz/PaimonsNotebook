package com.lianyi.paimonsnotebook.ui.screen.setting.util

import com.lianyi.paimonsnotebook.common.application.PaimonsNotebookApplication
import com.lianyi.paimonsnotebook.common.extension.list.takeFirstIf
import com.lianyi.paimonsnotebook.common.extension.string.errorNotify
import com.lianyi.paimonsnotebook.common.util.file.FileHelper
import com.lianyi.paimonsnotebook.common.util.parameter.getParameterizedType
import com.lianyi.paimonsnotebook.common.util.request.ProgressListener
import com.lianyi.paimonsnotebook.common.util.request.ProgressResponseBody
import com.lianyi.paimonsnotebook.common.util.request.buildRequest
import com.lianyi.paimonsnotebook.common.util.request.getAsByteResult
import com.lianyi.paimonsnotebook.common.util.request.getAsJsonNative
import com.lianyi.paimonsnotebook.ui.screen.setting.data.GithubLatestData
import okhttp3.OkHttpClient
import java.io.File

/*
* 更新服务
*
* */
class UpdateService {

    companion object {
        val updateRemoteEndpoints = listOf(
            "github",
            "mirror.ghproxy(推荐)",
            "cdn.jsdelivr"
        )

        //下载到本地的安装包文件名(固定ASCII名,便于覆盖与避免中文名问题)
        private const val RELEASE_PACKAGE_FILE_NAME = "PaimonsNotebook-update"

        //安装包最小合理体积(小于此值必然是错误响应)
        private const val MIN_PACKAGE_SIZE = 1024 * 1024L
    }

    private var _githubLatestDataCache: GithubLatestData? = null

    lateinit var newVersionPackage: File
        private set

    //距上次查询已超过10分钟时才需要重新请求
    //(原属性名为skipQueryLatestInfo,含义与实现相反,极易被误改成取反而引入bug)
    private val shouldQueryLatestInfo: Boolean
        get() = System.currentTimeMillis() - latestQueryTimestamp >= 600000L

    private var latestQueryTimestamp = 0L

    //检查新版本
    suspend fun checkNewVersion(
        onFoundNewVersion: () -> Unit,
        onFail: () -> Unit,
        onNotFoundNewVersion: () -> Unit
    ) {
        if (shouldQueryLatestInfo) {
            val res = buildRequest {
                url(PaimonsNotebookApplication.latestReleaseUrl)
            }.getAsJsonNative<GithubLatestData>(getParameterizedType(GithubLatestData::class.java))

            //更新数据缓存
            _githubLatestDataCache = res
            latestQueryTimestamp = System.currentTimeMillis()

            //当最后一个版本没有内容或文件列表为空,检查更新失败
            //此处isNullOrEmpty是必须的,无网络时assets为空,getAsJsonNative内部处理了返回值res不会为空
            if (res?.assets.isNullOrEmpty() || res == null) {
                onFail.invoke()
                return
            }
        }

        checkNewVersionFromCache(
            onFoundNewVersion = onFoundNewVersion,
            onNotFoundNewVersion = onNotFoundNewVersion
        )
    }

    private fun checkNewVersionFromCache(
        onFoundNewVersion: () -> Unit,
        onNotFoundNewVersion: () -> Unit
    ) {
        if (compareVersion()) {
            onFoundNewVersion.invoke()
        } else {
            onNotFoundNewVersion.invoke()
        }
    }

    /*
    * 比对版本
    * true为需要更新,false不需要
    *
    * 原理是从tag_name中分割后面附带的version_code,如果大于当前version_code则需要更新
    * */
    private fun compareVersion(): Boolean {
        if (_githubLatestDataCache == null) return false

        val currentVersion = PaimonsNotebookApplication.versionCode
        val tagNameSplit = _githubLatestDataCache!!.tag_name.split("-")

        //当tagNameSplit的size==1时,表明没有在tag中设置版本号,直接返回false
        if (tagNameSplit.size == 1) {
            return false
        }

        val latestVersion = tagNameSplit.last().toIntOrNull() ?: 0

        return latestVersion > currentVersion
    }

    //下载新版本安装包
    suspend fun downloadNewVersionPackage(
        remoteEndpointName: String,
        onSuccess: () -> Unit,
        onFail: () -> Unit,
        onProgress: (progress: Float) -> Unit
    ) {
        //当缓存内容为空时直接返回
        if (_githubLatestDataCache == null || _githubLatestDataCache?.assets?.isEmpty() == true) {
            onFail.invoke()
            return
        }

        val requestUrl = getRequestUrlByEndpointName(remoteEndpointName)

        //用固定的ASCII文件名,不用release.name:
        //release.name是中文(如"派蒙笔记本1.8.7"),中文文件名在FileProvider与部分安装器上易出问题,
        //且每次发版name都变,会在package目录残留旧APK
        val saveFile = FileHelper.getPackageSaveFile(RELEASE_PACKAGE_FILE_NAME)

        try {
            val progressListener = object : ProgressListener {
                override fun update(
                    url: String,
                    bytesRead: Long,
                    contentLength: Long,
                    done: Boolean
                ) {
                    //contentLength可能为0(未知长度),此时按0处理避免NaN
                    onProgress.invoke(
                        if (contentLength > 0) {
                            (bytesRead.toDouble() / contentLength).toFloat().coerceIn(0f, 1f)
                        } else {
                            0f
                        }
                    )
                }
            }

            val client = OkHttpClient.Builder().addInterceptor {
                val response = it.proceed(it.request())
                //204或无响应体时body为null,原先用!!会NPE
                val body = response.body

                if (body == null) {
                    response
                } else {
                    response.newBuilder().body(
                        ProgressResponseBody(
                            response.request.url.toUrl().toString(), body, progressListener
                        )
                    ).build()
                }
            }.build()

            val res = buildRequest {
                url(requestUrl)
            }.getAsByteResult(client)

            if (res.first && res.second != null) {
                FileHelper.saveFile(saveFile, res.second!!) {}

                //校验下载结果确实是安装包:HTTP层已过滤非2xx,
                //此处再排除"返回了错误页HTML"这类200响应,避免把垃圾文件当APK
                if (!isValidPackageFile(saveFile)) {
                    saveFile.delete()
                    onFail.invoke()
                    return
                }

                newVersionPackage = saveFile

                onSuccess.invoke()
            } else {
                onFail.invoke()
            }
        } catch (_: Exception) {
            onFail.invoke()
        }
    }

    //判断下载结果是否为合法的APK(ZIP以"PK"开头,长度不能过小)
    private fun isValidPackageFile(file: File): Boolean {
        if (!file.exists() || file.length() < MIN_PACKAGE_SIZE) {
            return false
        }

        return try {
            file.inputStream().use { input ->
                val header = ByteArray(2)
                input.read(header) == 2 && header[0] == 'P'.code.toByte() && header[1] == 'K'.code.toByte()
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun getRequestUrlByEndpointName(
        name: String
    ): String {
        //本仓库release上传的资产名是PaimonsNotebook-<版本>-release.apk,
        //而不再是上游的app-release.apk;两者都接受以兼容历史release
        val asset = _githubLatestDataCache?.assets?.takeFirstIf {
            it.name == "app-release.apk" ||
                    (it.name.startsWith("PaimonsNotebook-") && it.name.endsWith(".apk"))
        }

        return when (name) {
            "github" -> {
                asset?.browser_download_url ?: ""
            }

            "mirror.ghproxy(推荐)" -> {
                //mirror.ghproxy.com 已停服,改用现行可用的 gh-proxy 镜像
                "https://gh-proxy.com/${
                    asset?.browser_download_url ?: ""
                }"
            }

            "cdn.jsdelivr" -> {
                //走本分支仓库的app/release目录(该文件已入库,jsDelivr可直取)
                "https://cdn.jsdelivr.net/gh/hlzlzlz/PaimonsNotebook/app/release/app-release.apk"
            }

            else -> {
                "选择了未知的站点".errorNotify()
                ""
            }
        }
    }
}