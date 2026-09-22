package com.lianyi.paimonsnotebook.ui.screen.dps.view

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import com.lianyi.core.ui.components.text.InfoText
import com.lianyi.core.ui.components.text.PrimaryText
import com.lianyi.paimonsnotebook.R
import com.lianyi.paimonsnotebook.common.components.layout.column.TopSlotColumnLayout
import com.lianyi.paimonsnotebook.common.components.loading.ContentLoadingLayout
import com.lianyi.paimonsnotebook.common.components.loading.ContentLoadingPlaceholder
import com.lianyi.paimonsnotebook.common.components.placeholder.ErrorPlaceholder
import com.lianyi.paimonsnotebook.common.core.base.BaseActivity
import com.lianyi.paimonsnotebook.common.extension.modifier.radius.radius
import com.lianyi.paimonsnotebook.ui.screen.dps.viewmodel.DpsCalculatorScreenViewModel
import com.lianyi.paimonsnotebook.ui.theme.CardBackGroundColor
import com.lianyi.paimonsnotebook.ui.theme.PaimonsNotebookTheme

/*
* 队伍 DPS 计算器页面
*
* ⚠️ 三条必须保留的"诚实边界"(不是待办,是设计要求):
*
* 1. **风险提示必须显示**:面板字符串格式未经真机验证(`panelFormatVerified == false`),
*    用户必须能看到这一点,而不是拿到一个看起来很确定的错数。
* 2. **不显示"每秒伤害"除非用户填了循环耗时**:本机元数据没有攻速/帧数,
*    自动换算等于编造。未填时只显示"每循环伤害"。
* 3. **被跳过的剧变反应与不可用成员必须列出**:否则用户会以为这些伤害被算进去了。
*
* UI 约定(遵守项目既有体系,不用 Material 那套):
*   PrimaryText / InfoText、TopSlotColumnLayout、CardBackGroundColor + radius(6.dp)、
*   无 AlertDialog / 无 material3。
* 宽度:有效宽度恒 360dp(Theme 里 density = widthPixels/360f),横排内容超宽会溢出被裁,
*   故凡可能较长的行都用 horizontalScroll,不用 LazyRow(外层 IntrinsicSize.Min 会崩)。
* */
class DpsCalculatorScreen : BaseActivity() {

