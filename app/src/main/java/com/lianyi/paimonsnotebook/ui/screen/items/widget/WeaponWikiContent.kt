package com.lianyi.paimonsnotebook.ui.screen.items.widget

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lianyi.paimonsnotebook.common.components.dialog.ConfirmDialog
import com.lianyi.paimonsnotebook.common.components.dialog.LazyColumnDialog
import com.lianyi.paimonsnotebook.common.components.layout.blur_card.widget.ItemLevelSlider
import com.lianyi.paimonsnotebook.ui.screen.items.components.content.ItemScreenContent
import com.lianyi.paimonsnotebook.ui.screen.items.components.cultivate.WeaponCultivateConfigCard
import com.lianyi.paimonsnotebook.ui.screen.items.components.information.InformationItem
import com.lianyi.paimonsnotebook.ui.screen.items.components.item.icon.ItemIconCard
import com.lianyi.paimonsnotebook.ui.screen.items.components.item.material.ItemMaterialContent
import com.lianyi.paimonsnotebook.ui.screen.items.components.item.property.ItemPropertyContent
import com.lianyi.paimonsnotebook.ui.screen.items.components.layout.ItemInformationCardLayout
import com.lianyi.paimonsnotebook.ui.screen.items.data.ItemListCardData
import com.lianyi.paimonsnotebook.ui.screen.items.viewmodel.screen.WeaponScreenViewModel

/*
* 武器资料内容(资料库的「武器」标签)
*
* 从 WeaponScreen 抽出,使资料库与独立页共用同一份实现。
*
* statusBarPaddingEnabled:资料库外层已有状态栏占位与切换栏,必须传 false。
* */
@Composable
internal fun WeaponWikiContent(
    viewModel: WeaponScreenViewModel,
    statusBarPaddingEnabled: Boolean = true
) {
    val weapon = viewModel.currentItem ?: return

    ItemScreenContent(
        backgroundImgUrl = weapon.gachaEquipImg,
        itemImageContentScale = ContentScale.FillHeight,
        listButtonText = "武器列表",
        enabledItemShadow = true,
        itemBackgroundResId = weapon.weaponGachaTypeBgResId,
        baseInfoName = weapon.name,
        baseInfoStarCount = weapon.rankLevel,
        baseInfoIconUrl = weapon.weaponIconUrl,
        tabs = viewModel.tabs,
        itemFilterViewModel = viewModel.itemFilterViewModel,
        statusBarPaddingEnabled = statusBarPaddingEnabled,
        onClickListButton = viewModel::toggleFilterContent,
        getListItemDataContent = viewModel::getItemDataContent,
        listVerticalEndInformationContentSlot = { item ->
            InformationItem(
                text = item.weaponTypeName,
                iconUrl = item.weaponIconUrl,
                paddingValues = PaddingValues(2.dp)
            )
        },
        getItemListCardData = ItemListCardData::fromWeapon,
        onClickListItemCard = viewModel::onClickItem,
        onClickAddButton = viewModel::addCurrentItemToCultivateProject,
        itemAddedCurrentCultivateProject = viewModel.itemAddedToCurrentCultivateProject,
        cardContent = { CardContent(viewModel, it) }
    )

    /*
    * ⚠️ 同 AvatarWikiContent:养成计划弹窗必须放在共用的内容组件里,
    *    否则资料库入口会缺掉"添加到养成计划"这个既有功能。
    * */
    if (viewModel.showItemConfigDialog && viewModel.currentItem != null) {
        LazyColumnDialog(
            title = if (viewModel.itemAddedToCurrentCultivateProject) "更新当前养成计划" else "添加到当前养成计划",
            buttons = viewModel.itemConfigDialogButtons,
            onDismissRequest = viewModel::showItemConfigDialogRequestDismiss,
            onClickButton = viewModel::onClickItemConfigDialogButton
        ) {
            item {
                WeaponCultivateConfigCard(
                    weapon = viewModel.currentItem!!,
                    list = viewModel.cultivateConfigList
                )
            }
        }
    }

    if (viewModel.showNoCultivateProjectNoticeDialog) {
        ConfirmDialog(
            title = "养成计划",
            content = "没有找到养成计划,点击确定跳转养成计划设置页面进行添加",
            onConfirm = viewModel::goCultivateProjectOptionScreen,
            onCancel = viewModel::dismissNoCultivateProjectNoticeDialog
        )
    }
}

@Composable
private fun CardContent(viewModel: WeaponScreenViewModel, index: Int) {
    val weapon = viewModel.currentItem ?: return

    Crossfade(targetState = index, label = "") {
        when (it) {
            0 -> ItemPropertyContent(
                iconUrl = weapon.iconUrl,
                name = weapon.name,
                maxLevel = weapon.maxLevel,
                compareIconUrl = viewModel.compareItem?.iconUrl ?: "",
                propertyList = viewModel.propertyList,
                compareItemPropertyList = viewModel.compareItemPropertyList,
                showPromotedButton = false,
                onClickCompareItem = viewModel::onClickCompareItem,
                onLevelChange = viewModel::onChangeItemLevel,
                onPromotedChange = viewModel::onPromotedChange,
                informationSlot = {
                    InformationItem(
                        iconUrl = weapon.weaponIconUrl,
                        iconSize = 20.dp,
                        textSize = 14.sp,
                        text = weapon.weaponTypeName
                    )
                }
            )

            1 -> {
                val affix = viewModel.weaponAffixFormat

                if (affix != null) {
                    Column {
                        ItemInformationCardLayout(margin = 0.dp, contentSpacer = 8.dp) {
                            Text(
                                weapon.affix?.Name ?: "",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )

                            com.lianyi.core.ui.components.text.RichText(text = affix.affixText)
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        ItemLevelSlider(
                            value = affix.sliderValue,
                            level = affix.currentLevel,
                            onValueChange = affix::onSliderValueChange,
                            range = (1f..affix.maxLevel.toFloat()),
                        )
                    }
                }
            }

            2 -> {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        ItemIconCard(
                            url = weapon.iconUrl,
                            star = weapon.rankLevel,
                            borderRadius = 4.dp
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = weapon.description,
                            fontSize = 12.sp
                        )
                    }

                    Row(modifier = Modifier.fillMaxWidth()) {
                        ItemIconCard(
                            url = weapon.awakenIconUrl,
                            star = weapon.rankLevel,
                            borderRadius = 4.dp
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        Text(
                            text = "${weapon.name} 突破后",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            3 -> {
                ItemMaterialContent(list = viewModel.materialList)
            }
        }
    }
}
