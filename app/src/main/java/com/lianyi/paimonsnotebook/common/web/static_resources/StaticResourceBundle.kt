package com.lianyi.paimonsnotebook.common.web.static_resources

import com.lianyi.paimonsnotebook.common.application.PaimonsNotebookApplication
import com.lianyi.paimonsnotebook.common.util.file.FileHelper
import com.lianyi.paimonsnotebook.common.util.request.ProgressResponseBody
import com.lianyi.paimonsnotebook.common.util.request.ProgressListener
import com.lianyi.paimonsnotebook.common.util.request.buildRequest
import com.lianyi.paimonsnotebook.common.util.request.getAsByteResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile

/*
* 离线图标包:批量下载官方图标压缩包并解压到本地,之后图片**完全本地读取**
*
* ## 为什么需要它
*
* 2026-09-26 实测:主图床 `static.snaphutaorp.org` 单张 72KB 图标耗时
* **21.8s / 62.2s / 137.5s**,甚至 60s 超时;而本机带宽正常
* (同期 npmmirror 1862 KB/s)。图标又是**一张一个请求**的,
* 光角色资料就有 118 个 ⇒ 一屏列表几十个并发请求,每个几十秒,页面自然像卡死。
*
* 官方的 `static/zip/{分类}.zip` 是一次性打包好的全部图标。
* **一次下载、永久本地** —— 这是唯一能根治"图床慢"的办法。
*
* ## 命名契约(照搬胡桃 Snap.Hutao.Remastered 的既有约定)
*
* 胡桃 `DownloadSummary.ExtractFilesAsync` 的做法是:
* ```
* destPath = imageCache.GetFileFromCategoryAndName(FileName, entry.FullName)
* ```
* 其中 `GetFileFromCategoryAndName(category, name)` 最终指向
* `StaticRaw(category, name)` = `{root}/static/raw/{category}/{name}`。
* ⇒ **zip 条目名就是原始文件名**(如 `UI_AvatarIcon_Qin.png`),
*   而分类由 zip 自身的名字决定(如 `AvatarIcon.zip`)。
*
* 于是本地路径可以唯一确定为:
* ```
* {bundleDir}/{category}/{fileName}
* ```
* 查表时从 URL 反解 category 与 fileName 即可
* (见 [StaticResourceSources.categoryOf])。
*
* ## 为什么不直接写进 Coil 的磁盘缓存目录
*
* 虽然 Coil 的缓存文件名算法(`encodeUtf8().sha256().hex()`)与 PN 本地读取
* 完全一致(已从 coil-base-2.6.0.aar 反编译确认),但 Coil 的 `DiskLruCache`
* **带 journal**,不认它 journal 之外的既有文件 —— 直接塞进去会被当作孤儿清理。
* 因此**另建目录**,由 PN 自己在读取时优先查找,不干扰 Coil 的缓存管理。
*
* ## 体积(实测,决定"哪些值得下载")
* ```
*   Talent.zip         11.9 MB
*   AvatarIcon.zip     16.7 MB
*   RelicIcon.zip      25.3 MB
*   MonsterIcon.zip    30.2 MB
*   EquipIcon.zip      40.5 MB
*   ItemIcon.zip      134.2 MB
*   Skill.zip         166.0 MB
* ```
* ⇒ **必须由用户按需选择,不能默认全下**(合计 400MB+)。
* */
object StaticResourceBundle {

    /*
    * 可下载的分类与实测大小(MB)
    *
    * ⚠️ 只列**实际会被列表大量展示**的几个。`Bg` / `LoadingPic` /
    *    `NameCardPic` 这类单张大图不属于"一屏几十张"的场景,下载收益低,
    *    故不列入。
    * */
    val AVAILABLE_BUNDLES: List<BundleInfo> = listOf(
        BundleInfo("Talent", "天赋图标", 11.9),
        BundleInfo("AvatarIcon", "角色图标", 16.7),
        BundleInfo("RelicIcon", "圣遗物图标", 25.3),
        BundleInfo("MonsterIcon", "怪物图标", 30.2),
        BundleInfo("EquipIcon", "武器图标", 40.5),
        BundleInfo("ItemIcon", "物品图标", 134.2)
    )

    data class BundleInfo(
        val category: String,
        val label: String,
        val sizeMb: Double
    )

    //包目录:与 Coil 的 image_cache 分开,互不干扰
    private val bundleRoot: File
        get() = PaimonsNotebookApplication.context.filesDir.resolve("static_bundle")

    fun categoryDir(category: String): File = File(bundleRoot, category)

    /*
    * 该分类是否已下载并解压完成
    *
    * 用"标记文件"判定,而不是"目录非空" —— 解压中途失败会留下半个目录,
    * 若非空即算完成,用户会看到一半图标本地命中、一半仍然走慢图床,
    * 且**再也不会重试**(因为判定为已完成)。
    * */
    fun isBundleReady(category: String): Boolean =
        File(categoryDir(category), READY_MARKER).exists()

    /*
    * 给定图片 URL,返回本地已就绪的图标文件;没有则返回 null
    *
    * 非静态资源(用户帖子里外链的图等)一律返回 null —— 那些图不在图标包里。
    * */
    fun localFileForUrl(url: String): File? {
        val category = StaticResourceSources.categoryOf(url)
        if (category.isBlank() || !isBundleReady(category)) {
            return null
        }

        val fileName = url.substringAfterLast('/').substringBefore('?')
        if (fileName.isBlank()) {
            return null
        }

        /*
        * ⚠️ 只取文件名并做**路径穿越防护**:fileName 来自 URL,
        *    若含 `../` 会读到包目录之外。
        * */
        if (fileName.contains("..") || fileName.contains('/') || fileName.contains('\\')) {
            return null
        }

        val file = File(categoryDir(category), fileName)
        return if (file.isFile && file.length() > 0) file else null
    }

