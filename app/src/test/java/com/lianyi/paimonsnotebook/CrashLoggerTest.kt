package com.lianyi.paimonsnotebook

import com.lianyi.paimonsnotebook.common.util.log.CrashLogger
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/*
* 崩溃日志格式化的回归测试
*
* 只测纯函数 formatEntry —— logFile()/hasPendingCrash() 依赖 Context,
* 在纯 JVM 下不可用(与本项目既有的"单测分支 vs 真机分支"约束一致:
* 平台相关部分不放进单测,把可测逻辑抽成纯函数)。
* */
class CrashLoggerTest {

    private class Boom(message: String) : RuntimeException(message)

    private fun entry(
        throwable: Throwable = Boom("炸了"),
        threadName: String = "main",
        header: String = "app=27 android=27 device=test/model"
    ) = CrashLogger.formatEntry(
        timeMillis = 1_700_000_000_000L,
        threadName = threadName,
        throwable = throwable,
        header = header
    )

    @Test
    fun 日志条目包含异常类名与消息() {
        val text = entry()

        assertTrue(
            "必须含异常类名,否则无法区分崩溃类型",
            text.contains("com.lianyi.paimonsnotebook.CrashLoggerTest\$Boom")
        )
        assertTrue("必须含异常消息", text.contains("炸了"))
    }

    @Test
    fun 日志条目包含完整堆栈() {
        val text = entry()

        //堆栈里必须有方法名,否则这份日志对定位毫无价值
        assertTrue(
            "必须含堆栈帧",
            text.contains("at com.lianyi.paimonsnotebook.CrashLoggerTest")
        )
    }

    @Test
    fun 日志条目包含线程名() {
        val text = entry(threadName = "DefaultDispatcher-worker-3")

        assertTrue(
            "必须含线程名:崩溃发生在哪个线程是首要排查信息",
            text.contains("thread=DefaultDispatcher-worker-3")
        )
    }

    @Test
    fun 日志条目包含机型与版本头() {
        val text = entry()

        assertTrue("必须含版本/机型头", text.contains("app=27 android=27 device=test/model"))
    }

    @Test
    fun 空头信息不产生空行() {
        val text = entry(header = "")

        //header 为空时不应插入多余空行,否则日志格式不稳定
        val afterHeader = text.substringAfter("===\n")
        assertFalse(
            "header 为空时不该出现空行",
            afterHeader.startsWith("\n")
        )
    }

    @Test
    fun 时间戳按毫秒格式化且带分隔符() {
        val text = entry()

        //固定时间戳 -> 固定输出,便于断言
        assertTrue("时间戳格式应为 yyyy-MM-dd HH:mm:ss.SSS", text.contains("2023-11-15 06:13:20.000"))
        assertTrue("条目应有分隔标记", text.startsWith("=== "))
    }

    @Test
    fun 不同异常产生不同条目() {
        val a = entry(Boom("第一个"))
        val b = entry(Boom("第二个"))

        assertTrue(a != b)
        assertTrue(a.contains("第一个"))
        assertTrue(b.contains("第二个"))
    }

    @Test
    fun 嵌套异常的原因链被完整打印() {
        val cause = IllegalStateException("根因")
        val wrapper = RuntimeException("外层", cause)

        val text = entry(wrapper)

        assertTrue("必须打印外层异常", text.contains("外层"))
        assertTrue(
            "必须打印 Caused by 原因链:只看到外层会误判根因",
            text.contains("Caused by: java.lang.IllegalStateException: 根因")
        )
    }

    @Test
    fun 日志条目以单个换行结尾() {
        //追加写日志时条目之间必须有且只有一个换行,否则两次崩溃会粘成一行或多出空行
        val text = entry()
        assertTrue(text.endsWith("\n"))
        assertFalse("不应有多余空行", text.endsWith("\n\n"))
    }
}
