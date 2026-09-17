package com.lianyi.paimonsnotebook.common.application

import android.content.Intent
import android.os.Build
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.lianyi.paimonsnotebook.common.service.overlay.debug.DebugOverlayService
import com.lianyi.paimonsnotebook.common.service.overlay.util.OverlayHelper
import com.lianyi.paimonsnotebook.common.service.util.ServiceHelper
import com.lianyi.paimonsnotebook.common.util.file.FileHelper

/*
* 程序生命周期观察者
* */
open class ApplicationLifecycleObserver : DefaultLifecycleObserver {

    override fun onPause(owner: LifecycleOwner) {
        sendCommandForDebugPanel(ServiceHelper.Command_Hide)
        super.onPause(owner)
    }

    override fun onResume(owner: LifecycleOwner) {
        sendCommandForDebugPanel(ServiceHelper.Command_Show)
        super.onResume(owner)
    }

    private fun sendCommandForDebugPanel(command: String) {
        if (!FileHelper.debug || !OverlayHelper.checkPermission()) {
            return
        }

        val context = PaimonsNotebookApplication.context

        val intent = Intent(context, DebugOverlayService::class.java)
            .putExtra(ServiceHelper.Command, command)

        try {
            //onPause(切到后台)时调startService在Android 8+会抛
            //IllegalStateException: Not allowed to start service Intent ... app is in background。
            //DebugOverlayService本身是前台服务(onStartCommand里会startForeground),
            //因此后台场景应改用startForegroundService。
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            //调试面板不可用不应影响正常使用
            e.printStackTrace()
        }
    }
}