    private val viewModel by lazy {
        ViewModelProvider(this)[DpsCalculatorScreenViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PaimonsNotebookTheme(this) {
                ContentLoadingLayout(
                    loadingState = viewModel.loadingState,
                    errorContent = { ErrorPlaceholder("加载失败,请稍后再试") },
                    loadingContent = { ContentLoadingPlaceholder() }
                ) {
                    TopSlotColumnLayout(
                        topSlot = {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp, 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                PrimaryText(text = "队伍 DPS 计算器")
                                Row(
                                    modifier = Modifier
                                        .radius(4.dp)
                                        .clickable { viewModel.clearTeam() }
                                        .padding(4.dp)
                                ) {
                                    InfoText(text = "清空队伍")
                                }
                            }
                        }
                    ) {
                        DpsCalculatorContent()
                    }
                }
            }
        }
    }

    @Composable
    private fun DpsCalculatorContent() {
        /*
        * ⚠️ 结构上刻意**不用** `Column(verticalScroll)` 包 `LazyColumn`。
        *
        * 原因:那是 Compose 的已知反模式 —— 外层给无限高约束,
        * 内层 LazyColumn 无法测量,会抛
        * "Vertically scrollable component was measured with an infinity
        *  maximum height constraints"。
        * 项目内唯一一处 `verticalScroll` + `LazyColumn` 共存的历史写法
        * (MonsterScreen 的详情 Dialog)并不嵌套,不构成先例。
        *
        * 正确做法:**整页用单个 LazyColumn**,把风险提示/选择区/结果都当 item。
        * */
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp)
        ) {
            item { RiskNotice() }

            item { SectionTitle("选择队伍成员(最多 ${DpsCalculatorScreenViewModel.MAX_TEAM_SIZE} 人)") }
            item {
                InfoText(
                    text = "已选 ${viewModel.teamCharacterIds.size} 人。" +
                            "⚠️ 游戏不提供队伍编成接口,此队伍由你手动选择。"
                )
            }

            // 角色列表:直接作为 item 铺开(不再嵌套 LazyColumn)
            items(viewModel.characterList, key = { it.id }) { character ->
                val selected = viewModel.teamCharacterIds.contains(character.id)
                CharacterRow(
                    name = character.name,
                    level = character.level,
                    element = character.element,
                    selected = selected,
                    onClick = { viewModel.toggleMember(character.id) }
                )
            }

            item {
                // ---- 循环耗时(可选) ----
                SectionTitle("循环耗时(可选)")
                InfoText(
                    text = "本机元数据没有攻击速度/动画帧数据,无法自动换算 DPS。" +
                            "填入一轮循环的秒数后才会显示每秒伤害;耗时由你提供,非游戏真实值。"
                )
                QuickSecondsRow()

                // ---- 计算按钮 ----
                Row(
                    modifier = Modifier
                        .padding(vertical = 12.dp)
                        .radius(6.dp)
                        .background(CardBackGroundColor)
                        .clickable { viewModel.calculate() }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    PrimaryText(text = "开始计算")
                }
            }

            // ---- 结果 ----
            viewModel.result?.let { r ->
                item { ResultSection(r) }
            }

            // 底部留白,避免被导航栏遮住
            item { Column(modifier = Modifier.height(24.dp)) {} }
        }
    }

    /*
    * ⚠️ 风险提示 —— 用户已确认要显示。
    * 这不是"待办",而是本功能的**诚实边界**:
    * character/detail 的面板字符串格式未在真机验证过。
    * */
    @Composable
    private fun RiskNotice() {
        if (!viewModel.panelFormatVerified) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .radius(6.dp)
                    .background(CardBackGroundColor)
                    .padding(10.dp)
            ) {
                PrimaryText(text = "⚠️ 结果为估算值,请阅读以下说明", textSize = 14.sp)
                InfoText(
                    modifier = Modifier.padding(top = 4.dp),
                    text = "1. 面板数据取自米游社接口,其字符串格式尚未经真机验证;\n" +
                            "2. 角色等级按 90、目标等级 90、抗性 10% 假定;\n" +
                            "3. 不含元素附着、ICD、减抗、无视防御;\n" +
                            "4. 剧变反应(超载/感电/绽放/激化等)不支持,会在结果中列出而未计入;"
                )
            }
        }
    }

    @Composable
    private fun QuickSecondsRow() {
        // ⚠️ 横向可能超宽 ⇒ 用 horizontalScroll(不可用 LazyRow)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(15.0, 20.0, 25.0, 30.0).forEach { s ->
                val active = viewModel.rotationSeconds == s
                Column(
                    modifier = Modifier
                        .radius(4.dp)
                        .background(if (active) Color(0xFF4A4A4A) else CardBackGroundColor)
                        .clickable { viewModel.updateRotationSeconds(s) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    InfoText(text = "${s.toInt()} 秒")
                }
            }
        }
    }

    @Composable
    private fun ResultSection(result: com.lianyi.paimonsnotebook.common.util.damage.TeamDamageResult) {
        SectionTitle("结果")

        // 队伍合计
        ResultCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                MetricItem("每循环期望", formatNumber(result.teamExpected))
                MetricItem("非暴击", formatNumber(result.teamNonCrit))
                MetricItem("暴击", formatNumber(result.teamCrit))
            }

            // ⚠️ 只在用户填了耗时后才显示 DPS;否则明确说明为何不显示
            val seconds = viewModel.rotationSeconds
            val dps = com.lianyi.paimonsnotebook.common.util.damage.TeamDamageCalculator
                .toDps(result.teamExpected, seconds)
            if (dps != null) {
                InfoText(
                    modifier = Modifier.padding(top = 6.dp),
                    text = "每秒伤害(按你填的 ${seconds.toInt()} 秒/循环):${formatNumber(dps)}" +
                            " —— 耗时由你提供,非游戏真实值"
                )
            } else {
                InfoText(
                    modifier = Modifier.padding(top = 6.dp),
                    text = "未填循环耗时,故不显示每秒伤害(本机无攻速/帧数数据,不自动换算)"
                )
            }
        }

        // 各成员明细
        result.members.forEach { member ->
            SectionTitle(member.name)
            ResultCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    MetricItem("期望", formatNumber(member.expectedTotal))
                    MetricItem("非暴击", formatNumber(member.nonCritTotal))
                    MetricItem("暴击", formatNumber(member.critTotal))
                }
                member.perAction.forEach { action ->
                    InfoText(
                        modifier = Modifier.padding(top = 3.dp),
                        text = "· ${action.label} ×${action.count}  " +
                                "倍率 ${formatPercent(action.multiplier)}  →  " +
                                "期望 ${formatNumber(action.expected)}"
                    )
                }
            }
        }

        // ⚠️ 被跳过的剧变反应必须列出
        if (result.skippedReactions.isNotEmpty()) {
            SectionTitle("未计入的剧变反应")
            ResultCard {
                InfoText(text = "以下反应本版本不支持计算(缺精通系数表),故未计入合计:")
                result.skippedReactions.forEach {
                    InfoText(
                        modifier = Modifier.padding(top = 3.dp),
                        text = "· ${it.memberName} · ${it.actionLabel}(${it.reactionName})"
                    )
                }
            }
        }

        // ⚠️ 面板不可用的成员必须列出
        if (result.skippedMembers.isNotEmpty()) {
            SectionTitle("未参与计算的成员")
            ResultCard {
                InfoText(text = "以下成员因面板数据缺失(缺攻击力或暴击率)未参与计算:")
                result.skippedMembers.forEach {
                    InfoText(modifier = Modifier.padding(top = 3.dp), text = "· $it")
                }
            }
        }
    }

    @Composable
    private fun SectionTitle(text: String) {
        PrimaryText(
            modifier = Modifier.padding(top = 14.dp, bottom = 4.dp),
            text = text,
            textSize = 15.sp
        )
    }

    @Composable
    private fun ResultCard(content: @Composable () -> Unit) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .radius(6.dp)
                .background(CardBackGroundColor)
                .padding(10.dp)
        ) {
            content()
        }
    }

    @Composable
    private fun MetricItem(label: String, value: String) {
        Column {
            InfoText(text = label)
            PrimaryText(text = value, textSize = 14.sp)
        }
    }

    @Composable
    private fun CharacterRow(
        name: String,
        level: Int,
        element: String,
        selected: Boolean,
        onClick: () -> Unit
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
                .radius(6.dp)
                .background(if (selected) Color(0xFF4A4A4A) else CardBackGroundColor)
                .clickable { onClick() }
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 选中标记:用项目**实际存在**的图标
            // (实测 drawable 下无 ic_check_box/ic_radio,但有 ic_checkmark_circle[_full])
            Icon(
                painter = painterResource(
                    id = if (selected) R.drawable.ic_checkmark_circle_full
                    else R.drawable.ic_checkmark_circle
                ),
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            PrimaryText(
                modifier = Modifier
                    .padding(start = 8.dp)
                    .weight(1f, fill = false),
                text = name
            )
            InfoText(
                modifier = Modifier.padding(start = 8.dp),
                text = "Lv.$level · $element"
            )
        }
    }

    private fun formatNumber(v: Double): String = when {
        v.isNaN() || v.isInfinite() -> "-"
        v >= 10000 -> String.format("%.1f万", v / 10000)
        else -> String.format("%.0f", v)
    }

    private fun formatPercent(v: Double): String = String.format("%.1f%%", v * 100)
}
