package com.lianyi.paimonsnotebook.ui.screen.splash.view

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.lianyi.paimonsnotebook.common.components.loading.ContentLoadingPlaceholder
import com.lianyi.paimonsnotebook.common.core.base.BaseActivity
import com.lianyi.paimonsnotebook.common.extension.intent.setComponentName
import com.lianyi.paimonsnotebook.ui.screen.app_widget.view.AppWidgetEditScreen
import com.lianyi.paimonsnotebook.ui.screen.home.util.HomeHelper
import com.lianyi.paimonsnotebook.ui.screen.home.view.HomeDrawerManagerScreen
import com.lianyi.paimonsnotebook.ui.screen.home.view.HomeScreen
import com.lianyi.paimonsnotebook.ui.screen.splash.components.EnableMetadataHint
import com.lianyi.paimonsnotebook.ui.screen.splash.components.MetadataDownloadFailedPanel
import com.lianyi.paimonsnotebook.ui.screen.splash.viewmodel.SplashScreenViewModel
import com.lianyi.paimonsnotebook.ui.theme.BackGroundColor
import com.lianyi.paimonsnotebook.ui.theme.PaimonsNotebookTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@SuppressLint("CustomSplashScreen")
class SplashScreen : BaseActivity(false) {

    companion object {

        //主页是透明窗口主题,开屏页若在主页首帧绘制完成前退出,交接空窗期会露出底层任务(上一个应用)
        @Volatile
        private var homeScreenFirstFrameCallback: (() -> Unit)? = null

        val isWaitingHomeScreenFirstFrame: Boolean
            get() = homeScreenFirstFrameCallback != null

        //由HomeScreen在首帧绘制完成时调用
        fun onHomeScreenFirstFrameDrawn() {
            homeScreenFirstFrameCallback?.invoke()
            homeScreenFirstFrameCallback = null
        }
    }

    val viewModel by lazy {
        ViewModelProvider(this)[SplashScreenViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //TODO 用户协议
//        lifecycleScope.launch {
//            val userAgree = datastorePf.data.first()[PreferenceKeys.AgreeUserAgreement] ?: false
//            if (!userAgree) {
//                HomeHelper.goActivity(
//                    GuideScreen::class.java,
//                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
//                )
//            }
//        }

//        goDebugPage()
//        return

        viewModel.initParam(
            onGoTargetScreen = this::goTargetScreen,
            onDownload = this::downloadMetadata
        )

        setContent {
            PaimonsNotebookTheme {
                Box {

                    Crossfade(targetState = viewModel.showLoading, label = "") {
                        if (it) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(BackGroundColor),
                                contentAlignment = Alignment.Center
                            ) {
                                ContentLoadingPlaceholder(text = "正在下载所需的元数据...\n${viewModel.currentMetadataLoadCount}/${viewModel.maxMetadataCount}")
                            }
                        }
                    }

                    Crossfade(targetState = viewModel.showEnableMetadataHint, label = "") {
                        if (it) {
                            EnableMetadataHint(
                                onCountDownEnd = this@SplashScreen::downloadMetadata,
                                skipDownloadMetadata = this@SplashScreen::skipDownload,
                                downloadMetadata = this@SplashScreen::downloadMetadata
                            )
                        }
                    }

                    /*
                    * 下载失败出口。
                    *
                    * 必须排在进度与倒计时之后(最后绘制、位于最上层):失败时
                    * showEnableMetadataHint 已为 false、showLoading 也已复位,
                    * 若无此面板屏幕上将没有任何可点内容(纯白屏)。
                    * */
                    Crossfade(targetState = viewModel.metadataDownloadFailed, label = "") {
                        if (it) {
                            MetadataDownloadFailedPanel(
                                onRetry = this@SplashScreen::downloadMetadata,
                                onSkip = this@SplashScreen::skipDownload
                            )
                        }
                    }
                }
            }
        }
    }

    private fun goTargetScreen() {
        goHomeScreen()
//            goDebugPage()
    }

    private fun downloadMetadata() {
        viewModel.metadataDownloadInit {
            goTargetScreen()
        }
    }

    private fun skipDownload() {
        viewModel.onSkipMetadataDownload {
            goTargetScreen()
        }
    }

    private fun goHomeScreen() {
        viewModel.disabledOnLaunchShowMetadataHint {
            //先注册首帧回调,再回主线程启动主页
            waitHomeScreenFirstFrameThenFinish()
            runOnUiThread {
                HomeHelper.goActivityByIntentNewTask {
                    setComponentName(HomeScreen::class.java)
                }
            }
        }
    }

    //等主页完成首帧绘制后再退出开屏;超时兜底,主页异常时避免卡在开屏页
    private fun waitHomeScreenFirstFrameThenFinish() {
        var finished = false
        val finishOnce = {
            if (!finished) {
                finished = true
                finish()
            }
        }
        homeScreenFirstFrameCallback = finishOnce
        lifecycleScope.launch {
            delay(10000)
            homeScreenFirstFrameCallback = null
            finishOnce()
        }
    }

    private fun goDebugPage() {
        HomeHelper.goActivityByIntentNewTask {
            setComponentName(AppWidgetEditScreen::class.java)
        }
        finish()
    }

}