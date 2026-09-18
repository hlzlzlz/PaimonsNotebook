package com.lianyi.paimonsnotebook.common.web.bridge

import android.net.Uri

/*
* WebView 桥接的可信来源判定
*
* addJavascriptInterface 注入的对象对 WebView 当前加载的任意页面可见,
* 而承载该桥的 HoyolabWebActivity 允许导航到外部链接(帖子内超链接、
* 服务器下发的 page)。因此凡是会泄露账号凭证或设备指纹的桥接方法,
* 都必须先确认调用方来自官方域。
*
* 单独抽成 object 是为了让域名匹配逻辑可被单元测试覆盖
* (见 app/src/test/.../WebViewUrlAllowlistTest.kt)。
* */
object WebViewUrlAllowlist {

    //完整匹配的域名
    private val ALLOWED_HOSTS = setOf(
        "mihoyo.com",
        "miyoushe.com",
        "hoyolab.com",
    )

    /*
    * 后缀匹配的域名。
    * 前导点不能省略:它保证 evil-mihoyo.com / mihoyo.com.evil.com 这类
    * 伪装域名不会命中(前者不以".mihoyo.com"结尾,后者以".evil.com"结尾)
    * */
    private val ALLOWED_HOST_SUFFIXES = listOf(
        ".mihoyo.com",
        ".miyoushe.com",
        ".hoyolab.com",
    )

    /*
    * 判断主机名是否属于官方域。
    *
    * 传入的必须是已解析出的 host(不含 userinfo/端口/路径),
    * 例如 "api-takumi.mihoyo.com",而不是整个 url。
    * */
    fun isTrustedHost(host: String?): Boolean {
        if (host.isNullOrBlank()) return false

        val normalized = host.lowercase()

        return ALLOWED_HOSTS.contains(normalized) ||
                ALLOWED_HOST_SUFFIXES.any { normalized.endsWith(it) }
    }

    /*
    * 从 url 解析 host 后判断是否可信。
    *
    * 用 Uri.parse 而不是手工切字符串:它能正确处理 userinfo
    * (https://user@evil.com)与端口(https://mihoyo.com:8080),
    * 手工解析很容易被这两种形式绕过。
    * */
    fun isTrustedUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false

        val host = try {
            Uri.parse(url).host
        } catch (e: Exception) {
            null
        }

        return isTrustedHost(host)
    }
}