    /*
    * 解压已下载的 zip 到包目录
    *
    * @param zipFile 下载好的压缩包(调用方负责下载与删除临时文件)
    * @return 解压出的文件数;异常时抛出让调用方提示
    * */
    fun extract(category: String, zipFile: File): Int {
        val targetDir = categoryDir(category)

        //先清空旧内容,避免残留上次解压到一半的文件
        if (targetDir.exists()) {
            targetDir.deleteRecursively()
        }
        targetDir.mkdirs()

        var count = 0

        ZipFile(zipFile).use { zip ->
            val entries = zip.entries()

            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.isDirectory) continue

                /*
                * 只取最后一段作为文件名 —— 条目名可能是 `UI_x.png` 也可能是
                * `AvatarIcon/UI_x.png`,两种都归一化到同一个本地路径,
                * 保证 [localFileForUrl] 的查表逻辑唯一。
                * */
                val name = entry.name.substringAfterLast('/').substringAfterLast('\\')
                if (name.isBlank() || name.contains("..")) continue

                val out = File(targetDir, name)

                //再用 canonicalPath 复核一次,确保没有逃出包目录
                val canon = runCatching { out.canonicalPath }.getOrNull() ?: continue
                val rootCanon = targetDir.canonicalPath
                if (!canon.startsWith(rootCanon + File.separator)) continue

                zip.getInputStream(entry).use { input ->
                    out.outputStream().use { output -> input.copyTo(output) }
                }

                if (out.length() > 0) count++
            }
        }

        /*
        * ⚠️ 标记文件必须在**全部解压成功之后**才写 —— 它是"完成"的唯一凭据。
        * */
        if (count > 0) {
            File(targetDir, READY_MARKER).writeText("")
        }

        return count
    }

    //删除某个分类的包(释放空间)
    fun deleteBundle(category: String) {
        categoryDir(category).deleteRecursively()
    }

    //已占用空间(字节)
    fun usedBytes(): Long =
        bundleRoot.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    /*
    * 删除包目录(设置页"清空离线图标"用)
    *
    * 注意:这与 [deleteBundle] 的区别是它不碰 Coil 的 image_cache ——
    * 用户只是想释放这批离线资源,不该顺手清掉日常浏览的缓存。
    * */
    fun deleteAll() {
        bundleRoot.deleteRecursively()
    }

    /*
    * 下载并解压一个分类
    *
    * 提供**多源重试**:主图床实测极不稳定(21.8s~137.5s、甚至超时),
    * 故镜像优先,主图床兜底 —— 与图片加载的候选顺序相反,
    * 因为这里是一次性大文件,镜像的稳定性比"少一次跳转"更重要。
    *
    * @param onProgress 已下载字节数 / 总字节数(总长未知时为 -1)
    * @return 解压出的文件数
    * */
    suspend fun downloadAndExtract(
        category: String,
        onProgress: (downloaded: Long, total: Long) -> Unit = { _, _ -> }
    ): Int = withContext(Dispatchers.IO) {
        val candidates = StaticResourceSources.zipCandidateUrls(category)

        var lastError: Exception? = null

        for (url in candidates) {
            try {
                val temp = File(
                    PaimonsNotebookApplication.context.cacheDir,
                    "bundle_$category.zip"
                )
                if (temp.exists()) temp.delete()

                val progressListener = object : ProgressListener {
                    override fun update(
                        url: String,
                        bytesRead: Long,
                        contentLength: Long,
                        done: Boolean
                    ) {
                        onProgress(bytesRead, contentLength)
                    }
                }

                val client = OkHttpClient.Builder()
                    //大文件必须放宽超时:16MB 以上在慢网络下 60s 不够
                    .readTimeout(10, TimeUnit.MINUTES)
                    .callTimeout(30, TimeUnit.MINUTES)
                    .addInterceptor { chain ->
                        val response = chain.proceed(chain.request())
                        val body = response.body

                        if (body == null) {
                            response
                        } else {
                            response.newBuilder()
                                .body(ProgressResponseBody(url, body, progressListener))
                                .build()
                        }
                    }
                    .build()

                val res = buildRequest { url(url) }.getAsByteResult(client)
                if (!res.first || res.second == null) {
                    lastError = IOException("下载失败: $url")
                    continue
                }

                FileHelper.saveFile(temp, res.second!!) { onProgress(it, -1) }

                /*
                * 校验下载结果是 zip 而不是错误页 ——
                * 与 UpdateService 校验 APK 同一道理:HTTP 200 也可能返回 HTML。
                * */
                if (!isZipFile(temp)) {
                    temp.delete()
                    lastError = IOException("返回内容不是压缩包: $url")
                    continue
                }

                val count = extract(category, temp)
                temp.delete()

                if (count <= 0) {
                    lastError = IOException("压缩包内没有可解压的文件: $url")
                    continue
                }

                return@withContext count
            } catch (e: Exception) {
                //单个源失败继续试下一个
                lastError = e
            }
        }

        throw lastError ?: IOException("全部下载源均失败: $category")
    }

    //是否为 zip(PK\x03\x04 魔数)
    private fun isZipFile(file: File): Boolean {
        if (!file.isFile || file.length() < 4) return false

        return try {
            val header = ByteArray(4)
            file.inputStream().use { it.read(header) }
            header[0] == 0x50.toByte() && header[1] == 0x4B.toByte()
        } catch (e: Exception) {
            false
        }
    }

    //标记文件名
    private const val READY_MARKER = ".ready"
}
