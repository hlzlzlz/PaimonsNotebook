package com.lianyi.paimonsnotebook.ui.screen.home.view

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResult
import androidx.core.view.OneShotPreDrawListener
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.DrawerValue
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.ModalDrawer
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material.rememberDrawerState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.ViewModelProvider
import com.lianyi.core.ui.components.text.InfoText
import com.lianyi.paimonsnotebook.R
import com.lianyi.paimonsnotebook.common.components.dialog.ConfirmDialog
import com.lianyi.paimonsnotebook.common.components.loading.LoadingAnimationPlaceholder
import com.lianyi.paimonsnotebook.common.components.placeholder.EmptyPagePlaceholder
import com.lianyi.paimonsnotebook.common.components.spacer.StatusBarPaddingSpacer
import com.lianyi.paimonsnotebook.common.core.base.BaseActivity
import com.lianyi.paimonsnotebook.common.extension.modifier.radius.radius
import com.lianyi.paimonsnotebook.ui.screen.account.components.dialog.UserDialog
import com.lianyi.paimonsnotebook.ui.screen.home.components.home.HomeContent
import com.lianyi.paimonsnotebook.ui.screen.home.components.home.HomeDrawerContent
import com.lianyi.paimonsnotebook.ui.screen.home.viewmodel.HomeScreenViewModel
import com.lianyi.paimonsnotebook.ui.screen.splash.view.SplashScreen
import com.lianyi.paimonsnotebook.ui.screen.setting.util.enums.HomeScreenDisplayState
import com.lianyi.paimonsnotebook.ui.theme.BackGroundColor
import com.lianyi.paimonsnotebook.ui.theme.PaimonsNotebookTheme
import com.lianyi.paimonsnotebook.ui.theme.White

class HomeScreen : BaseActivity() {
    private val viewModel by lazy {
        ViewModelProvider(this)[HomeScreenViewModel::class.java]
    }

    @OptIn(ExperimentalMaterialApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel.startActivity = registerStartActivityForResult()
        registerPressedCallback()
        registerRequestPermissionsResult()

        viewModel.init()

        setContent {
            PaimonsNotebookTheme {
                val coroutineScope = rememberCoroutineScope()
                val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

                ModalDrawer(
                    modifier = Modifier.fillMaxSize(),
                    drawerState = drawerState,
                    drawerContent = {
                        HomeDrawerContent(
                            selectedUser = viewModel.selectedUser,
                            modalItems = viewModel.modalItems,
                            onScanQRCode = viewModel::onScanQRCode,
                            goSignWeb = viewModel::goSignWeb,
                            functionNavigate = viewModel::functionNavigate
                        )
                    },
                    drawerBackgroundColor = BackGroundColor
                ) {

                    val pullRefreshState = rememberPullRefreshState(
                        refreshing = viewModel.isRefreshing,
                        onRefresh = viewModel::refreshData
                    )

                    Box(
                        modifier = Modifier
                            .pullRefresh(pullRefreshState)
                            .fillMaxSize()
                            .background(White)
                    ) {

                        Column(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .zIndex(3f)
                        ) {
                            StatusBarPaddingSpacer()

                            Icon(
                                painter = painterResource(id = R.drawable.ic_navigation),
                                contentDescription = null,
                                modifier = Modifier
                                    .padding(2.dp)
                                    .radius(3.dp)
                                    .size(32.dp)
                                    .clickable {
                                        viewModel.toggleModalDrawer(
                                            drawerState,
                                            coroutineScope
                                        )
                                    }
                                    .padding(4.dp)
                            )
                        }

                        Crossfade(
                            targetState = viewModel.configurationData.homeScreenDisplayState,
                            label = ""
                        ) {
                            when (it) {
                                HomeScreenDisplayState.Simple -> {
                                    //Simple(非社区)主页尚未实现,原先此分支为空Composable,
                                    //用户关掉"启用社区主页"后会看到纯白屏,像是应用坏了。
                                    //此处给出明确说明,避免误解。
                                    EmptyPagePlaceholder(title = "简洁主页正在开发中") {
                                        InfoText(text = "可在 设置 中重新开启「启用社区主页」")
                                    }
                                }

                                HomeScreenDisplayState.Community -> {
                                    HomeContent(
                                        bannerList = viewModel.bannerList,
                                        nearActivity = viewModel.nearActivity,
                                        noticeList = viewModel.noticeList,
                                        travelersDiaryData = viewModel.travelersDiaryData,
                                        cardPools = viewModel.cardPools,
                                        miyoliveCodes = viewModel.miyoliveCodes,
                                        goPostDetail = viewModel::goPostDetail
                                    )
                                }

                                else -> {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        LoadingAnimationPlaceholder()
                                    }
                                }
                            }
                        }

                        PullRefreshIndicator(
                            refreshing = viewModel.isRefreshing,
                            state = pullRefreshState,
                            modifier = Modifier.align(Alignment.TopCenter)
                        )
                    }
                }

                if (viewModel.showUserDialog) {
                    UserDialog(
                        onButtonClick = {
                            viewModel.dismissUserDialog()
                        },
                        onDismissRequest = viewModel::dismissUserDialog,
                        onClickUser = viewModel::onSelectUser
                    )
                }

                if (viewModel.showConfirm) {
                    ConfirmDialog(
                        title = getString(R.string.no_permission),
                        content = getString(R.string.content_request_overlay_permission),
                        onConfirm = viewModel::requestOverlayPermission,
                        onCancel = viewModel::removeOverlayPermissionFlag
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        //主页是透明窗口主题,首帧绘制前窗口下露出的是上一个应用;
        //开屏页在等待首帧时(冷启动或从后台重开,窗口可能保留旧帧不再触发绘制),
        //重挂一次性首帧监听并主动触发重绘,确保回调能触发
        if (SplashScreen.isWaitingHomeScreenFirstFrame) {
            OneShotPreDrawListener.add(window.decorView) {
                //再延迟一帧,确保首帧已上屏
                window.decorView.post {
                    SplashScreen.onHomeScreenFirstFrameDrawn()
                }
            }
            window.decorView.invalidate()
        }
    }

    override fun onBackPressedCallback() {
        viewModel.onBackPressed {
            //返回桌面
            moveTaskToBack(false)
        }
    }

    override fun onStartActivityForResult(result: ActivityResult) {
        viewModel.onActivityResult(result)
    }
}

