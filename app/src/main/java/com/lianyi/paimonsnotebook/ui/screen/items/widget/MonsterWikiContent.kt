package com.lianyi.paimonsnotebook.ui.screen.items.widget

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lianyi.core.ui.components.text.InfoText
import com.lianyi.core.ui.components.text.PrimaryText
import com.lianyi.paimonsnotebook.common.components.media.NetworkImage
import com.lianyi.paimonsnotebook.common.web.hutao.genshin.monster.MonsterData
import com.lianyi.paimonsnotebook.ui.screen.items.components.content.ItemScreenContent
import com.lianyi.paimonsnotebook.ui.screen.items.components.layout.ItemInformationCardLayout
import com.lianyi.paimonsnotebook.ui.screen.items.data.ItemListCardData
import com.lianyi.paimonsnotebook.ui.screen.items.viewmodel.MonsterScreenViewModel

/*
* 怪物资料内容(资料库的「怪物」标签)
*
* 从 MonsterScreen 抽出,使资料库与独立页共用同一份实现。
*
* statusBarPaddingEnabled:资料库外层已有状态栏占位与切换栏,必须传 false。
* */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MonsterWikiContent(
    viewModel: MonsterScreenViewModel,
    statusBarPaddingEnabled: Boolean = true
) {
    val monster = viewModel.currentItem ?: return

    ItemScreenContent(
        //怪物没有立绘(元数据只有图标),背景留空由默认底图兜底
        backgroundImgUrl = "",
        listButtonText = "怪物列表",
        baseInfoName = monster.name,
        //MonsterData 无星级,传 0(不编造星级)
        baseInfoStarCount = 0,
        baseInfoIconUrl = monster.iconUrl,
        tabs = viewModel.tabs,
        itemFilterViewModel = viewModel.itemFilterViewModel,
        statusBarPaddingEnabled = statusBarPaddingEnabled,
        onClickListButton = viewModel::toggleFilterContent,
        getListItemDataContent = viewModel::getItemDataContent,
        getItemListCardData = ItemListCardData::fromMonster,
        onClickListItemCard = viewModel::onClickItem,
        //怪物不能加入养成计划,隐藏添加按钮
        showAddButton = false,
        onClickAddButton = {},
        cardContent = { CardContent(viewModel, it) }
    )
}

@Composable
private fun CardContent(viewModel: MonsterScreenViewModel, index: Int) {
    val monster = viewModel.currentItem ?: return

    Crossfade(targetState = index, label = "") {
        when (it) {
            0 -> MonsterPropertyContent(monster)
            1 -> MonsterDropsContent(viewModel, monster)
            2 -> MonsterDescriptionContent(monster)
        }
    }
}

//基础属性与元素抗性
@Composable
private fun MonsterPropertyContent(monster: MonsterData) {
    ItemInformationCardLayout(margin = 0.dp, contentSpacer = 8.dp) {
        PrimaryText(text = monster.title, textSize = 14.sp)

        //机关/测试类条目没有 BaseValue,缺失时整块不展示而不是显示 0
        val baseValue = monster.baseValue

        if (baseValue == null) {
            InfoText(text = "该条目没有基础属性数据")
        } else {
            InfoText(text = "基础属性", fontSize = 14.sp)

            InfoRow("基础生命", formatBaseValue(baseValue.HpBase))
            InfoRow("基础攻击", formatBaseValue(baseValue.AttackBase))
            InfoRow("基础防御", formatBaseValue(baseValue.DefenseBase.toFloat()))

            Spacer(modifier = Modifier.height(6.dp))

            InfoText(text = "抗性", fontSize = 14.sp)

            InfoRow("物理", formatResist(baseValue.PhysicalSubHurt))
            InfoRow("火", formatResist(baseValue.FireSubHurt))
            InfoRow("雷", formatResist(baseValue.ElecSubHurt))
            InfoRow("水", formatResist(baseValue.WaterSubHurt))
            InfoRow("草", formatResist(baseValue.GrassSubHurt))
            InfoRow("风", formatResist(baseValue.WindSubHurt))
            InfoRow("冰", formatResist(baseValue.IceSubHurt))
            InfoRow("岩", formatResist(baseValue.RockSubHurt))
        }
    }
}

//掉落材料
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MonsterDropsContent(viewModel: MonsterScreenViewModel, monster: MonsterData) {
    val drops = monster.drops.orEmpty()

    ItemInformationCardLayout(margin = 0.dp, contentSpacer = 8.dp) {
        if (drops.isEmpty()) {
            InfoText(text = "没有掉落数据")
            return@ItemInformationCardLayout
        }

        InfoText(text = "掉落材料(${drops.size} 项)", fontSize = 14.sp)

        /*
        * ⚠️ 用 FlowRow 而不是 Row:掉落最多 8 项、每项宽 56dp,
        *    横排总宽必然超过 360dp 的有效宽度(见 AGENTS.md 宽度约束),
        *    超出部分会被裁掉。
        * */
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            drops.take(8).forEach { materialId ->
                val material = viewModel.getMaterialById(materialId)

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(56.dp)
                ) {
                    NetworkImage(
                        url = material.iconUrl,
                        modifier = Modifier.size(40.dp)
                    )

                    InfoText(
                        text = material.Name,
                        fontSize = 10.sp,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun MonsterDescriptionContent(monster: MonsterData) {
    ItemInformationCardLayout(margin = 0.dp, contentSpacer = 8.dp) {
        PrimaryText(text = monster.title, textSize = 14.sp)

        val description = monster.description.orEmpty()

        if (description.isBlank()) {
            InfoText(text = "没有描述数据")
        } else {
            InfoText(text = description)
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        InfoText(
            text = label,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f)
        )

        PrimaryText(text = value, textSize = 13.sp)
    }
}

private fun formatBaseValue(value: Float): String =
    if (value % 1f == 0f) "${value.toInt()}" else String.format("%.1f", value)

private fun formatResist(value: Float): String =
    String.format("%.1f%%", value * 100)
