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
import androidx.compose.ui.graphics.Color
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
import com.lianyi.core.ui.components.text.InfoText
import com.lianyi.core.ui.components.text.PrimaryText
import com.lianyi.paimonsnotebook.ui.theme.Black_60
import com.lianyi.paimonsnotebook.common.components.widget.RoundedTag
import com.lianyi.paimonsnotebook.ui.theme.CardBackGroundColor
import com.lianyi.paimonsnotebook.ui.theme.CardBackGroundColor_Gray_Dark
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
                                PrimaryText(
                                    text = viewModel.currentGameRole?.game_uid ?: "",
                                    textSize = 16.sp
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
                    InfoText(
                        text = "${entry.schedule.start_date_time?.format() ?: ""} ~ ${entry.schedule.end_date_time?.format() ?: ""}",
                        fontSize = 14.sp
                    )

                    entry.stat?.let { stat ->
                        InfoText(text = "战绩徽章 ${stat.medal_num} 枚", fontSize = 15.sp)
                        InfoText(text = "幻象币 ${stat.coin_num}", fontSize = 15.sp)
                        InfoText(text = "角色支援 ${stat.rent_cnt} 次", fontSize = 15.sp)
                        InfoText(
                            text = "难度 ${stat.difficulty_id} · 最高回合 ${stat.max_round_id}",
                            fontSize = 15.sp
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
                            PrimaryText(
                                text = "第 ${round.round_id} 幕",
                                textSize = 15.sp
                            )

                            Spacer(modifier = Modifier.weight(1f))

                            RoundedTag(
                                text = if (round.is_get_medal) "已获徽章" else "未获徽章",
                                backGroundColor = if (round.is_get_medal) Success else CardBackGroundColor_Gray_Dark,
                                textColor = if (round.is_get_medal) Color.White else Black_60
                            )
                        }

                        if (round.enemies.isNotEmpty()) {
                            InfoText(
                                text = "敌方:${round.enemies.joinToString("、") { "${it.name}(Lv.${it.level})" }}",
                                fontSize = 13.sp
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
                                    InfoText(
                                        text = "Lv.${avatar.level}",
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }

                        if (round.buffs.isNotEmpty()) {
                            InfoText(
                                text = "增益:${round.buffs.take(3).joinToString("、") { it.name }}",
                                fontSize = 13.sp
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
                        PrimaryText(text = "后备队员", textSize = 15.sp)

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
                    PrimaryText(text = entry.schedule.name, textSize = 16.sp)
                    InfoText(
                        text = "${entry.schedule.start_date_time?.format() ?: ""} ~ ${entry.schedule.end_date_time?.format() ?: ""}",
                        fontSize = 14.sp
                    )

                    entry.single?.best?.let { best ->
                        InfoText(
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
                            PrimaryText(
                                text = challenge.name,
                                textSize = 15.sp,
                                modifier = Modifier.weight(1f)
                            )

                            InfoText(
                                text = formatSecond(challenge.second),
                                fontSize = 14.sp
                            )
                        }

                        InfoText(
                            text = "${challenge.monster.name} Lv.${challenge.monster.level}",
                            fontSize = 13.sp
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
                                    InfoText(
                                        text = "Lv.${avatar.level}",
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }

                        challenge.best_avatar.firstOrNull()?.let { bestAvatar ->
                            InfoText(
                                text = "输出最高:${bestAvatar.dps}",
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            if (viewModel.hardChallengePopularity?.avatar_list?.isNotEmpty() == true) {
                item {
                    HardChallengePopularityCard()
                }
            }

            item {
                com.lianyi.core.ui.components.spacer.NavigationBarPaddingSpacer()
            }
        }
    }

    /*
    * 全服热门角色
    *
    * 服务端只返回一份有序名单(avatar_list),**不含任何比例数值**,
    * 所以这里只按返回顺序标名次,不要编造百分比 —— 与剧诗全服统计
    * (那边有 BackupAvatarRates 的 Rate)不同。
    * */
    @Composable
    private fun HardChallengePopularityCard() {
        val avatars = viewModel.hardChallengePopularity?.avatar_list
        if (avatars.isNullOrEmpty()) return

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp, 0.dp)
                .radius(2.dp)
                .background(White)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PrimaryText(text = "全服热门角色", textSize = 15.sp)

            InfoText(
                text = "本期幽境危战中全服使用最多的角色,按顺序排列",
                fontSize = 12.sp
            )

            avatars.forEachIndexed { index, avatar ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    InfoText(
                        text = "${index + 1}",
                        fontSize = 13.sp,
                        modifier = Modifier.width(26.dp)
                    )

                    NetworkImage(
                        url = avatar.image,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    PrimaryText(
                        text = avatar.name,
                        textSize = 14.sp,
                        bold = false,
                        modifier = Modifier.weight(1f)
                    )

                    RoundedTag(
                        text = "${avatar.rarity}星",
                        backGroundColor = CardBackGroundColor_Gray_Dark,
                        textColor = Black_60
                    )
                }
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
                        PrimaryText(
                            text = "第 ${statistics.ScheduleId} 期 · 全服统计",
                            textSize = 16.sp,
                            modifier = Modifier.weight(1f)
                        )

                        PrimaryText(
                            text = if (viewModel.statisticsLastPeriod) "上期" else "本期",
                            textSize = 14.sp,
                            modifier = Modifier
                                .radius(2.dp)
                                .clickable { viewModel.toggleStatisticsPeriod() }
                                .padding(10.dp, 4.dp)
                        )
                    }

                    InfoText(
                        text = "参与统计的记录总数 ${statistics.RecordTotal}",
                        fontSize = 14.sp
                    )

                    InfoText(
                        text = "热门替补为全服记录中各角色作为后备队员的上阵比例",
                        fontSize = 12.sp
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
                        .radius(6.dp)
                        .background(CardBackGroundColor)
                        .padding(10.dp, 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    InfoText(
                        text = "${index + 1}",
                        fontSize = 13.sp,
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

                    PrimaryText(
                        text = avatar?.name ?: "${rate.Item}",
                        textSize = 14.sp,
                        bold = false,
                        modifier = Modifier.weight(1f)
                    )

                    InfoText(
                        text = String.format("%.2f%%", rate.Rate * 100),
                        fontSize = 13.sp
                    )
                }
            }

            item {
                com.lianyi.core.ui.components.spacer.NavigationBarPaddingSpacer()
            }
        }
    }
}
