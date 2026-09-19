package com.lianyi.paimonsnotebook

import com.lianyi.paimonsnotebook.common.service.daily_note_notify.DailyNoteWebhook
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/*
* 便笺 Webhook 地址校验的回归测试
*
* 为什么值得测:校验发生在"用户填地址"和"每轮后台推送"两处。
* 若放行了非法地址(file:// 等),OkHttp 会在后台 Worker 里抛异常;
* 若误拦了合法地址,用户会以为功能坏了却没有任何提示。
* 两者都难以在真机上覆盖(需要后台周期触发)。
* */
class DailyNoteWebhookTest {

    @Test
    fun `合法的http与https地址应通过`() {
        assertTrue(DailyNoteWebhook.isValidUrl("http://example.com/webhook"))
        assertTrue(DailyNoteWebhook.isValidUrl("https://example.com/webhook"))
        assertTrue(DailyNoteWebhook.isValidUrl("https://example.com:8080/a/b?c=d"))
        assertTrue(DailyNoteWebhook.isValidUrl("https://192.168.1.6:8123/api/webhook/abc"))
        //大小写不敏感
        assertTrue(DailyNoteWebhook.isValidUrl("HTTPS://EXAMPLE.COM/hook"))
    }

    @Test
    fun `首尾空白应被容忍`() {
        //用户从别处复制粘贴常带空格
        assertTrue(DailyNoteWebhook.isValidUrl("  https://example.com/hook  "))
    }

    @Test
    fun `空值表示未配置不算合法地址`() {
        //空串是"关闭推送"的表示,不是错误,但也不该被当成可推送地址
        assertFalse(DailyNoteWebhook.isValidUrl(null))
        assertFalse(DailyNoteWebhook.isValidUrl(""))
        assertFalse(DailyNoteWebhook.isValidUrl("   "))
    }

    @Test
    fun `非http协议的地址应被拒绝`() {
        //这些 scheme 会让 OkHttp 抛异常,必须在推送前挡掉
        assertFalse(DailyNoteWebhook.isValidUrl("file:///sdcard/a.json"))
        assertFalse(DailyNoteWebhook.isValidUrl("content://com.example/x"))
        assertFalse(DailyNoteWebhook.isValidUrl("ftp://example.com/a"))
        assertFalse(DailyNoteWebhook.isValidUrl("javascript:alert(1)"))
    }

    @Test
    fun `只有协议没有主机的地址应被拒绝`() {
        //"http://" 这种写法 OkHttp 会报错,提前拦掉
        assertFalse(DailyNoteWebhook.isValidUrl("http://"))
        assertFalse(DailyNoteWebhook.isValidUrl("https://"))
        assertFalse(DailyNoteWebhook.isValidUrl("http:// "))
    }

    @Test
    fun `裸域名与相对路径应被拒绝`() {
        assertFalse(DailyNoteWebhook.isValidUrl("example.com/hook"))
        assertFalse(DailyNoteWebhook.isValidUrl("/api/webhook"))
        assertFalse(DailyNoteWebhook.isValidUrl("example"))
    }
}
