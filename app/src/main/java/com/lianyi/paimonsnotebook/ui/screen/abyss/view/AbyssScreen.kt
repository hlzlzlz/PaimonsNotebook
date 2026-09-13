package com.lianyi.paimonsnotebook.ui.screen.abyss.view

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import com.lianyi.paimonsnotebook.R
import com.lianyi.paimonsnotebook.common.components.dialog.ConfirmDialog
import com.lianyi.paimonsnotebook.common.components.layout.column.TabBarColumnLayout
import com.lianyi.paimonsnotebook.common.core.base.BaseActivity
import com.lianyi.paimonsnotebook.common.extension.modifier.radius.radius
import com.lianyi.paimonsnotebook.common.components.loading.ContentLoadingLayout
import com.lianyi.paimonsnotebook.ui.screen.abyss.components.page.AbyssRecordPage
import com.lianyi.paimonsnotebook.ui.screen.abyss.components.page.HutaoAvatarRatePage
import com.lianyi.paimonsnotebook.ui.screen.abyss.components.page.HutaoOverviewPage
import com.lianyi.paimonsnotebook.ui.screen.abyss.components.page.HutaoTeamPage
import com.lianyi.paimonsnotebook.ui.screen.abyss.viewmodel.AbyssScreenViewModel
import com.lianyi.paimonsnotebook.ui.screen.account.components.dialog.UserGameRolesDialog
import com.lianyi.paimonsnotebook.ui.theme.PaimonsNotebookTheme

class AbyssScreen : BaseActivity() {
    private val viewModel by lazy {
        ViewModelProvider(this)[AbyssScreenViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PaimonsNotebookTheme(this) {
                TabBarColumnLayout(
                    tabs = viewModel.tabs,
                    onTabBarSelect = viewModel::onPageIndexChange,
                    tabBarPaddingHorizontal = 12.dp
                ) {
                    //标签栏下方独立一行:角色选择 + 全服数据的本期/上期切换
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp, 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier
                                .radius(2.dp)
                                .clickable {
                                    viewModel.showUserGameRoleDialog()
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = viewModel.currentGameRole?.game_uid ?: "",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )

                            Spacer(modifier = Modifier.width(4.dp))

                            Icon(
                                painter = painterResource(id = R.drawable.ic_chevron_down),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        //全服数据标签页显示本期/上期切换
                        if (viewModel.currentPageIndex >= 2) {
                            Text(
                                text = if (viewModel.lastPeriod) "上期" else "本期",
                                fontSize = 14.sp,
                                modifier = Modifier
                                    .radius(2.dp)
                                    .clickable {
                                        viewModel.togglePeriod()
                                    }
                                    .padding(10.dp, 4.dp)
                            )
                        }
                    }

                    Crossfade(targetState = viewModel.currentPageIndex, label = "") {
                        when (it) {
                            0 -> {
                                AbyssRecordPage(
                                    abyssData = viewModel.currentAbyssRecord,
                                    loadingState = viewModel.currentAbyssRecordLoadingState,
                                    getAvatarFromMetadata = viewModel::getAvatarFromMetadata,
                                    getMonsterFromMetadata = viewModel::getMonsterFromMetadata
                                )
                            }

                            1 -> {
                                AbyssRecordPage(
                                    abyssData = viewModel.previousAbyssRecord,
                                    loadingState = viewModel.previousAbyssRecordLoadingState,
                                    getAvatarFromMetadata = viewModel::getAvatarFromMetadata,
                                    getMonsterFromMetadata = viewModel::getMonsterFromMetadata
                                )
                            }

                            2 -> {
                                ContentLoadingLayout(
                                    loadingState = viewModel.overviewLoadingState,
                                    successContent = {
                                        HutaoOverviewPage(overview = viewModel.overview)
                                    }
                                )
                            }

                            3 -> {
                                ContentLoadingLayout(
                                    loadingState = viewModel.appearanceRateLoadingState,
                                    successContent = {
                                        HutaoAvatarRatePage(
                                            rates = viewModel.appearanceRate,
                                            title = "出场率",
                                            getAvatar = viewModel::getAvatarFromMetadata
                                        )
                                    }
                                )
                            }

                            4 -> {
                                ContentLoadingLayout(
                                    loadingState = viewModel.usageRateLoadingState,
                                    successContent = {
                                        HutaoAvatarRatePage(
                                            rates = viewModel.usageRate,
                                            title = "使用率",
                                            getAvatar = viewModel::getAvatarFromMetadata
                                        )
                                    }
                                )
                            }

                            5 -> {
                                ContentLoadingLayout(
                                    loadingState = viewModel.teamCombinationLoadingState,
                                    successContent = {
                                        HutaoTeamPage(
                                            teams = viewModel.teamCombination,
                                            getAvatar = viewModel::getAvatarFromMetadata
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (viewModel.showUserGameRoleDialog) {
                UserGameRolesDialog(
                    onButtonClick = {
                        viewModel.dismissUserGameRoleDialog()
                    },
                    onDismissRequest = viewModel::dismissUserGameRoleDialog,
                    onSelectRole = viewModel::onChangeGameRole
                )
            }

            if (viewModel.showConfirmDialog) {
                ConfirmDialog(
                    content = "进行验证才能继续进行查询,点击确认前往验证界面",
                    onConfirm = viewModel::goValidateScreen,
                    onCancel = viewModel::dismissConfirmDialog
                )
            }
        }
    }
}