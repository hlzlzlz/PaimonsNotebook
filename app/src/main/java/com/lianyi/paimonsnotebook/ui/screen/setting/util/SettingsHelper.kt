package com.lianyi.paimonsnotebook.ui.screen.setting.util

import com.lianyi.paimonsnotebook.common.extension.scope.launchSafeIO
import com.lianyi.paimonsnotebook.common.util.data_store.PreferenceKeys
import com.lianyi.paimonsnotebook.common.util.data_store.dataStoreValues
import com.lianyi.paimonsnotebook.common.util.json.JSON
import com.lianyi.paimonsnotebook.ui.screen.home.util.HomeHelper
import com.lianyi.paimonsnotebook.ui.screen.setting.data.ConfigurationData
import com.lianyi.paimonsnotebook.ui.screen.setting.util.enums.HomeScreenDisplayState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object SettingsHelper {
    private val _ConfigurationData = MutableStateFlow(ConfigurationData())
    val configurationFlow = _ConfigurationData.asStateFlow()

    init {
        //用launchSafeIO:本object在HomeScreenViewModel初始化时首次触碰,
        //裸launch里抛异常会直接杀掉进程(表现为开屏一闪就没了)
        launchSafeIO {
            dataStoreValues { preferences ->
                val configurationData = ConfigurationData().apply {
                    //valueOf遇非法持久化值会抛IllegalArgumentException,此处回退到默认值,
                    //避免历史脏数据或枚举变更导致启动崩溃
                    homeScreenDisplayState =
                        runCatching {
                            HomeScreenDisplayState.valueOf(
                                preferences[PreferenceKeys.HomeScreenDisplayState]
                                    ?: ConfigurationData.homeScreenDisplayStateDefault.name
                            )
                        }.getOrDefault(ConfigurationData.homeScreenDisplayStateDefault)
                    enableOverlay = preferences[PreferenceKeys.EnableOverlay]
                        ?: ConfigurationData.ENABLE_OVERLAY_DEFAULT
                    alwaysUseDefaultUser =
                        preferences[PreferenceKeys.AlwaysUseDefaultUser]
                            ?: ConfigurationData.ALWAYS_USE_DEFAULT_USER_DEFAULT
                    enableAutoCleanExpiredImages =
                        preferences[PreferenceKeys.EnableAutoCleanExpiredImages]
                            ?: ConfigurationData.ENABLE_AUTO_CLEAN_EXPIRED_IMAGES_DEFAULT
                    enableCheckNewVersion = preferences[PreferenceKeys.EnableCheckNewVersion]
                        ?: ConfigurationData.ENABLE_CHECK_NEW_VERSION_DEFAULT
                    enableMetadata = preferences[PreferenceKeys.EnableMetadata]
                        ?: ConfigurationData.ENABLE_METADATA_DEFAULT
                    enableAutoSignIn = preferences[PreferenceKeys.EnableAutoSignIn]
                        ?: ConfigurationData.ENABLE_AUTO_SIGN_IN_DEFAULT
                    enableAutoReSign = preferences[PreferenceKeys.EnableAutoReSign]
                        ?: ConfigurationData.ENABLE_AUTO_RESIGN_DEFAULT
                    enableDailyNoteNotify = preferences[PreferenceKeys.EnableDailyNoteNotify]
                        ?: ConfigurationData.ENABLE_DAILY_NOTE_NOTIFY_DEFAULT
                    dailyNoteNotifyInterval = preferences[PreferenceKeys.DailyNoteNotifyInterval]
                        ?: ConfigurationData.DAILY_NOTE_NOTIFY_INTERVAL_DEFAULT
                    dailyNoteResinNotifyThreshold =
                        preferences[PreferenceKeys.DailyNoteResinNotifyThreshold]
                            ?: ConfigurationData.DAILY_NOTE_RESIN_THRESHOLD_DEFAULT
                    enableDailyNoteNotifyDndGaming =
                        preferences[PreferenceKeys.DailyNoteNotifyDndGaming]
                            ?: ConfigurationData.ENABLE_DAILY_NOTE_DND_GAMING_DEFAULT
                    //Webhook 地址默认空串(即不推送)
                    dailyNoteWebhookUrl =
                        preferences[PreferenceKeys.DailyNoteWebhookUrl] ?: ""
                }

                _ConfigurationData.emit(configurationData)

                val customDrawerListJson =
                    preferences[PreferenceKeys.CustomHomeDrawerList] ?: JSON.EMPTY_LIST

                val enableCustomDrawer = preferences[PreferenceKeys.EnableCustomHomeDrawer] ?: false

                HomeHelper.updateShowModalItemData(
                    configurationData.enableMetadata,
                    customDrawerListJson,
                    enableCustomDrawer
                )
            }
        }
    }
}