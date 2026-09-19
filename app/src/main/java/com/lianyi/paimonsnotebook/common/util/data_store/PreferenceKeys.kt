package com.lianyi.paimonsnotebook.common.util.data_store

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

object PreferenceKeys {
    //device_fp
    val DeviceFp by lazy {
        stringPreferencesKey("device_fp")
    }

    //getFp接口专用device_id(16位十六进制)
    val FpDeviceId by lazy {
        stringPreferencesKey("fp_device_id")
    }

    //指纹最近一次成功签发时间,7天内启动跳过getFp(胡桃同款静默续期策略)
    val DeviceFpUpdateTime by lazy {
        longPreferencesKey("device_fp_update_time")
    }

    //圣遗物评分手动权重模式
    val EnableReliquaryScoreCustomWeight by lazy {
        booleanPreferencesKey("enableReliquaryScoreCustomWeight")
    }
    //圣遗物评分手动权重JSON
    val ReliquaryScoreWeightJson by lazy {
        stringPreferencesKey("reliquaryScoreWeight")
    }

    //device_id
    val DeviceId by lazy {
        stringPreferencesKey("device_id")
    }

    //device_id
    val DeviceId40 by lazy {
        stringPreferencesKey("device_id40")
    }

    //bbs_device_id
    val BBSDeviceId by lazy {
        stringPreferencesKey("bbs_device_id")
    }

    val DeviceIdSeed by lazy {
        stringPreferencesKey("device_id_seed")
    }

    val DeviceIdSeedTime by lazy {
        longPreferencesKey("device_id_seed_time")
    }

    //外部存储权限申请标识
    val PermissionRequestFlag by lazy {
        booleanPreferencesKey("permissionRequestFlag")
    }

    //启用选择主页modal自动收回
    val EnableHomeModalSelectClose by lazy {
        booleanPreferencesKey("enableHomeModalSelectClose")
    }

    val EnableOverlay by lazy {
        booleanPreferencesKey("enableOverlay")
    }

    //主页显示状态
    val HomeScreenDisplayState by lazy {
        stringPreferencesKey("homeScreenDisplayState")
    }

    //桌面组件是否以当前选择的用户为绑定目标
    val AppwidgetAlwaysUseSelectedUser by lazy {
        booleanPreferencesKey("appwidgetAlwaysUseSelectedUser")
    }

    //当选择用户的时候,默认使用选中用户
    val AlwaysUseDefaultUser by lazy {
        booleanPreferencesKey("alwaysUseDefaultUser")
    }

    //当前显示的祈愿记录uid
    val GachaRecordCurrentGameUid by lazy {
        stringPreferencesKey("gachaRecordCurrentGameUid")
    }

    //祈愿记录时区map,存储时区
    val GachaRecordGameUidRegionMap by lazy {
        stringPreferencesKey("gachaRecordGameUidRegionMap")
    }

    /*
    * 祈愿导出使用的 UIGF 版本
    *
    * 值为 "v3.0"/"v4.0"/"v4.1"/"v4.2"。
    *
    * 注:此处沿用旧的键名 gachaRecordExportToUIGFV3 但**类型由 Boolean 改为 String**。
    * DataStore 的键由"名字+类型"共同决定身份,类型变了就等同于换了一个键,
    * 旧 Boolean 值会被忽略、走默认值(v4.0),不会崩 —— 旧值本来也只是
    * "是否导出 v3",无法表达 v4.1/v4.2,没有迁移价值。
    * 读取处统一经 UIGFExportVersion.fromValue() 做兜底,脏数据不会导致导出出错。
    * */
    val GachaRecordExportToUIGFVersion by lazy {
        stringPreferencesKey("gachaRecordExportToUIGFVersion")
    }

    //旧键,保留声明以免历史代码引用时报错;新代码请用 GachaRecordExportToUIGFVersion
    @Deprecated("已被 GachaRecordExportToUIGFVersion 取代")
    val GachaRecordExportToUIGFV3 by lazy {
        booleanPreferencesKey("gachaRecordExportToUIGFV3")
    }

    //当前祈愿记录最后的uid,用于生成无ID的记录
    val GenerateGachaRecordId by lazy {
        longPreferencesKey("generateGachaRecordId")
    }

    //桌面组件快捷方式组件当前页面
    val AppWidgetShortcutCurrentPage by lazy {
        intPreferencesKey("appWidgetShortcutCurrentPage")
    }

    //最后一次查看的角色的id
    val LastViewAvatarId by lazy {
        intPreferencesKey("lastViewAvatarId")
    }

    //最后查看的武器的id
    val LastViewWeaponId by lazy {
        intPreferencesKey("lastViewWeaponId")
    }

