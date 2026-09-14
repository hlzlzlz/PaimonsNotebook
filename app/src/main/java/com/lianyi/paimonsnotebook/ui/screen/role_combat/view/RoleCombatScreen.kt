package com.lianyi.paimonsnotebook.ui.screen.role_combat.view

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
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
import com.lianyi.paimonsnotebook.ui.screen.role_combat.viewmodel.RoleCombatScreenViewModel
import com.lianyi.paimonsnotebook.ui.theme.Black_60
import com.lianyi.paimonsnotebook.ui.theme.Success
import com.lianyi.paimonsnotebook.ui.theme.PaimonsNotebookTheme
import com.lianyi.paimonsnotebook.ui.theme.White

class RoleCombatScreen : BaseActivity() {
    private val viewModel by lazy {
        ViewModelProvider(this)[RoleCombatScreenViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PaimonsNotebookTheme(this) {
                TabBarColumnLayout(
                    tabs = viewModel.tabs,
                    onTabBarSelect = viewModel::onPageIndexChange,
                    topSlot = {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.End
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
                            }

                            Spacer(modifier = Modifier.width(8.dp))
                        }
                    }
                ) {
                    Crossfade(targetState = viewModel.currentPageIndex, label = "") { page ->
                        when (page) {
                            0 -> {
                                ContentLoadingLayout(
                                    loadingState = viewModel.roleCombatLoadingState,
                                    successContent = {
                                        RoleCombatPage()
                                    }
                                )
                            }

                            1 -> {
                                ContentLoadingLayout(
                                    loadingState = viewModel.hardChallengeLoadingState,
                                    successContent = {
                                        HardChallengePage()
                                    }
                                )
                            }

                            2 -> {
                                ContentLoadingLayout(
                                    loadingState = viewModel.statisticsLoadingState,
                                    successContent = {
                                        StatisticsPage()
                                    }
                                )
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

    @Composable
    private fun RoleCombatPage() {
        val entry = viewModel.roleCombatData?.data?.firstOrNull() ?: return

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp, 4.dp)
                        .radius(2.dp)
                        .background(White)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "${entry.schedule.start_date_time?.format() ?: ""} ~ ${entry.schedule.end_date_time?.format() ?: ""}",
                        fontSize = 14.sp,
                        color = Black_60
                    )

                    entry.stat?.let { stat ->
                        Text(text = "战绩徽章 ${stat.medal_num} 枚", fontSize = 15.sp)
                        Text(text = "幻象币 ${stat.coin_num}", fontSize = 15.sp)
                        Text(text = "角色支援 ${stat.rent_cnt} 次", fontSize = 15.sp)
                        Text(
                            text = "难度 ${stat.difficulty_id} · 最高回合 ${stat.max_round_id}",
                            fontSize = 15.sp,
                            color = Black_60
                        )
                    }
                }
            }

            entry.detail?.rounds_data?.sortedByDescending { it.round_id }?.forEach { round ->
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp, 0.dp)
                            .radius(2.dp)
                            .background(White)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "第 ${round.round_id} 幕",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )

                            Spacer(modifier = Modifier.weight(1f))

                            Text(
                                text = if (round.is_get_medal) "已获徽章" else "未获徽章",
                                fontSize = 13.sp,
                                color = if (round.is_get_medal) Success else Black_60
                            )
                        }

