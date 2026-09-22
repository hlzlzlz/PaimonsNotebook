package com.lianyi.paimonsnotebook.common.util.system_service

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.view.WindowManager
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import com.lianyi.paimonsnotebook.common.application.PaimonsNotebookApplication
import com.lianyi.paimonsnotebook.common.extension.string.errorNotify
import com.lianyi.paimonsnotebook.common.util.file.FileHelper
import java.io.File

@SuppressLint("InternalInsetResource", "DiscouragedApi")
object SystemService {

    private val context by lazy {
        PaimonsNotebookApplication.context
    }

    private val PERMISSION_REQUEST_CODE by lazy {
        1200
    }

    val statusBarHeight by lazy {
        val resourceId: Int =
            context.resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            context.resources.getDimensionPixelSize(resourceId)
        } else {
            0
        }
    }

    val screenWidthPx by lazy {
        context.resources.displayMetrics.widthPixels
    }
    val screenHeightPx by lazy {
        context.resources.displayMetrics.heightPixels
    }

    private val clipboardManager by lazy {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }

    private val windowManager by lazy {
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }



    //设置剪切板内容
    fun setClipBoardText(text: String, label: String? = null) {
        clipboardManager.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    //读取剪贴板文本(无文本内容时返回null)
    fun getClipBoardText(): String? =
        clipboardManager.primaryClip?.getItemAt(0)?.text?.toString()

    /*
    * 分享文件(系统分享面板)
    *
    * 用于把本地生成的文件交给其它应用(微信/邮件等),便于用户反馈问题。
    *
    * ⚠️ 必须用 FileProvider 生成 content:// uri 并加 FLAG_GRANT_READ_URI_PERMISSION:
    *    file:// uri 从 targetSdk 24 起会抛 FileUriExposedException;
    *    而 content:// 若不带授权标志,接收方读不到文件(静默失败)。
    * 这两条都已踩过坑,见 AGENTS.md「FileProvider 路径的硬性约束」。
    *
    * 返回是否成功唤起分享面板。
    * */
    fun shareFile(file: File, mimeType: String = "*/*", chooserTitle: String = "分享文件"): Boolean {
        if (!file.exists()) {
            "文件不存在,无法分享".errorNotify()
            return false
        }

        return try {
            val uri = FileProvider.getUriForFile(context, FileHelper.provider, file)

            val send = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            //用 chooser 包一层:FLAG_ACTIVITY_NEW_TASK 需加在 chooser 上,
            //而从非 Activity 上下文启动又必须有该标志(否则抛 AndroidRuntimeException)
            context.startActivity(
                Intent.createChooser(send, chooserTitle).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    //授权标志要同时给 chooser,否则部分 ROM 上接收方拿不到读权限
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            )
            true
        } catch (e: Exception) {
            "无法分享文件:${e.message ?: "未知错误"}".errorNotify()
            false
        }
    }

    //安装程序
    //返回是否成功唤起安装界面
    fun installAndroidApplication(file: File): Boolean {
        //文件不存在时FileProvider.getUriForFile会抛IllegalArgumentException
        if (!file.exists()) {
            "安装包不存在或已被清理,请重新下载".errorNotify()
            return false
        }

        return try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    val uri =
                        FileProvider.getUriForFile(context, FileHelper.provider, file)
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    flags =
                        Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                }
            )
            true
        } catch (e: Exception) {
            //无安装器(部分定制ROM会移除packageinstaller的ACTION_VIEW入口)等情况
            "无法唤起安装界面:${e.message ?: "未知错误"}".errorNotify()
            false
        }
    }


    fun serviceIsRunning(cls: Service) {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    }

    fun checkPermission(vararg list: String): Boolean {
        var pass = true
        list.forEach {
            pass =
                pass && PaimonsNotebookApplication.context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
        }
        return pass
    }

    fun requestPermission(activity: Activity, vararg list: String) {
        ActivityCompat.requestPermissions(activity, list, PERMISSION_REQUEST_CODE)
    }

}