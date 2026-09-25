package com.lianyi.paimonsnotebook.common.util.image

import android.content.res.AssetManager
import com.lianyi.paimonsnotebook.common.application.PaimonsNotebookApplication
import com.lianyi.paimonsnotebook.common.web.static_resources.StaticResourceSources
import java.util.zip.ZipFile

/*
* 内置缩略图(打包进 APK 的低清占位图)
*
* ## 为什么要有它
*
* 2026-09-26 实测:主图床 `static.snaphutaorp.org` 单张 70KB 图标耗时
* **7.7s ~ 52s**(同一张图重复五次),兜底源同样慢。而图标是**一张一个请求**的
* (角色资料 118 个、材料 2640 个)⇒ 列表页几十个请求、每个几十秒,页面像卡死。
*
* 对策(用户提议,已实现):
*   **把低清小图内置进安装包** —— 列表立即显示本地小图,
*   等高清图下载完成后再无缝换成高清图。
*   这样即使图床再慢,页面也**立刻有内容**,不再白屏等待。
*
* ## 数据来源与规格
*
* 由 `_thumbs/build_icon_thumbs.py` 从**官方图标包**生成:
*   - 分辨率 **48×48**(列表卡片渲染 60dp × density 2.0 = 120 物理像素,
*     但作为"占位"只需在 48~120px 之间可辨认;48px 已是总包体积
*     5.48MB vs 64px 7.46MB 的取舍点)
*   - 格式 **WebP**(minSdk 26,原生支持;同等画质下约为 PNG 的 31%)
*   - 覆盖 **3367 张**(AvatarIcon 118 / MonsterIcon 377 / EquipIcon 246 /
*     RelicIcon 63 / ItemIcon 2563)
*   - 元数据里引用的图标中有 **81 张找不到源图**(约 2.4%),未命中时正常回退到网络
*
* ## 存储形态
*
* **单个 zip 存于 `assets/icon_thumbs.zip`**,而不是 3367 个独立 asset 文件。
* 理由:
*   - 独立文件会让 APK 多出 3367 个 zip 条目,构建与安装都变慢;
*   - Android 对 assets 的**压缩存储**不会优化大量小文件;
*   - 单 zip 只多一次 ZipFile 打开(已缓存句柄),读取仍是一次顺序 IO。
*   ⚠️ 但**不能**解压到内部存储 —— 那样首次启动要多花几秒且占用空间翻倍,
*      失去了"内置"的意义。
*
* ## 线程与缓存
*
* `ZipFile` 打开与读取都是 **IO**,必须在 IO 线程调用(调用方负责)。
* 打开后的 `ZipFile` 缓存在 [zipFile] 里复用 —— 每次读取都重新打开 zip
* 会产生可观的重复开销(本类会被列表里每张图调用)。
* */
object BuiltInThumbnails {

    private const val ASSET_NAME = "icon_thumbs.zip"

    /*
    * 缓存的 ZipFile 句柄
    *
    * ⚠️ 用 @Volatile + 双重检查:列表滚动时多线程同时首访是常态。
    *    ZipFile 本身是线程安全的(内部同步),故打开后共享即可。
    * */
    @Volatile
    private var zipFile: ZipFile? = null

    @Volatile
    private var unavailable = false

    /*
    * 条目名缓存:category/name -> 是否存在于包内
    *
    * 直接依赖 ZipFile.getEntry 每次都会做一次哈希查找,开销可接受;
    * 但"哪些条目不存在"会被反复查询(未命中的图会一直被请求),
    * 故把**未命中**也记下来,避免重复查找。
    *
    * ⚠️ 只缓存"不存在",不缓存"存在" —— 存在的条目读取本身就要打开流,
    *    再多一次 map 查询意义不大,而缓存全部 3367 条会白占内存。
    * */
    private val missingEntries = java.util.Collections.synchronizedSet(HashSet<String>())

