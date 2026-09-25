package com.lianyi.paimonsnotebook.common.web.static_resources

/*
* 静态资源(图标)的候选地址与分类白名单
*
* ## 背景(2026-09-26 实测)
*
* 主图床 `static.snaphutaorp.org` 实测**极不稳定**:
*   同一张 72KB 图标,连续三次请求耗时 **21.8s / 62.2s / 137.5s**,
*   之后一次直接 **60s 超时**;而同期本机带宽正常
*   (npmmirror 5.6MB/3.0s ≈ 1862 KB/s,baidu 194ms)。
*   ⇒ **瓶颈在图床,不在用户网络,也不在应用代码。**
*
* 而原 `ImageFallbackInterceptor` 有两个真实缺陷:
*   1. **超时不会触发兜底** —— 它只判断 `response.isSuccessful`,
*      而 `chain.proceed` 超时时**抛异常**,根本走不到判断那一行。
*      偏偏当前图床的主要症状就是超时 ⇒ 兜底形同虚设。
*   2. **只有 enka 一个兜底,且 enka 自身也慢**(实测 7.9s / 23.6s / 12.7s)。
*
* 本文件把"该按什么顺序尝试哪些地址"抽成**纯函数**,便于单测;
* 重试与超时处理在 `ImageFallbackInterceptor` 里。
*
* ## 为什么镜像要放前面、enka 放最后
*
* 镜像(`static.hutaorp.org`)与主图床**路径结构完全相同**,能覆盖全部分类
* (含 MonsterIcon / LoadingPic);而 enka 是按**原始文件名**提供的,
* **只有 UI_ 开头的那批**,没有 MonsterIcon 与 LoadingPic 分类。
* 所以 enka 必须是最后手段,且要按分类过滤(见 [enkaSupportsCategory])。
*
* ⚠️ 原代码的注释自称"不含 MonsterIcon 与 LoadingPic 分类",
*    但**实现里没有任何分类过滤**(只判了 `.png` 后缀)—— 注释与实现不符。
*    这里把过滤真正实现出来,让注释成立。
* */
object StaticResourceSources {

    //主图床
    const val PRIMARY_HOST = "static.snaphutaorp.org"

    /*
    * 镜像主机(路径结构与主图床一致)
    *
    * ⚠️ `static.hutaorp.org` 是 2026-09-26 实测可用(HTTP 200, 4.1s)的社区镜像;
    *    胡桃自身的统计接口备用域也是 `hutaorp.org`(见 code-map 的 statistics 段),
    *    属同一套设施的备用域。**未验证它是否含全部分类** —— 故它排在 enka 之前、
    *    主图床之后:即便某个分类缺失,也只是那一档失败,不会比现状更差。
    * */
    private val MIRROR_HOSTS = listOf("static.hutaorp.org")

    //enka 的图片前缀(按原始文件名提供)
    private const val ENKA_UI_PREFIX = "https://enka.network/ui/"

    /*
    * enka 明确没有的分类(原注释的说法,现予以落实)
    *
    * `MonsterIcon`(怪物图标)与 `LoadingPic`(加载图)不在 enka 的 `ui/` 目录下。
    * */
    private val ENKA_UNSUPPORTED_CATEGORIES = setOf("MonsterIcon", "LoadingPic")

    /*
    * 给定一个静态资源地址,返回**按优先级排列**的候选地址
    *
    * 非静态资源地址(host 不是主图床)原样返回单元素列表 ——
    * 不去改写用户或其它来源的 URL。
    * */
    fun candidateUrls(url: String): List<String> {
        if (!isStaticResource(url)) {
            return listOf(url)
        }

        val candidates = mutableListOf(url)

        //镜像:仅替换 host,路径保持不变
        MIRROR_HOSTS.forEach { host ->
            candidates += replaceHost(url, host)
        }

        /*
        * enka 兜底:仅当分类受支持时加入。
        * 取最后一个路径段作为文件名(enka 用的就是原始文件名)。
        * */
        val category = categoryOf(url)
        val fileName = url.substringAfterLast('/')

        if (fileName.endsWith(".png") && enkaSupportsCategory(category)) {
            candidates += ENKA_UI_PREFIX + fileName
        }

        return candidates
    }

    //是否为主图床的静态资源
    fun isStaticResource(url: String): Boolean =
        url.contains("//$PRIMARY_HOST/")

    /*
    * 图标压缩包(`static/zip/{分类}.zip`)的候选地址
    *
    * ⚠️ 顺序与**单图**候选**相反**:这里把镜像放在最前。
    *    两者场景不同 ——
    *      - 单图:几十张并发、重试代价只是一次小请求,
    *        主图床偶尔能用时先试它可以少一次跳转;
    *      - 压缩包:一次 12~40MB 的大文件,失败要**重下整个包**,
    *        稳定性远比"少一次跳转"重要,而实测主图床正是最不稳的那个。
    * */
    fun zipCandidateUrls(category: String): List<String> {
        val path = "/static/zip/$category.zip"
        return MIRROR_HOSTS.map { "https://$it$path" } + "https://$PRIMARY_HOST$path"
    }

    /*
    * 取出分类名
    *
    * 形如 `https://static.snaphutaorp.org/static/raw/AvatarIcon/UI_x.png`
    * 的分类是 `AvatarIcon`(路径段的**倒数第二**段)。
    * 取不到时返回空串。
    * */
    fun categoryOf(url: String): String {
        val segments = url.substringAfter("//").substringAfter('/', "").split('/')
        //去掉最后的文件名后取最后一段
        return segments.dropLast(1).lastOrNull { it.isNotBlank() } ?: ""
    }

    //enka 是否提供该分类
    fun enkaSupportsCategory(category: String): Boolean =
        category.isNotBlank() && category !in ENKA_UNSUPPORTED_CATEGORIES

    /*
    * 把 URL 的 host 换成另一个(保留 scheme、端口、路径与查询串)
    *
    * 抽成纯函数是因为"替换 host"用字符串拼接极易出错
    * (例如把 `://` 也一起替换掉,或丢掉路径)。
    * */
    private fun replaceHost(url: String, newHost: String): String {
        val schemeEnd = url.indexOf("://")
        if (schemeEnd < 0) return url

        val scheme = url.substring(0, schemeEnd + 3)
        val rest = url.substring(schemeEnd + 3)
        val pathStart = rest.indexOf('/')

        return if (pathStart < 0) {
            scheme + newHost
        } else {
            scheme + newHost + rest.substring(pathStart)
        }
    }
}
