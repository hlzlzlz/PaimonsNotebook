package com.lianyi.paimonsnotebook.ui.screen.travelers_diary.view

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import com.lianyi.paimonsnotebook.R
import com.lianyi.paimonsnotebook.common.components.charts.pie_charts.data.PieChartData
import com.lianyi.paimonsnotebook.common.util.metadata.genshin.ledger.LedgerHistoryFormatter
import com.lianyi.paimonsnotebook.common.web.hoyolab.takumi.game_record.ledger.LedgerData
import com.lianyi.paimonsnotebook.common.components.charts.pie_charts.view.PieChart
import com.lianyi.paimonsnotebook.common.components.dialog.ConfirmDialog
import com.lianyi.paimonsnotebook.common.components.layout.column.TopSlotColumnLayout
import com.lianyi.paimonsnotebook.common.components.loading.ContentLoadingLayout
import com.lianyi.paimonsnotebook.common.core.base.BaseActivity
import com.lianyi.paimonsnotebook.common.extension.modifier.radius.radius
import com.lianyi.paimonsnotebook.ui.screen.account.components.dialog.UserGameRolesDialog
import com.lianyi.paimonsnotebook.ui.screen.travelers_diary.viewmodel.TravelersDiaryScreenViewModel
import com.lianyi.paimonsnotebook.ui.theme.Black
import com.lianyi.paimonsnotebook.ui.theme.Black_60
import com.lianyi.paimonsnotebook.ui.theme.PaimonsNotebookTheme
import com.lianyi.paimonsnotebook.ui.theme.White
import com.lianyi.core.ui.components.text.InfoText
import com.lianyi.core.ui.components.text.PrimaryText

