package com.lianyi.paimonsnotebook

import com.lianyi.paimonsnotebook.common.web.bridge.WebViewUrlAllowlist
import org.junit.Assert.assertFalse
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
}
