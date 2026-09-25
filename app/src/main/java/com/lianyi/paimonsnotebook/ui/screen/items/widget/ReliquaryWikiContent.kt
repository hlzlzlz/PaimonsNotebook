package com.lianyi.paimonsnotebook.ui.screen.items.widget

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lianyi.core.ui.components.text.InfoText
import com.lianyi.core.ui.components.text.PrimaryText
import com.lianyi.paimonsnotebook.common.web.hutao.genshin.reliquary.ReliquarySetData
import com.lianyi.paimonsnotebook.ui.screen.items.components.content.ItemScreenContent
import com.lianyi.paimonsnotebook.ui.screen.items.components.item.icon.ItemIconCard
import com.lianyi.paimonsnotebook.ui.screen.items.components.layout.ItemInformationCardLayout
import com.lianyi.paimonsnotebook.ui.screen.items.data.ItemListCardData
import com.lianyi.paimonsnotebook.ui.screen.items.viewmodel.screen.ReliquaryScreenViewModel

/*
* 圣遗物资料内容(资料库的「圣遗物」标签)
*
* 从 ReliquaryScreen 抽出,使资料库与独立页共用同一份实现。
*
* statusBarPaddingEnabled:资料库外层已有状态栏占位与切换栏,必须传 false。
* */
@Composable
internal fun ReliquaryWikiContent(
    viewModel: ReliquaryScreenViewModel,
    statusBarPaddingEnabled: Boolean = true
) {
    val set = viewModel.currentItem ?: return

    ItemScreenContent(
        //圣遗物没有背景立绘(元数据只有套装图标),背景留空
        backgroundImgUrl = "",
        listButtonText = "圣遗物列表",
        baseInfoName = set.Name,
        baseInfoStarCount = viewModel.getReliquaryStar(set.SetId),
        baseInfoIconUrl = viewModel.getReliquaryIconUrl(set),
        tabs = viewModel.tabs,
        itemFilterViewModel = viewModel.itemFilterViewModel,
        statusBarPaddingEnabled = statusBarPaddingEnabled,
        onClickListButton = viewModel::toggleFilterContent,
        getListItemDataContent = viewModel::getItemDataContent,
        getItemListCardData = {
            ItemListCardData.fromReliquarySet(
                set = it,
                maxStar = viewModel.getReliquaryStar(it.SetId)
            )
        },
        onClickListItemCard = viewModel::onClickItem,
        //圣遗物套装不能加入养成计划,隐藏添加按钮
        showAddButton = false,
        onClickAddButton = {},
        cardContent = { CardContent(viewModel, it) }
    )
}

@Composable
private fun CardContent(viewModel: ReliquaryScreenViewModel, index: Int) {
    val set = viewModel.currentItem ?: return

    Crossfade(targetState = index, label = "") {
        when (it) {
            0 -> ReliquaryEffectContent(set)
            1 -> ReliquaryDescriptionContent(viewModel, set)
        }
    }
}

//套装效果:NeedNumber 与 Descriptions 按下标配对
@Composable
private fun ReliquaryEffectContent(set: ReliquarySetData) {
    ItemInformationCardLayout(margin = 0.dp, contentSpacer = 8.dp) {
        InfoText(text = "套装效果", fontSize = 14.sp)

        if (set.Descriptions.isEmpty()) {
            InfoText(text = "没有套装效果数据")
            return@ItemInformationCardLayout
        }

        set.NeedNumber.forEachIndexed { index, setNum ->
            /*
            * ⚠️ 必须按 Descriptions 的长度截断:元数据里两者长度不保证一致,
            *    越界会抛 IndexOutOfBounds(改造前的列表页就有这个判断,此处保留)。
            * */
            if (index >= set.Descriptions.size) {
                return@forEachIndexed
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PrimaryText(text = "${setNum} 件套", textSize = 13.sp)
                InfoText(text = set.Descriptions[index])
            }
        }
    }
}

@Composable
private fun ReliquaryDescriptionContent(
    viewModel: ReliquaryScreenViewModel,
    set: ReliquarySetData
) {
    ItemInformationCardLayout(margin = 0.dp, contentSpacer = 8.dp) {
        val star = viewModel.getReliquaryStar(set.SetId)

        Row(verticalAlignment = Alignment.CenterVertically) {
            ItemIconCard(
                url = viewModel.getReliquaryIconUrl(set),
                star = star,
                size = 48.dp,
                borderRadius = 6.dp
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column {
                PrimaryText(text = set.Name, textSize = 16.sp)
                InfoText(
                    text = "套装 ID ${set.SetId} · 共 ${set.NeedNumber.size} 档效果",
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        //取不到星级时明说,不留空也不编造
        InfoText(
            text = if (star > 0) "最高星级 $star 星" else "无星级数据"
        )
    }
}
