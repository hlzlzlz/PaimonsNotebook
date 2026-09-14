package com.lianyi.paimonsnotebook.ui.screen.items.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lianyi.paimonsnotebook.common.util.enums.LoadingState
import com.lianyi.paimonsnotebook.common.web.hutao.genshin.common.service.MaterialService
import com.lianyi.paimonsnotebook.common.web.hutao.genshin.common.service.MonsterService
import com.lianyi.paimonsnotebook.common.web.hutao.genshin.item.Material
import com.lianyi.paimonsnotebook.common.web.hutao.genshin.monster.MonsterData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/*
* 怪物资料页ViewModel
* 同名变种按名称去重(保留id最小的一条),按title分类展示
* */
class MonsterScreenViewModel : ViewModel() {

    var loadingState by mutableStateOf(LoadingState.Loading)
        private set

    //title -> 怪物列表
    var monsterGroups by mutableStateOf<List<Pair<String, List<MonsterData>>>>(listOf())
        private set

    var searchKeyword by mutableStateOf("")

    //详情弹窗当前展示的怪物
    var currentMonster by mutableStateOf<MonsterData?>(null)

    private val monsterService by lazy {
        MonsterService {
            loadingState = LoadingState.Error
        }
    }

    private val materialService by lazy {
        MaterialService {
        }
    }

    init {
        viewModelScope.launch {
            //文件与JSON解析在IO线程,状态写回主线程
            val groups = withContext(Dispatchers.IO) {
                monsterService.monsterList
                    //同名变种折叠,保留id最小的一条
                    .associateBy { it.name }
                    .values
                    .groupBy { it.title }
                    .map { (title, list) -> title to list.sortedBy { it.id } }
                    .sortedBy { it.first }
            }

            monsterGroups = groups
            loadingState = if (groups.isEmpty()) LoadingState.Empty else LoadingState.Success
        }
    }

    fun getMaterialById(id: Int): Material = materialService.getMaterialById(id)

    fun showMonsterDetail(monster: MonsterData) {
        currentMonster = monster
    }

    fun dismissMonsterDetail() {
        currentMonster = null
    }
}
