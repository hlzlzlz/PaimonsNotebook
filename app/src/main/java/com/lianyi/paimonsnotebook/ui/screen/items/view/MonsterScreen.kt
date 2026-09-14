package com.lianyi.paimonsnotebook.ui.screen.items.view

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.material.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModelProvider
import com.lianyi.core.ui.components.text.InfoText
import com.lianyi.core.ui.components.text.PrimaryText
import com.lianyi.paimonsnotebook.common.components.lazy.ContentSpacerLazyColumn
import com.lianyi.paimonsnotebook.common.components.loading.ContentLoadingLayout
import com.lianyi.paimonsnotebook.common.components.media.NetworkImage
import com.lianyi.paimonsnotebook.common.core.base.BaseActivity
import com.lianyi.paimonsnotebook.common.extension.modifier.radius.radius
import com.lianyi.paimonsnotebook.ui.screen.items.components.item.icon.ItemIconCard
import com.lianyi.paimonsnotebook.ui.screen.items.viewmodel.MonsterScreenViewModel
import com.lianyi.paimonsnotebook.ui.theme.BackGroundColor
import com.lianyi.paimonsnotebook.ui.theme.CardBackGroundColor
import com.lianyi.paimonsnotebook.ui.theme.PaimonsNotebookTheme
import com.lianyi.paimonsnotebook.ui.theme.White

class MonsterScreen : BaseActivity() {

    private val viewModel by lazy {
        ViewModelProvider(this)[MonsterScreenViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PaimonsNotebookTheme(this) {
                ContentLoadingLayout(loadingState = viewModel.loadingState) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(BackGroundColor)
                    ) {
                        TextField(
                            value = viewModel.searchKeyword,
                            onValueChange = {
                                viewModel.searchKeyword = it
                            },
                            placeholder = {
                                Text(text = "搜索怪物名称", fontSize = 14.sp)
                            },
                            singleLine = true,
                            colors = TextFieldDefaults.textFieldColors(
                                backgroundColor = CardBackGroundColor,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp, 6.dp)
                                .radius(6.dp)
                        )

                        ContentSpacerLazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(12.dp, 6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val keyword = viewModel.searchKeyword.trim()

                            viewModel.monsterGroups.forEach { (title, monsters) ->
                                val filtered = if (keyword.isEmpty()) {
                                    monsters
                                } else {
                                    monsters.filter { it.name.contains(keyword) }
                                }

                                if (filtered.isEmpty()) {
                                    return@forEach
                                }

                                item(key = "group_$title") {
                                    PrimaryText(
                                        text = title,
                                        textSize = 14.sp,
                                        modifier = Modifier.padding(4.dp, 6.dp)
                                    )
                                }

                                items(filtered, key = { "monster_${it.id}" }) { monster ->
                                    Column(
                                        modifier = Modifier
                                            .radius(6.dp)
                                            .background(CardBackGroundColor)
                                            .fillMaxWidth()
                                            .clickable {
                                                viewModel.showMonsterDetail(monster)
                                            }
                                            .padding(8.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            ItemIconCard(
                                                url = monster.iconUrl,
                                                star = 0,
                                                size = 36.dp,
                                                borderRadius = 6.dp
                                            )

                                            Spacer(modifier = Modifier.width(8.dp))

                                            PrimaryText(
                                                text = monster.name,
                                                textSize = 14.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                viewModel.currentMonster?.let { monster ->
                    MonsterDetailDialog(monster)
                }
            }
        }
    }

    @Composable
    private fun MonsterDetailDialog(monster: com.lianyi.paimonsnotebook.common.web.hutao.genshin.monster.MonsterData) {
        Dialog(onDismissRequest = viewModel::dismissMonsterDetail) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .radius(8.dp)
                    .background(White)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NetworkImage(
                        url = monster.iconUrl,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = monster.name,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        Text(
                            text = monster.title,
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                }

                InfoRow("基础生命", formatBaseValue(monster.baseValue.HpBase))
                InfoRow("基础攻击", formatBaseValue(monster.baseValue.AttackBase))
                InfoRow("基础防御", formatBaseValue(monster.baseValue.DefenseBase.toFloat()))

                Text(text = "抗性", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    InfoRow("物理", formatResist(monster.baseValue.PhysicalSubHurt))
                    InfoRow("火", formatResist(monster.baseValue.FireSubHurt))
                    InfoRow("雷", formatResist(monster.baseValue.ElecSubHurt))
                    InfoRow("水", formatResist(monster.baseValue.WaterSubHurt))
                    InfoRow("草", formatResist(monster.baseValue.GrassSubHurt))
                    InfoRow("风", formatResist(monster.baseValue.WindSubHurt))
                    InfoRow("冰", formatResist(monster.baseValue.IceSubHurt))
                    InfoRow("岩", formatResist(monster.baseValue.RockSubHurt))
                }

                if (monster.drops.isNotEmpty()) {
                    Text(text = "掉落", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        monster.drops.take(8).forEach { materialId ->
                            val material = viewModel.getMaterialById(materialId)

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.width(56.dp)
                            ) {
                                NetworkImage(
                                    url = material.iconUrl,
                                    modifier = Modifier.size(40.dp)
                                )

                                Text(
                                    text = material.Name,
                                    fontSize = 10.sp,
                                    color = Color.Gray,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                if (monster.description.isNotBlank()) {
                    Text(text = "描述", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    InfoText(text = monster.description)
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }

    @Composable
    private fun InfoRow(label: String, value: String) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = label,
                fontSize = 13.sp,
                color = Color.Gray,
                modifier = Modifier.weight(1f)
            )

            Text(text = value, fontSize = 13.sp)
        }
    }

    private fun formatBaseValue(value: Float): String =
        if (value % 1f == 0f) "${value.toInt()}" else String.format("%.1f", value)

    private fun formatResist(value: Float): String =
        String.format("%.1f%%", value * 100)
}
