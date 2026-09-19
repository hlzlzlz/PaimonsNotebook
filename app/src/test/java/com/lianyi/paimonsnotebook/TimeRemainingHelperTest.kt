package com.lianyi.paimonsnotebook

import com.lianyi.paimonsnotebook.common.util.time.TimeRemainingHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/*
* 剩余时间文案的回归测试
*
* 为什么值得单测:这类"倒计时"文案的边界分支(已结束/不足1分钟/不足1小时/
* 不足1天/跨天)在真机上极难覆盖 —— 你得等到每种时间点各测一次。
* 而算错的表现很隐蔽:显示"剩余0天"或把已结束的显示成剩余时间。
*
* 时区注意:服务端时间固定按北京时间(+08:00)解析,故用例里的期望值
* 都是按 +08:00 推算的,与本机时区无关。
* */
class TimeRemainingHelperTest {

    //2026-09-19 12:00:00 (北京时间) 对应的 Unix 秒
    private val base = TimeRemainingHelper.parseServerTimeToEpochSecond("2026-09-19 12:00:00")!!

    @Test
    fun `服务端时间按北京时间解析`() {
        //与 +08:00 手工推算一致:2026-09-19 12:00 +08:00 = 2026-09-19 04:00 UTC
        val expected = java.time.LocalDateTime.of(2026, 9, 19, 12, 0, 0)
            .toEpochSecond(java.time.ZoneOffset.ofHours(8))

        assertEquals(expected, base)
    }

    @Test
    fun `非法或空输入返回null而不是0`() {
        //返回 0 是危险的:0 是合法时间戳(1970),会被算成"早已结束"
        assertNull(TimeRemainingHelper.parseServerTimeToEpochSecond(null))
        assertNull(TimeRemainingHelper.parseServerTimeToEpochSecond(""))
        assertNull(TimeRemainingHelper.parseServerTimeToEpochSecond("   "))
        assertNull(TimeRemainingHelper.parseServerTimeToEpochSecond("not a date"))
        assertNull(TimeRemainingHelper.parseServerTimeToEpochSecond("2026-13-45 99:99:99"))
    }

    @Test
    fun `已结束返回null由调用方隐藏该行`() {
        //结束时间早于当前
        assertNull(TimeRemainingHelper.formatRemaining("2026-09-19 12:00:00", base + 1))
        //正好等于当前时刻也算已结束
        assertNull(TimeRemainingHelper.formatRemaining("2026-09-19 12:00:00", base))
        //解析失败同样返回 null
        assertNull(TimeRemainingHelper.formatRemaining("bad", base))
    }

    @Test
    fun `不足一分钟显示秒`() {
        assertEquals("剩余30秒", TimeRemainingHelper.formatRemaining("2026-09-19 12:00:30", base))
        assertEquals("剩余59秒", TimeRemainingHelper.formatRemaining("2026-09-19 12:00:59", base))
    }

    @Test
    fun `不足一小时显示分钟`() {
        //30 分钟
        assertEquals("剩余30分钟", TimeRemainingHelper.formatRemaining("2026-09-19 12:30:00", base))
        //59 分 59 秒仍按分钟(向下取整)
        assertEquals("剩余59分钟", TimeRemainingHelper.formatRemaining("2026-09-19 12:59:59", base))
    }

    @Test
    fun `不足一天显示小时与分钟`() {
        assertEquals("剩余5小时", TimeRemainingHelper.formatRemaining("2026-09-19 17:00:00", base))
        assertEquals("剩余5小时30分钟", TimeRemainingHelper.formatRemaining("2026-09-19 17:30:00", base))
        //23 小时
        assertEquals("剩余23小时", TimeRemainingHelper.formatRemaining("2026-09-20 11:00:00", base))
    }

    @Test
    fun `跨天显示天与小时`() {
        assertEquals("剩余1天", TimeRemainingHelper.formatRemaining("2026-09-20 12:00:00", base))
        assertEquals("剩余1天6小时", TimeRemainingHelper.formatRemaining("2026-09-20 18:00:00", base))
        assertEquals("剩余10天", TimeRemainingHelper.formatRemaining("2026-09-29 12:00:00", base))
    }

    /*
    * 回归:深渊本期通常 15 天左右,确保这个量级不会出现"剩余0天"这类退化文案。
    * */
    @Test
    fun `深渊量级的剩余时间文案正常`() {
        val text = TimeRemainingHelper.formatRemaining("2026-10-04 03:59:59", base)

        assertNotNull(text)
        assertTrue("不应出现0天: $text", !text!!.contains("剩余0天"))
        assertTrue("应以'剩余'开头: $text", text.startsWith("剩余"))
    }
}
