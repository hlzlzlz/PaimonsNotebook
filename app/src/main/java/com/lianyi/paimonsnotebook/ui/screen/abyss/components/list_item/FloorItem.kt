package com.lianyi.paimonsnotebook.ui.screen.abyss.components.list_item

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lianyi.paimonsnotebook.R
import com.lianyi.paimonsnotebook.common.components.widget.ExpansionIndicator
import com.lianyi.paimonsnotebook.common.extension.modifier.radius.radius
import com.lianyi.paimonsnotebook.common.util.time.TimeHelper
import com.lianyi.paimonsnotebook.common.web.hoyolab.takumi.game_record.abyss.SpiralAbyssData
import com.lianyi.paimonsnotebook.common.web.hutao.genshin.avatar.AvatarData
import com.lianyi.paimonsnotebook.common.web.hutao.genshin.monster.MonsterData
import com.lianyi.paimonsnotebook.ui.theme.Black
import com.lianyi.paimonsnotebook.ui.theme.Black_10
import com.lianyi.paimonsnotebook.ui.theme.Black_30
import com.lianyi.paimonsnotebook.ui.theme.Black_60
import com.lianyi.paimonsnotebook.ui.theme.CardBackGroundColor_Light_1

@Composable
fun FloorItem(
    floor: SpiralAbyssData.Floor,
    getAvatarFromMetadata: (Int) -> AvatarData?,
    getMonsterDataFromMetadata: (String) -> MonsterData?
) {
    //当没有战斗数据时
    if (floor.levels.isEmpty()) return

    //是否展示全部内容
    var showAll by remember {
        mutableStateOf(true)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier
                .padding(bottom = 4.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            com.lianyi.core.ui.components.text.PrimaryText(text = "第${floor.index}层")

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.icon_abyss_star),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = Black
                )

                com.lianyi.core.ui.components.text.PrimaryText(text = "${floor.star}/${floor.max_star}")

                ExpansionIndicator(expand = showAll, size = 20.dp) {
                    showAll = !showAll
                }
            }
        }
        /*
        * 地脉异常(ley_line_disorder)
        *
        * 服务端一直随 floors 下发,但此前全树仅数据类声明、零渲染 ——
        * 玩家看深渊页最需要知道的就是"这期地脉异常是什么"。
        * 放在层标题下方、展开区域之上:即使折叠也可见(它是整层的全局信息,
        * 不属于某一间),故不放进 AnimatedVisibility 内部。
        * */
        if (floor.ley_line_disorder.isNotEmpty()) {
            LeyLineDisorder(floor.ley_line_disorder)
        }

        AnimatedVisibility(visible = showAll) {
            Column {
                floor.levels.forEachIndexed { index, level ->
                    FloorLevelItem(
                        level = level,
                        getAvatarFromMetadata = getAvatarFromMetadata,
                        getMonsterDataFromMetadata = getMonsterDataFromMetadata
                    )

                    if (index != floor.levels.size - 1) {
                        Spacer(
                            modifier = Modifier
                                .padding(vertical = 4.dp)
                                .fillMaxWidth()
                                .height(.5.dp)
                                .background(Black_10)
                        )
                    }
                }
            }
        }
    }
}

/*
* 地脉异常展示
*
* 一层可能有多条(如"火元素伤害提高75%"+"超载伤害提高200%"),逐条列出。
* 用次级卡片底色与正文区分,避免与战斗记录抢视觉重心。
* */
@Composable
private fun LeyLineDisorder(disorders: List<String>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .radius(4.dp)
            .background(CardBackGroundColor_Light_1)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "地脉异常",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = Black
        )

        disorders.forEach { disorder ->
            Text(
                text = disorder,
                fontSize = 11.sp,
                color = Black_60
            )
        }
    }
}

@Composable
private fun FloorLevelItem(
    level: SpiralAbyssData.Level,
    getAvatarFromMetadata: (Int) -> AvatarData?,
    getMonsterDataFromMetadata: (String) -> MonsterData?,
) {
    var showMonster by remember {
        mutableStateOf(false)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(text = "第${level.index}间", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                //battles声明非空但服务端可能返回空数组,Gson也会把缺失字段塞成null,
                //原先直接first()会抛NoSuchElementException
                text = TimeHelper.getTime(
                    level.battles.firstOrNull()?.timestamp?.toLongOrNull() ?: 0L
                ),
                fontSize = 10.sp
            )
        }

        Row(
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(level.max_star) {
                val tint = if (level.star < it + 1) Black_30 else Black

                Icon(
                    painter = painterResource(id = R.drawable.icon_abyss_star),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = tint
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                painter = painterResource(id = R.drawable.ic_genshin_game_biology),
                contentDescription = null,
                modifier = Modifier
                    .radius(2.dp)
                    .size(20.dp)
                    .clickable {
                        showMonster = !showMonster
                    },
                tint = Black
            )
        }
    }

    val topMonsters = remember {
        level.top_half_floor_monster.map {
            getMonsterDataFromMetadata.invoke(it.name) to it.level
        }
    }
    val topAvatars = remember {
        level.battles.firstOrNull()?.avatars.orEmpty().map {
            getAvatarFromMetadata.invoke(it.id) to it.level
        }
    }

    FloorHalf(
        showMonster = showMonster,
        monsters = topMonsters,
        avatars = topAvatars,
        subText = "上半"
    )


    //当下半没有记录时
    if (level.battles.size < 2) return

    val bottomMonsters = remember {
        level.bottom_half_floor_monster.map {
            getMonsterDataFromMetadata.invoke(it.name) to it.level
        }
    }
    val bottomAvatars = remember {
        level.battles.lastOrNull()?.avatars.orEmpty().map {
            getAvatarFromMetadata.invoke(it.id) to it.level
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    FloorHalf(
        showMonster = showMonster,
        monsters = bottomMonsters,
        avatars = bottomAvatars,
        subText = "下半"
    )
}

@Composable
private fun FloorHalf(
    showMonster: Boolean,
    monsters: List<Pair<MonsterData?, Int>>,
    avatars: List<Pair<AvatarData?, Int>>,
    subText: String
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.size(244.dp, 70.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Crossfade(targetState = showMonster, label = "") {
                if (it) {
                    LazyRow(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(monsters) { pair ->
                            ItemCard(
                                url = pair.first?.iconUrl ?: "",
                                text = "Lv.${pair.second}",
                                star = 0
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        avatars.forEach { pair ->
                            ItemCard(
                                url = pair.first?.iconUrl ?: "",
                                text = "Lv.${pair.second}",
                                star = pair.first?.starCount ?: 0
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .weight(1f)
                .height(70.dp)
                .padding(start = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            com.lianyi.core.ui.components.text.PrimaryText(text = subText, textSize = 14.sp)
        }
    }
}