    //第一次进入桌面组件配置界面
    val FirstEntryAppWidgetConfigurationScreen by lazy {
        booleanPreferencesKey("firstEntryAppWidgetConfigurationScreen")
    }

    //是否同意用户协议
    val AgreeUserAgreement by lazy {
        booleanPreferencesKey("agreeUserAgreement")
    }

    //是否启用自动清除过期图像
    val EnableAutoCleanExpiredImages by lazy {
        booleanPreferencesKey("enableAutoCleanExpiredImages")
    }

    //检查新版本
    val EnableCheckNewVersion by lazy {
        booleanPreferencesKey("enableCheckNewVersion")
    }

    //全屏模式(隐藏状态栏)
    val FullScreenMode by lazy {
        booleanPreferencesKey("fullScreenMode")
    }

    //桌面组件快捷方式当前页面
    val AppWidgetDailyMaterialCurrentPage by lazy {
        intPreferencesKey("appWidgetDailyMaterialCurrentPage")
    }

    //元数据更新时间
    val MetadataUpdateTime by lazy {
        longPreferencesKey("metadataUpdateTime")
    }

    //staticResources渠道
    //自动签到
    val EnableAutoSignIn by lazy {
        booleanPreferencesKey("enableAutoSignIn")
    }
    val AutoSignInLastCompletedMap by lazy {
        stringPreferencesKey("autoSignInLastCompletedMap")
    }

    //便笺提醒
    val EnableDailyNoteNotify by lazy {
        booleanPreferencesKey("enableDailyNoteNotify")
    }

    //自动签到补签
    val EnableAutoReSign by lazy {
        booleanPreferencesKey("enableAutoReSign")
    }
    //提醒检查间隔(分钟)
    val DailyNoteNotifyInterval by lazy {
        intPreferencesKey("dailyNoteNotifyInterval")
    }
    //已提醒条件抑制表 JSON Map<"$uid:$condition", true>
    val DailyNoteNotifySuppressed by lazy {
        stringPreferencesKey("dailyNoteNotifySuppressed")
    }

    //树脂提醒阈值
    val DailyNoteResinNotifyThreshold by lazy {
        intPreferencesKey("dailyNoteResinNotifyThreshold")
    }

    //原神运行时免打扰
    val DailyNoteNotifyDndGaming by lazy {
        booleanPreferencesKey("dailyNoteNotifyDndGaming")
    }

    /*
    * 便笺 Webhook 推送地址
    *
    * 开启后每轮便笺检查会把该 uid 的便笺数据以 JSON POST 到该地址
    * (与胡桃工具箱的 DailyNoteWebhookOperation 行为一致)。
    * 留空表示不推送。
    * */
    val DailyNoteWebhookUrl by lazy {
        stringPreferencesKey("dailyNoteWebhookUrl")
    }

    val StaticResourcesChannel by lazy {
        stringPreferencesKey("staticResourcesChannel")
    }

    //启用成就更新时刷新列表
    val EnableAchievementChangeUpdateList by lazy {
        booleanPreferencesKey("enableAchievementChangeUpdateList")
    }

    //禁用成就记录交互
    val DisableAchievementInteraction by lazy {
        booleanPreferencesKey("disableAchievementInteraction")
    }

    //配置的快捷方式列表
    val ShortcutsList by lazy {
        stringPreferencesKey("shortcutsList")
    }

    //启用快捷方式列表
    val EnableShortcutsList by lazy {
        booleanPreferencesKey("enableShortcutsList")
    }

    //本地存储的app_key
    val AuthorizeAppSign by lazy {
        stringPreferencesKey("authorizeAppSign")
    }

    //启用元数据
    val EnableMetadata by lazy {
        booleanPreferencesKey("enableMetadata")
    }

    //是否显示元数据的提示
    val OnLaunchShowEnableMetadataHint by lazy {
        booleanPreferencesKey("onLaunchShowEnableMetadataHint")
    }

    //根据养成计划类型进行排序
    val CultivateProjectSortByEntityType by lazy {
        booleanPreferencesKey("cultivateProjectSortByEntityType")
    }

    //是否完成了元数据的初始化下载
    val InitialMetadataDownload by lazy {
        booleanPreferencesKey("initialMetadataDownload")
    }

    val CheckMetadataNewVersion by lazy {
        booleanPreferencesKey("checkMetadataNewVersion")
    }

    //是否启用自定义主页侧边栏
    val EnableCustomHomeDrawer by lazy {
        booleanPreferencesKey("enableCustomHomeDrawer")
    }


    val CustomHomeDrawerList by lazy {
        stringPreferencesKey("customHomeDrawerList")
    }


    val widgetsCount by lazy {
        intPreferencesKey("widgetsCount")
    }
}