package com.lianyi.paimonsnotebook.common.util.log

import com.lianyi.paimonsnotebook.common.application.PaimonsNotebookApplication
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/*
* 崩溃记录器
*
* 为什么需要它:
*   本应用 CaocConfig 配的是 BACKGROUND_MODE_SILENT + showRestartButton(false),
*   而 AppCenter 的 secret 在 local.properties 里是**占位符**(全 0 的 GUID),
*   即崩溃上报链路从未真正生效。
*   结果是崩溃时**无界面、无日志、无上报**,用户只能描述"开屏一闪就没了",
*   开发者拿不到任何堆栈 —— 本工作区已多次因此只能靠截图倒推原因
*   (1.8.8/1.8.9 米游社页面全失效、1.8.13 公告时间标签没修好)。
*
* 做法:
*   接管 Thread 默认未捕获异常处理器,把堆栈落盘到 files/crash.log,
*   然后**继续委托**给原处理器(CAOC),因此崩溃页/静默关闭行为不受影响。
*
* 注意:本类不做上报,只做本地留痕。用户可在崩溃后把该文件导出给开发者。
* */
object CrashLogger {

    private const val FILE_NAME = "crash.log"

    /*
    * 日志体积上限。
    * 崩溃常连续发生,不能让文件无限增长(弱机存储紧张),超限后只保留尾部。
    * */
    private const val MAX_BYTES = 256 * 1024L

    /*
    * DateTimeFormatter 是不可变且线程安全的,可直接作为单例共享。
    * ⚠️ 不要改用 SimpleDateFormat —— 它非线程安全(会改写内部 Calendar),
    *    而崩溃可能发生在任意线程上,共享实例会写出错乱时间。
    * */
    private val timeFormat: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
            .withZone(ZoneId.systemDefault())

    @Volatile
    private var installed = false

    /*
    * 安装处理器
    *
    * ⚠️ 调用时机必须在 CaocConfig.Builder.apply() **之后**:
    *    CAOC 在 apply() 里安装它自己的处理器,若我们先装,CAOC 会直接覆盖掉我们,
    *    本记录器就永远不会被调用。装在它之后,才能把 CAOC 的处理器取出来当 previous。
    * */
    fun install() {
        if (installed) return
        installed = true

        val previous = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            //记录过程本身绝不能再抛异常,否则会掩盖原始崩溃
            runCatching { record(thread, throwable) }
            //委托回原处理器(CAOC),保持崩溃页/静默关闭行为不变
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun record(thread: Thread, throwable: Throwable) {
        val entry = formatEntry(
            timeMillis = System.currentTimeMillis(),
            threadName = thread.name,
            throwable = throwable,
            header = buildHeader()
        )
        append(entry)
    }

    /*
    * 纯函数:把一次崩溃格式化成日志条目
    *
    * 与文件、Context 全部解耦,因此可被纯 JVM 单测覆盖。
    * (本工作区已有教训:逻辑与平台耦合会导致单测与真机走不同分支,见 HtmlSpanParser。)
    * */
    fun formatEntry(
        timeMillis: Long,
        threadName: String,
        throwable: Throwable,
        header: String
    ): String {
        val stackTrace = StringWriter().also { sw ->
            PrintWriter(sw).use { throwable.printStackTrace(it) }
        }.toString()

        return buildString {
            append("=== ")
            append(timeFormat.format(Instant.ofEpochMilli(timeMillis)))
            append(" | thread=")
            append(threadName)
            append(" | ")
            append(throwable.javaClass.name)
            append(" ===\n")
            if (header.isNotEmpty()) {
                append(header)
                append('\n')
            }
            //printStackTrace 自身已以换行结尾,这里先去掉再补一个,
            //保证每条记录**恰好**以单个换行结束(追加写时不会多出空行)
            append(stackTrace.trimEnd('\n'))
            append('\n')
        }
    }

    /*
    * 机型/版本信息:多版本迭代下,没有这些就无法判断是哪个包在哪台机器上崩的
    * */
    private fun buildHeader(): String = runCatching {
        "app=${PaimonsNotebookApplication.versionCode} " +
                "android=${android.os.Build.VERSION.SDK_INT} " +
                "device=${android.os.Build.MANUFACTURER}/${android.os.Build.MODEL}"
    }.getOrDefault("")

    fun logFile(): File = File(PaimonsNotebookApplication.context.filesDir, FILE_NAME)

    //上次运行是否留下过崩溃记录(供启动时提示用户导出)
    fun hasPendingCrash(): Boolean = runCatching {
        val file = logFile()
        file.exists() && file.length() > 0
    }.getOrDefault(false)

    fun readLog(): String = runCatching { logFile().readText() }.getOrDefault("")

    private fun append(entry: String) {
        runCatching {
            val file = logFile()
            file.appendText(entry)
            trimIfNeeded(file)
        }
    }

    //超限时只保留尾部(最新的崩溃更有诊断价值)
    private fun trimIfNeeded(file: File) {
        if (file.length() <= MAX_BYTES) return

        val bytes = file.readBytes()
        val keepSize = (MAX_BYTES / 2).toInt()
        if (bytes.size <= keepSize) return

        file.writeBytes(bytes.copyOfRange(bytes.size - keepSize, bytes.size))
    }
}
