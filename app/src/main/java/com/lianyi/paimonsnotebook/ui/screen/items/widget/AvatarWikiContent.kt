package com.lianyi.paimonsnotebook.ui.screen.items.widget

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lianyi.paimonsnotebook.common.web.hutao.genshin.conveter.AssociationIconConverter
import com.lianyi.paimonsnotebook.common.web.hutao.genshin.intrinsic.AssociationType
import com.lianyi.paimonsnotebook.common.web.hutao.genshin.intrinsic.ElementType
import com.lianyi.paimonsnotebook.common.web.hutao.genshin.intrinsic.LevelLimit
import com.lianyi.paimonsnotebook.common.components.dialog.ConfirmDialog
import com.lianyi.paimonsnotebook.common.components.dialog.LazyColumnDialog
import com.lianyi.paimonsnotebook.ui.screen.items.components.content.ItemScreenContent
import com.lianyi.paimonsnotebook.ui.screen.items.components.cultivate.AvatarCultivateConfigCard
import com.lianyi.paimonsnotebook.ui.screen.items.components.information.InformationItem
import com.lianyi.paimonsnotebook.ui.screen.items.components.item.avatar.content.information.AvatarInformationContent
import com.lianyi.paimonsnotebook.ui.screen.items.components.item.avatar.content.skill.AvatarSkillContent
import com.lianyi.paimonsnotebook.ui.screen.items.components.item.material.ItemMaterialContent
import com.lianyi.paimonsnotebook.ui.screen.items.components.item.property.ItemPropertyContent
import com.lianyi.paimonsnotebook.ui.screen.items.data.ItemListCardData
import com.lianyi.paimonsnotebook.ui.screen.items.viewmodel.screen.AvatarScreenViewModel
import com.lianyi.paimonsnotebook.ui.theme.White

/*
* 角色资料内容(资料库的「角色」标签)
*
* 从 AvatarScreen 抽出,使资料库与独立页共用同一份实现。
*
* statusBarPaddingEnabled:资料库外层已有状态栏占位与切换栏,必须传 false。
* */
@Composable
internal fun AvatarWikiContent(
    viewModel: AvatarScreenViewModel,
    statusBarPaddingEnabled: Boolean = true
) {
    val avatar = viewModel.currentItem ?: return

    ItemScreenContent(
        backgroundImgUrl = avatar.gachaAvatarImg,
        listButtonText = "角色列表",
        baseInfoName = avatar.name,
        baseInfoStarCount = avatar.starCount,
        baseInfoIconUrl = avatar.fetterInfo.associationIconUrl,
        tabs = viewModel.tabs,
        itemFilterViewModel = viewModel.itemFilterViewModel,
        statusBarPaddingEnabled = statusBarPaddingEnabled,
        onClickListButton = viewModel::toggleFilterContent,
        getListItemDataContent = viewModel::getItemDataContent,
        listVerticalEndInformationContentSlot = { item ->
            InformationItem(
                text = AssociationType.getAssociationNameByType(item.fetterInfo.Association),
                iconUrl = AssociationIconConverter.avatarAssociationToUrl(item.fetterInfo.Association),
                paddingValues = PaddingValues(2.dp)
            )

            InformationItem(
                backgroundColor = ElementType.getElementColorByName(item.fetterInfo.VisionBefore),
                iconResId = ElementType.getElementResourceIdByName(item.fetterInfo.VisionBefore),
                textColor = White,
                paddingValues = PaddingValues(2.dp)
            )
        },
        getItemListCardData = ItemListCardData::fromAvatar,
        onClickListItemCard = viewModel::onClickItem,
        cardContent = { CardContent(viewModel, it) },
        onClickAddButton = viewModel::addCurrentItemToCultivateProject,
        itemAddedCurrentCultivateProject = viewModel.itemAddedToCurrentCultivateProject
    )

    /*
    * ⚠️ 养成计划相关的两个弹窗必须放在这里而不是各页面里。
    *
    * "添加到养成计划"是角色资料页的既有功能(点右上角 + 号触发),
    * 如果只放在独立页,资料库里的角色标签就没有这个功能了 ——
    * 两处入口必须共用同一份实现,否则会出现"同一个界面在两个入口下
    * 功能不一样"的隐蔽差异。
    * */
    if (viewModel.showItemConfigDialog && viewModel.currentItem != null) {
        LazyColumnDialog(
            title = if (viewModel.itemAddedToCurrentCultivateProject) "更新当前养成计划" else "添加到当前养成计划",
            buttons = viewModel.itemConfigDialogButtons,
            onDismissRequest = viewModel::showItemConfigDialogRequestDismiss,
            onClickButton = viewModel::onClickItemConfigDialogButton
        ) {
            item {
                AvatarCultivateConfigCard(
                    avatarData = viewModel.currentItem!!,
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
private fun CardContent(viewModel: AvatarScreenViewModel, index: Int) {
    val avatar = viewModel.currentItem ?: return

    Crossfade(targetState = index, label = "") {
        when (it) {
            0 -> ItemPropertyContent(
                iconUrl = avatar.iconUrl,
                name = avatar.name,
                maxLevel = LevelLimit.AvatarMaxLevel,
                compareIconUrl = viewModel.compareItem?.iconUrl ?: "",
                propertyList = viewModel.propertyList,
                compareItemPropertyList = viewModel.compareItemPropertyList,
                onClickCompareItem = viewModel::onClickCompareItem,
                onLevelChange = viewModel::onChangeItemLevel,
                onPromotedChange = viewModel::onPromotedChange,
                informationSlot = {
                    InformationItem(
                        iconResId = ElementType.getElementResourceIdByName(avatar.fetterInfo.VisionBefore),
                        textColor = White,
                        iconSize = 20.dp,
                        textSize = 14.sp,
                        text = avatar.fetterInfo.VisionBefore,
                        backgroundColor = ElementType.getElementColorByName(avatar.fetterInfo.VisionBefore),
                    )

                    InformationItem(
                        iconUrl = avatar.weaponIconUrl,
                        textColor = White,
                        iconSize = 20.dp,
                        textSize = 14.sp,
                        text = avatar.weaponTypeName,
                        backgroundColor = ElementType.getElementColorByName(avatar.fetterInfo.VisionBefore),
                    )
                }
            )

            1 -> AvatarSkillContent(
                skillList = viewModel.skillList,
                iconBackgroundColor = avatar.fetterInfo.elementColor
            )

            2 -> AvatarSkillContent(
                skillList = viewModel.talentList,
                iconBackgroundColor = avatar.fetterInfo.elementColor,
                enabledIconBorder = true
            )

            3 -> AvatarInformationContent(avatar = avatar)

            4 -> ItemMaterialContent(list = viewModel.materialList)
        }
    }
}