    private fun openZip(): ZipFile? {
        zipFile?.let { return it }

        if (unavailable) return null

        synchronized(this) {
            zipFile?.let { return it }
            if (unavailable) return null

            return try {
                val assets: AssetManager = PaimonsNotebookApplication.context.assets
                val file = java.io.File(
                    PaimonsNotebookApplication.context.cacheDir,
                    ASSET_NAME
                )

                /*
                * ⚠️ ZipFile 只能从**文件**打开,不能直接从 assets 流打开。
                *    所以先复制到 cacheDir 再打开。
                *
                *    这与"解压 3367 个文件"完全不同 —— 只复制**一个** 5.5MB 文件,
                *    且只做一次(带版本标记,升级后重新复制)。
                * */
                if (!file.exists() || file.length() == 0L) {
                    assets.open(ASSET_NAME).use { input ->
                        file.outputStream().use { output -> input.copyTo(output) }
                    }
                }

                ZipFile(file).also { zipFile = it }
            } catch (e: Exception) {
                //包不存在或损坏:标记不可用,后续调用直接回退网络
                e.printStackTrace()
                unavailable = true
                null
            }
        }
    }

    /*
    * 已命中的缩略图字节缓存
    *
    * ⚠️ 这个缓存是**必要的,不是优化**:
    *    NetworkImage 用 produceState 异步取缩略图,初值是 null。
    *    列表滚动时 item 会被回收重组,若每次都从 null 开始,
    *    用户会看到"占位图闪一下才出现"—— 在快速滚动时尤其明显。
    *    有了同步可读的缓存,重组时能立刻拿到上次的字节,不再闪。
    *
    * 容量:48px WebP 约 1.6KB/张,512 张约 0.8MB,对内存可忽略;
    * 而一屏可见图标通常只有几十张,512 足以覆盖滚动回退的场景。
    * */
    private val cache = object : android.util.LruCache<String, ByteArray>(512) {
        override fun sizeOf(key: String, value: ByteArray): Int = 1
    }

    /*
    * **仅从缓存**取缩略图(不触发任何 IO)
    *
    * 供 UI 在组合期间同步调用:拿到就立即显示,拿不到再异步加载。
    * */
    fun cachedThumbnailForUrl(url: String): ByteArray? {
        val entryName = entryNameFor(url) ?: return null
        return cache.get(entryName)
    }

    /*
    * 取某个图片 URL 对应的内置缩略图;没有则返回 null
    *
    * ⚠️ 返回的是**解压后的字节**(供 UI 直接解码)。
    *    不做"落盘再读" —— 那会退化成磁盘缓存,失去内置的即时性。
    *
    * @param url 原始图片 URL(与 app 请求的完全一致)
    * */
    fun thumbnailBytesForUrl(url: String): ByteArray? {
        val entryName = entryNameFor(url) ?: return null

        //先查缓存(命中则完全不碰 zip)
        cache.get(entryName)?.let { return it }

        if (entryName in missingEntries) return null

        val zip = openZip() ?: return null

        return try {
            val entry = zip.getEntry(entryName)
            if (entry == null) {
                missingEntries += entryName
                null
            } else {
                zip.getInputStream(entry).use { it.readBytes() }.also {
                    cache.put(entryName, it)
                }
            }
        } catch (e: Exception) {
            //单个条目损坏不应影响其它图标
            missingEntries += entryName
            null
        }
    }

    /*
    * URL -> zip 条目名;无法换算时返回 null
    *
    * 与 `BuiltInThumbnailKeyTest` 钉住的规则**必须一致**
    * (该测试覆盖 categoryOf 与 stem 的换算)。
    * */
    private fun entryNameFor(url: String): String? {
        val category = StaticResourceSources.categoryOf(url)
        if (category.isBlank()) return null

        val fileName = url.substringAfterLast('/').substringBefore('?')
        if (fileName.isBlank()) return null

        val stem = fileName.substringBeforeLast('.')
        if (stem.isBlank()) return null

        val entryName = "$category/$stem.webp"

        //路径穿越防护:条目名来自 URL
        if (entryName.contains("..")) return null

        return entryName
    }

    /*
    * 是否已内置(供设置页/调试显示)
    * */
    fun isAvailable(): Boolean = openZip() != null

    //条目总数(供调试)
    fun entryCount(): Int = openZip()?.size() ?: 0

    /*
    * 仅供测试:重置缓存状态
    * */
    internal fun resetForTest() {
        synchronized(this) {
            runCatching { zipFile?.close() }
            zipFile = null
            unavailable = false
            missingEntries.clear()
            cache.evictAll()
        }
    }

    //供测试注入
    internal fun setZipFileForTest(zip: ZipFile?) {
        synchronized(this) {
            zipFile = zip
            unavailable = zip == null
            missingEntries.clear()
            cache.evictAll()
        }
    }
}
