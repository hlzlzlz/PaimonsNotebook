package com.lianyi.paimonsnotebook.ui.screen.cultivate_project.components.page

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lianyi.core.ui.components.text.InfoText
import com.lianyi.core.ui.components.text.PrimaryText
import com.lianyi.paimonsnotebook.ui.theme.Black
import com.lianyi.paimonsnotebook.common.components.lazy.ContentSpacerLazyVerticalGrid
import com.lianyi.paimonsnotebook.common.data.popup.PopupWindowPositionProvider
import com.lianyi.paimonsnotebook.common.extension.modifier.radius.radius
import com.lianyi.paimonsnotebook.common.util.cultivation.ResinStatisticsCalculator
import com.lianyi.paimonsnotebook.common.web.hutao.genshin.item.Material
import androidx.compose.ui.Alignment
import com.lianyi.paimonsnotebook.ui.screen.cultivate_project.components.group.CultivateMaterialGroupHeader
import com.lianyi.paimonsnotebook.ui.screen.cultivate_project.components.group.CultivateMaterialGroupItem
import com.lianyi.paimonsnotebook.ui.screen.cultivate_project.data.EntityBaseInfo
import com.lianyi.paimonsnotebook.ui.screen.cultivate_project.data.MaterialBaseInfo
import com.lianyi.paimonsnotebook.ui.theme.CardBackGroundColor_Light_1

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CultivateProjectOverallPage(
    showGridLayout: Boolean,
    overallMaterialBaseInfoGroupList: List<List<MaterialBaseInfo>>,
    overallMaterialBaseInfoGroupListFlatten: List<MaterialBaseInfo>,
    getOverallEntityBaseInfoListByMaterialId: (Int) -> List<EntityBaseInfo>,
    onShowMaterialInfoPopupDialog: (Material, PopupWindowPositionProvider) -> Unit,
    onShowEntityInfoPopupDialog: (Int, PopupWindowPositionProvider) -> Unit,
    resinStatisticsResult: ResinStatisticsCalculator.ResinResult? = null
) {
    Crossfade(targetState = showGridLayout, label = "") {
        if (it) {
            ContentSpacerLazyVerticalGrid(
                columns = GridCells.Adaptive(60.dp),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                statusBarPaddingEnabled = false,
                modifier = Modifier.fillMaxSize()
            ) {
                item(span = { GridItemSpan(this.maxLineSpan) }) {
                    ResinSummaryCard(resinStatisticsResult)
                }

                items(overallMaterialBaseInfoGroupListFlatten) { baseInfo ->
                    CultivateMaterialGroupHeader(
                        materialBaseInfo = baseInfo,
                        imageSize = 55.dp,
                        onShowMaterialInfoPopupDialog = onShowMaterialInfoPopupDialog
                    )
                }
            }
        } else {
            ContentSpacerLazyVerticalGrid(
                columns = GridCells.Adaptive(40.dp),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                statusBarPaddingEnabled = false,
                modifier = Modifier.fillMaxSize()
            ) {

                item(span = { GridItemSpan(this.maxLineSpan) }) {
                    ResinSummaryCard(resinStatisticsResult)
                }

                items(overallMaterialBaseInfoGroupList, span = {
                    GridItemSpan(this.maxLineSpan)
                }) { materialBaseInfoList ->
                    var showEntityList by remember {
                        mutableStateOf(false)
                    }

                    Column(
                        modifier = Modifier
                            .radius(8.dp)
                            .fillMaxSize()
                            .background(CardBackGroundColor_Light_1)
                            .clickable {
                                showEntityList = !showEntityList
                            }
                            .padding(8.dp)
                    ) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            materialBaseInfoList.forEach { baseInfo ->

                                CultivateMaterialGroupHeader(
                                    materialBaseInfo = baseInfo,
                                    onShowMaterialInfoPopupDialog = onShowMaterialInfoPopupDialog
                                )
                            }
                        }

                        AnimatedVisibility(visible = showEntityList) {
                            Column {
                                Spacer(modifier = Modifier.height(12.dp))

                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    getOverallEntityBaseInfoListByMaterialId(materialBaseInfoList.first().material.Id)
                                        .forEach { entityBaseInfo ->
                                            CultivateMaterialGroupItem(
                                                entityBaseInfo = entityBaseInfo,
                                                onShowEntityInfoPopupDialog = onShowEntityInfoPopupDialog
                                            )
                                        }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResinSummaryCard(result: ResinStatisticsCalculator.ResinResult?) {
    if (result == null || result.items.isEmpty()) {
        return
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .radius(6.dp)
            .background(CardBackGroundColor_Light_1)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PrimaryText(text = "树脂预估", textSize = 14.sp)

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "约${result.totalResin}树脂 · 约${result.days}天",
                fontSize = 12.sp,
                color = Color(0xFFE65100),
                fontWeight = FontWeight.SemiBold
            )
        }


        result.items.forEach { item ->
            Row(modifier = Modifier.fillMaxWidth()) {
                InfoText(
                    text = item.title,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f)
                )

                InfoText(
                    text = "${item.runCount}次 · ${item.totalResin}树脂",
                    fontSize = 12.sp
                )
            }
        }

        InfoText(text = "按世界等级9单次掉落期望估算,仅供参考")
    }
}
