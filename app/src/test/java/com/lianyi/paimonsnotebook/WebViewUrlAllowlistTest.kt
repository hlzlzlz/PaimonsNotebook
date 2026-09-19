package com.lianyi.paimonsnotebook

import com.lianyi.paimonsnotebook.common.web.bridge.WebViewUrlAllowlist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/*
* WebView 桥接来源校验的回归测试
*
* 背景:HoyolabWebActivity 把 MiHoYoJSInterface 注入到 WebView,
* 而该 Activity 允许导航到外部链接。桥接的敏感方法会返回 ltoken/LTuid、
* device_fp、DS 签名,并能用 stoken 换出新的 cookie_token,
* 所以来源判定一旦失效等同于账号被接管。这里锁死域名匹配行为。
*
* 只测 isTrustedHost(纯 Kotlin 逻辑)。isTrustedUrl 依赖 android.net.Uri,
* 而本模块的单元测试没有 Robolectric(见 app/build.gradle),
* 在 JVM 上调用 Uri.parse 会抛 "not mocked",故不在此覆盖。
* */
class WebViewUrlAllowlistTest {

    @Test
    fun 官方域应被信任() {
        assertTrue(WebViewUrlAllowlist.isTrustedHost("mihoyo.com"))
        assertTrue(WebViewUrlAllowlist.isTrustedHost("miyoushe.com"))
        assertTrue(WebViewUrlAllowlist.isTrustedHost("hoyolab.com"))

        //实际会被加载的官方子域
        assertTrue(WebViewUrlAllowlist.isTrustedHost("api-takumi.mihoyo.com"))
        assertTrue(WebViewUrlAllowlist.isTrustedHost("webstatic.mihoyo.com"))
        assertTrue(WebViewUrlAllowlist.isTrustedHost("bbs-api.miyoushe.com"))
        assertTrue(WebViewUrlAllowlist.isTrustedHost("api-takumi-record.mihoyo.com"))
        assertTrue(WebViewUrlAllowlist.isTrustedHost("www.hoyolab.com"))
    }

    @Test
    fun 大小写不敏感() {
        assertTrue(WebViewUrlAllowlist.isTrustedHost("API-TAKUMI.MIHOYO.COM"))
        assertTrue(WebViewUrlAllowlist.isTrustedHost("Webstatic.MiHoYo.Com"))
    }

    @Test
    fun 伪装域名不应被信任() {
        //后缀匹配必须带前导点,否则这些都会被误判为可信
        assertFalse(WebViewUrlAllowlist.isTrustedHost("evil-mihoyo.com"))
        assertFalse(WebViewUrlAllowlist.isTrustedHost("notmihoyo.com"))
        assertFalse(WebViewUrlAllowlist.isTrustedHost("mihoyo.com.evil.com"))
        assertFalse(WebViewUrlAllowlist.isTrustedHost("miyoushe.com.attacker.net"))
        assertFalse(WebViewUrlAllowlist.isTrustedHost("fakehoyolab.com"))
    }

    @Test
    fun 完全无关的域名不应被信任() {
        assertFalse(WebViewUrlAllowlist.isTrustedHost("example.com"))
        assertFalse(WebViewUrlAllowlist.isTrustedHost("github.com"))
        assertFalse(WebViewUrlAllowlist.isTrustedHost("localhost"))
        assertFalse(WebViewUrlAllowlist.isTrustedHost("192.168.1.1"))
    }

    @Test
    fun 空值与空串不应被信任() {
        assertFalse(WebViewUrlAllowlist.isTrustedHost(null))
        assertFalse(WebViewUrlAllowlist.isTrustedHost(""))
        assertFalse(WebViewUrlAllowlist.isTrustedHost("   "))
    }

    /*
    * resolvePageUrl 决定"按哪个url判定来源"。
    *
    * 它存在的理由是:桥接方法运行在JavaBridge后台线程,不能在该线程读
    * webView.url(getUrl()会checkThread()并抛RuntimeException)。
    * 因此改由主线程上报url,本函数负责选择用哪一个。
    * 选择逻辑一旦写反(例如让首次url盖过最新上报),就会出现
    * "导航到第三方页后仍被判为可信"的漏放,故在此锁死。
    * */
    @Test
    fun 优先使用主线程上报的url() {
        assertEquals(
            "https://bbs.mihoyo.com/article/1",
            WebViewUrlAllowlist.resolvePageUrl(
                reportedUrl = "https://bbs.mihoyo.com/article/1",
                initialUrl = "https://webstatic.mihoyo.com/app/community-game-records/index.html"
            )
        )
    }

    @Test
    fun 上报为空白时回退到首次url() {
        val initial = "https://webstatic.mihoyo.com/x"

        assertEquals(initial, WebViewUrlAllowlist.resolvePageUrl(null, initial))
        assertEquals(initial, WebViewUrlAllowlist.resolvePageUrl("", initial))
        assertEquals(initial, WebViewUrlAllowlist.resolvePageUrl("   ", initial))
    }

    @Test
    fun 两者都缺失时返回null() {
        assertNull(WebViewUrlAllowlist.resolvePageUrl(null, null))
    }

    /*
    * 关键安全断言:导航到第三方页后,判定依据必须是该第三方url,
    * 而不是仍停留在首次的官方url(否则敏感方法会被漏放)。
    * */
    @Test
    fun 导航到第三方域后判定依据应随之改变() {
        val official = "https://webstatic.mihoyo.com/x"

        val resolved = WebViewUrlAllowlist.resolvePageUrl(
            reportedUrl = "https://evil.example.com/steal",
            initialUrl = official
        )

        assertEquals("https://evil.example.com/steal", resolved)
        assertFalse(WebViewUrlAllowlist.isTrustedHost("evil.example.com"))
    }
}
