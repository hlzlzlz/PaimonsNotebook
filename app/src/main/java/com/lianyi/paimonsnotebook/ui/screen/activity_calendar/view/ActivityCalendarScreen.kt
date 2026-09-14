package com.lianyi.paimonsnotebook.ui.screen.activity_calendar.view

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import com.lianyi.paimonsnotebook.common.components.dialog.ConfirmDialog
import com.lianyi.paimonsnotebook.common.components.layout.column.TabBarColumnLayout
import com.lianyi.paimonsnotebook.common.components.loading.ContentLoadingLayout
import com.lianyi.paimonsnotebook.common.components.media.NetworkImage
import com.lianyi.paimonsnotebook.common.core.base.BaseActivity
import com.lianyi.paimonsnotebook.common.extension.modifier.radius.radius
import com.lianyi.paimonsnotebook.ui.screen.account.components.dialog.UserGameRolesDialog
import com.lianyi.paimonsnotebook.ui.screen.activity_calendar.viewmodel.ActivityCalendarScreenViewModel
import com.lianyi.paimonsnotebook.ui.theme.Black_60
import com.lianyi.paimonsnotebook.ui.theme.PaimonsNotebookTheme
import com.lianyi.paimonsnotebook.ui.theme.White

/*
* 活动与卡池日历页
* 当期卡池 + 进行中/即将开始的活动列表
* */
class ActivityCalendarScreen : BaseActivity() {

    private val viewModel by lazy {
        ViewModelProvider(this)[ActivityCalendarScreenViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PaimonsNotebookTheme(this) {
                TabBarColumnLayout(
                    tabs = viewModel.tabs,
                    onTabBarSelect = viewModel::onTabIndexChange,
                    topSlot = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = viewModel.currentGameRole?.game_uid ?: "",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .radius(2.dp)
                                    .clickable { viewModel.showUserGameRoleDialog() }
                                    .padding(8.dp, 4.dp)
                            )
                        }
                    }
                ) {
                    ContentLoadingLayout(loadingState = viewModel.loadingState) {
                        CalendarContent()
                    }
                }

                if (viewModel.showUserGameRoleDialog) {
                    UserGameRolesDialog(
                        onButtonClick = { viewModel.dismissUserGameRoleDialog() },
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

    @Composable
    private fun CalendarContent() {
        val data = viewModel.calendarData ?: return

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val pools = viewModel.selectedPools(data)

            if (pools.isNotEmpty()) {
                item { SectionTitle("当期卡池") }

                items(pools, key = { "pool_${it.pool_id}" }) { pool ->
                    Card {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = pool.pool_name,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f)
                                )

                                Text(
                                    text = viewModel.formatCountdown(pool.countdown_seconds),
                                    fontSize = 12.sp,
                                    color = Color(0xFFE65100)
                                )
                            }

                            Text(
                                text = "${pool.version_name}版本 · ${pool.end_time?.format() ?: ""} 结束",
                                fontSize = 12.sp,
                                color = Black_60
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                pool.avatars.take(6).forEach { PoolIcon(it.icon, it.name) }
                                pool.weapon.take(3).forEach { PoolIcon(it.icon, it.name) }
                            }
                        }
                    }
                }
            }

            val ongoing = viewModel.ongoingActs(data)

            if (ongoing.isNotEmpty()) {
                item { SectionTitle("进行中活动") }

                items(ongoing, key = { "ongoing_${it.id}" }) { act ->
                    ActItem(act.name, viewModel.formatCountdown(act.countdown_seconds), act.strategy)
                }
            }

            val upcoming = viewModel.upcomingActs(data)

            if (upcoming.isNotEmpty()) {
                item { SectionTitle("即将开始") }

                items(upcoming, key = { "upcoming_${it.id}" }) { act ->
                    ActItem(act.name, "开始于 ${act.start_time?.format() ?: ""}", "")
                }
            }

            item { Spacer(modifier = Modifier.size(16.dp)) }
        }
    }

    @Composable
    private fun ActItem(name: String, trailing: String, strategy: String) {
        Card {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = strategy.isNotBlank()) {
                        viewModel.openStrategy(strategy)
                    }
            ) {
                Text(
                    text = name,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f)
                )

                Text(text = trailing, fontSize = 12.sp, color = Black_60)
            }
        }
    }

    @Composable
    private fun PoolIcon(url: String, name: String) {
        NetworkImage(
            url = url,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
        )
    }

    @Composable
    private fun SectionTitle(title: String) {
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = Black_60,
            modifier = Modifier.padding(4.dp, 6.dp, 4.dp, 0.dp)
        )
    }

    @Composable
    private fun Card(content: @Composable () -> Unit) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp, 2.dp)
                .radius(2.dp)
                .background(White)
                .padding(12.dp)
        ) {
            content()
        }
    }
}