                        if (round.enemies.isNotEmpty()) {
                            Text(
                                text = "敌方:${round.enemies.joinToString("、") { "${it.name}(Lv.${it.level})" }}",
                                fontSize = 13.sp,
                                color = Black_60
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            round.avatars.take(7).forEach { avatar ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.width(48.dp)
                                ) {
                                    NetworkImage(
                                        url = avatar.image,
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                    Text(
                                        text = "Lv.${avatar.level}",
                                        fontSize = 11.sp,
                                        color = Black_60
                                    )
                                }
                            }
                        }

                        if (round.buffs.isNotEmpty()) {
                            Text(
                                text = "增益:${round.buffs.take(3).joinToString("、") { it.name }}",
                                fontSize = 13.sp,
                                color = Black_60
                            )
                        }
                    }
                }
            }

            entry.detail?.backup_avatars?.takeIf { it.isNotEmpty() }?.let { backups ->
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp, 0.dp)
                            .radius(2.dp)
                            .background(White)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(text = "后备队员", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            backups.take(10).forEach { avatar ->
                                NetworkImage(
                                    url = avatar.image,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    }
                }
            }

            item {
                com.lianyi.core.ui.components.spacer.NavigationBarPaddingSpacer()
            }
        }
    }

    @Composable
    private fun HardChallengePage() {
        val entry = viewModel.hardChallengeData?.data?.firstOrNull() ?: return

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp, 4.dp)
                        .radius(2.dp)
                        .background(White)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(text = entry.schedule.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = "${entry.schedule.start_date_time?.format() ?: ""} ~ ${entry.schedule.end_date_time?.format() ?: ""}",
                        fontSize = 14.sp,
                        color = Black_60
                    )

                    entry.single?.best?.let { best ->
                        Text(
                            text = "最高难度 ${best.difficulty} · 最快通关 ${formatSecond(best.second)}",
                            fontSize = 15.sp
                        )
                    }
                }
            }

            entry.single?.challenge?.forEach { challenge ->
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp, 0.dp)
                            .radius(2.dp)
                            .background(White)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = challenge.name,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )

                            Text(
                                text = formatSecond(challenge.second),
                                fontSize = 14.sp,
                                color = Black_60
                            )
                        }

                        Text(
                            text = "${challenge.monster.name} Lv.${challenge.monster.level}",
                            fontSize = 13.sp,
                            color = Black_60
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            challenge.teams.take(6).forEach { avatar ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.width(48.dp)
                                ) {
                                    NetworkImage(
                                        url = avatar.image,
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                    Text(
                                        text = "Lv.${avatar.level}",
                                        fontSize = 11.sp,
                                        color = Black_60
                                    )
                                }
                            }
                        }

                        challenge.best_avatar.firstOrNull()?.let { bestAvatar ->
                            Text(
                                text = "输出最高:${bestAvatar.dps}",
                                fontSize = 13.sp,
                                color = Black_60
                            )
                        }
                    }
                }
            }

            item {
                com.lianyi.core.ui.components.spacer.NavigationBarPaddingSpacer()
            }
        }
    }

    private fun formatSecond(second: Int): String {
        val minutes = second / 60
        val remainSeconds = second % 60
        return "${minutes}分${remainSeconds.toString().padStart(2, '0')}秒"
    }

    @Composable
    private fun StatisticsPage() {
        val statistics = viewModel.statistics ?: return

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp, 4.dp)
                        .radius(2.dp)
                        .background(White)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "第 ${statistics.ScheduleId} 期 · 全服统计",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )

                        Text(
                            text = if (viewModel.statisticsLastPeriod) "上期" else "本期",
                            fontSize = 14.sp,
                            modifier = Modifier
                                .radius(2.dp)
                                .clickable { viewModel.toggleStatisticsPeriod() }
                                .padding(10.dp, 4.dp)
                        )
                    }

                    Text(
                        text = "参与统计的记录总数 ${statistics.RecordTotal}",
                        fontSize = 14.sp,
                        color = Black_60
                    )

                    Text(
                        text = "热门替补为全服记录中各角色作为后备队员的上阵比例",
                        fontSize = 12.sp,
                        color = Black_60
                    )
                }
            }

            itemsIndexed(
                statistics.BackupAvatarRates.sortedByDescending { it.Rate }
            ) { index, rate ->
                val avatar = viewModel.getAvatarFromMetadata(rate.Item)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp, 0.dp)
                        .radius(2.dp)
                        .background(White)
                        .padding(10.dp, 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${index + 1}",
                        fontSize = 13.sp,
                        color = Black_60,
                        modifier = Modifier.width(26.dp)
                    )

                    NetworkImage(
                        url = avatar?.iconUrl ?: "",
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = avatar?.name ?: "${rate.Item}",
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f)
                    )

                    Text(
                        text = String.format("%.2f%%", rate.Rate * 100),
                        fontSize = 13.sp,
                        color = Black_60
                    )
                }
            }

            item {
                com.lianyi.core.ui.components.spacer.NavigationBarPaddingSpacer()
            }
        }
    }
}
