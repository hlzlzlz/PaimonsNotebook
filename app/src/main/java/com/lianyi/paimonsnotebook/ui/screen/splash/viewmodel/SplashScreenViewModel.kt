package com.lianyi.paimonsnotebook.ui.screen.splash.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lianyi.paimonsnotebook.common.extension.data_store.editValue
import com.lianyi.paimonsnotebook.common.extension.scope.launchIO
import com.lianyi.paimonsnotebook.common.extension.scope.launchMain
import com.lianyi.paimonsnotebook.common.extension.scope.withContextMain
import com.lianyi.paimonsnotebook.common.extension.string.errorNotify
import com.lianyi.paimonsnotebook.common.extension.string.notify
import com.lianyi.paimonsnotebook.common.util.data_store.PreferenceKeys
import com.lianyi.paimonsnotebook.common.util.data_store.dataStoreValuesFirst
import com.lianyi.paimonsnotebook.common.util.data_store.dataStoreValuesFirstLambda
import com.lianyi.paimonsnotebook.common.web.hutao.genshin.common.util.MetadataHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SplashScreenViewModel : ViewModel() {

    var showLoading by mutableStateOf(false)
        private set

    var currentMetadataLoadCount by mutableIntStateOf(0)
        private set
    var maxMetadataCount by mutableIntStateOf(0)
        private set

    var showEnableMetadataHint by mutableStateOf(false)
        private set

    var enableMetadata = false

    var initialMetadataDownload = false

    fun initParam(
        onGoTargetScreen: () -> Unit,
        onDownload: () -> Unit
    ) {
        viewModelScope.launchIO {
            dataStoreValuesFirst {
                enableMetadata = it[PreferenceKeys.EnableMetadata] ?: true
                showEnableMetadataHint = it[PreferenceKeys.OnLaunchShowEnableMetadataHint] ?: true
                initialMetadataDownload = it[PreferenceKeys.InitialMetadataDownload] ?: false
            }

            //禁用元数据或者完成下载时,直接进入主界面
            if(!enableMetadata || initialMetadataDownload){
                onGoTargetScreen.invoke()
                return@launchIO
            }

            //当条件为false时,代表已经进行了选择
            //到此分支时代表已经开始metadata下载,继续下载
            if (!showEnableMetadataHint) {
                onDownload.invoke()
                return@launchIO
            }
        }
    }

    fun metadataDownloadInit(onSuccess: () -> Unit) {
        /*
        * 此处的逻辑是判断元数据是否进行了初始化下载，若已完成，直接进入程序
        * 进入主页后再判断是否需要更新
        * 完整性验证在使用对应元数据的功能进行
        * */
        viewModelScope.launch(Dispatchers.IO) {
            //启用元数据
            PreferenceKeys.EnableMetadata.editValue(true)
            PreferenceKeys.OnLaunchShowEnableMetadataHint.editValue(false)

            //Compose状态的写入必须在主线程
            withContextMain { showEnableMetadataHint = false }

            val initialMetadataDownload = dataStoreValuesFirstLambda {
                this[PreferenceKeys.InitialMetadataDownload] ?: false
            }

            if (initialMetadataDownload) {
                onSuccess.invoke()
                return@launch
            }

            withContextMain { showLoading = true }

            MetadataHelper.updateMetadata(updateMap = true,
                onFailed = {
                    "下载元数据时出现了错误".errorNotify()
                }, onSuccess = {
                    "元数据下载完毕".notify()

                    //下载成功后更新标识
                    PreferenceKeys.MetadataUpdateTime.editValue(System.currentTimeMillis())
                    PreferenceKeys.InitialMetadataDownload.editValue(true)

                    onSuccess.invoke()
                }, onLoadMetadataFile = {
                    /*
                    * 该回调是非suspend的(不能直接调withContextMain),
                    * 且由 MetadataHelper 内多个async并发触发 —— 原先直接
                    * currentMetadataLoadCount++ 既跨线程写Compose状态,
                    * 又是非原子的自增。改为投递到主线程执行:Main是单线程,
                    * 顺带把并发自增也串行化了。
                    * */
                    viewModelScope.launchMain {
                        currentMetadataLoadCount++
                        maxMetadataCount = it
                    }
                }) {
                withContextMain { showLoading = false }
            }
        }
    }

    fun onSkipMetadataDownload(callback: () -> Unit) {
        viewModelScope.launchIO {
            PreferenceKeys.EnableMetadata.editValue(false)
            PreferenceKeys.OnLaunchShowEnableMetadataHint.editValue(false)

            callback.invoke()
        }
    }

    fun disabledOnLaunchShowMetadataHint(onComplete: () -> Unit) {
        viewModelScope.launchIO {
            PreferenceKeys.OnLaunchShowEnableMetadataHint.editValue(false)

            onComplete.invoke()
        }
    }
}