class TravelersDiaryScreen : BaseActivity() {
    private val viewModel by lazy {
        ViewModelProvider(this)[TravelersDiaryScreenViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PaimonsNotebookTheme(this) {
                TopSlotColumnLayout(
                    topSlot = {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PrimaryText(
                                text = "旅行者札记",
                                textSize = 18.sp
                            )

                            Spacer(modifier = Modifier.weight(1f))

                            /*
                            * 历史入口
                            * 用 RoundedTag 风格的轻量文案按钮,与页面既有点击区域一致
                            * */
                            PrimaryText(
                                text = if (viewModel.showHistory) "收起历史" else "历史",
                                textSize = 14.sp,
                                color = if (viewModel.showHistory) PrimaryColor else Black,
                                modifier = Modifier
                                    .radius(2.dp)
                                    .clickable { viewModel.toggleHistory() }
                                    .padding(8.dp, 6.dp)
                            )

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

                                Icon(
                                    painter = painterResource(id = R.drawable.ic_chevron_down),
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))
                        }
                    }
                ) {
                    ContentLoadingLayout(
                        loadingState = viewModel.loadingState,
                        successContent = {
                            val data = viewModel.ledgerData!!

                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(BackGroundLight)
                                    /*
                                    * ⚠️ 必须可纵向滚动:展开历史后内容会超过一屏,
                                    *    原先没有滚动修饰符,超出部分会被直接裁掉。
                                    *    (LazyRow 是横向的,不受影响。)
                                    * */
                                    .verticalScroll(rememberScrollState())
                            ) {
                                //月份切换
                                LazyRow(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp, 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val months = buildList {
                                        add(0 to "当月")
                                        data.optional_month.sortedDescending().forEach { month ->
                                            add(month to "${month}月")
                                        }
                                    }

                                    items(months) { (month, label) ->
                                        val selected = viewModel.currentMonth == month
                                        PrimaryText(
                                            text = label,
                                            textSize = 14.sp,
                                            bold = selected,
                                            color = if (selected) White else Black,
                                            modifier = Modifier
                                                .radius(2.dp)
                                                .background(if (selected) PrimaryColor else White)
                                                .clickable {
                                                    viewModel.onMonthChange(month)
                                                }
                                                .padding(10.dp, 6.dp)
                                        )
                                    }
                                }

                                StatCard {
                                    StatRow(
                                        title = "当月获取",
                                        currentValue = data.month_data.current_primogems,
                                        lastValue = data.month_data.last_primogems,
                                        rate = data.month_data.primogems_rate,
                                        label = "原石"
                                    )
                                    StatRow(
                                        title = "当月获取",
                                        currentValue = data.month_data.current_mora,
                                        lastValue = data.month_data.last_mora,
                                        rate = data.month_data.mora_rate,
                                        label = "摩拉"
                                    )
                                }

                                StatCard {
                                    StatRow(
                                        title = "今日获取",
                                        currentValue = data.day_data.current_primogems,
                                        lastValue = data.day_data.last_primogems,
                                        rate = null,
                                        label = "原石"
                                    )
                                    StatRow(
                                        title = "今日获取",
                                        currentValue = data.day_data.current_mora,
                                        lastValue = data.day_data.last_mora,
                                        rate = null,
                                        label = "摩拉"
                                    )
                                }

                                if (data.month_data.group_by.isNotEmpty()) {
                                    StatCard {
                                        PrimaryText(
                                            text = "当月原石来源",
                                            textSize = 16.sp
                                        )

                                        val pieData = buildGroupByPieData(data.month_data.group_by)

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            PieChart(
                                                data = pieData,
                                                modifier = Modifier.size(120.dp),
                                                radius = 120f
                                            )

                                            Spacer(modifier = Modifier.width(16.dp))

                                            Column(
                                                modifier = Modifier.weight(1f),
                                                verticalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                data.month_data.group_by.forEachIndexed { index, group ->
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Column(
                                                            modifier = Modifier
                                                                .size(10.dp)
                                                                .background(pieData[index].color),
                                                        ) {}

                                                        Spacer(modifier = Modifier.width(6.dp))

                                                        InfoText(
                                                            text = "${group.action} ${group.num} (${group.percent}%)",
                                                            fontSize = 13.sp
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                //历史快照(仅在展开时渲染)
                                if (viewModel.showHistory) {
                                    HistorySection(
                                        rows = viewModel.historyRows,
                                        hasData = viewModel.historySnapshots.isNotEmpty()
                                    )
                                }
                            }
                        }
                    )
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

    private fun buildGroupByPieData(groupBy: List<LedgerData.GroupBy>): List<PieChartData> {
        val colors = listOf(
            Color(0xFF4C8BF5),
            Color(0xFF5AC8FA),
            Color(0xFFFF9500),
            Color(0xFFFFCC00),
            Color(0xFF34C759),
            Color(0xFFAF52DE),
            Color(0xFFFF2D55),
            Color(0xFF8E8E93)
        )

        return groupBy.mapIndexed { index, group ->
            PieChartData(
                label = group.action,
                value = group.num,
                color = colors[index % colors.size]
            )
        }
    }

    @Composable
    private fun StatCard(content: @Composable () -> Unit) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp, 4.dp)
                .radius(2.dp)
                .background(White)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            content.invoke()
        }
    }

    @Composable
    private fun StatRow(
        title: String,
        currentValue: Int,
        lastValue: Int,
        rate: Int?,
        label: String,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            InfoText(
                text = label,
                fontSize = 14.sp,
                modifier = Modifier.width(36.dp)
            )

            PrimaryText(
                text = "$title $currentValue",
                textSize = 16.sp
            )

            Spacer(modifier = Modifier.weight(1f))

            if (rate != null) {
                Text(
                    text = "环比${if (rate >= 0) "+" else ""}${rate}%",
                    fontSize = 13.sp,
                    color = if (rate >= 0) Color(0xFF34C759) else Color(0xFFFF2D55)
                )

                Spacer(modifier = Modifier.width(8.dp))
            }

            InfoText(text = "上月$lastValue", fontSize = 13.sp)
        }
    }

    /*
    * 历史快照区
    *
    * 数据来自本地 ledger_month_snapshots 表(每次打开首页/札记页时自动存档),
    * 因此**越早开始用,历史越长** —— 空数据时要明确说明来源,
    * 否则用户会以为是功能坏了。
    * */
    @Composable
    private fun HistorySection(
        rows: List<LedgerHistoryFormatter.HistoryRow>,
        hasData: Boolean
    ) {
        StatCard {
            PrimaryText(
                text = "历史记录",
                textSize = 16.sp
            )

            when {
                !hasData -> {
                    InfoText(
                        text = "暂无历史数据。本机从首次打开本页或首页开始逐月记录," +
                                "服务端只保留近期月份,过期即无法补录。",
                        fontSize = 13.sp
                    )
                }

                else -> {
                    rows.forEach { row ->
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                PrimaryText(
                                    text = row.label,
                                    textSize = 15.sp
                                )

                                Spacer(modifier = Modifier.weight(1f))

                                InfoText(
                                    text = "原石 ${row.primogems}",
                                    fontSize = 13.sp
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                InfoText(
                                    text = "摩拉 ${row.mora}",
                                    fontSize = 13.sp
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                /*
                                * 环比文案为空表示"上一月无记录"(月份不连续),
                                * 此时不显示任何数字 —— 详见 LedgerHistoryFormatter
                                * */
                                val deltaText = LedgerHistoryFormatter.deltaText(row.primogemsDelta)
                                if (deltaText.isNotEmpty()) {
                                    Text(
                                        text = deltaText,
                                        fontSize = 12.sp,
                                        color = when {
                                            (row.primogemsDelta ?: 0) > 0 -> Color(0xFF34C759)
                                            (row.primogemsDelta ?: 0) < 0 -> Color(0xFFFF2D55)
                                            else -> Black_60
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    companion object {
        val BackGroundLight = Color(0xFFF5F5F5)
        val PrimaryColor = Color(0xFF4C8BF5)
    }
}
