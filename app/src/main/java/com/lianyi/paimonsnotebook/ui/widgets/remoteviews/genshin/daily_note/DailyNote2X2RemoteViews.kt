package com.lianyi.paimonsnotebook.ui.widgets.remoteviews.genshin.daily_note

import com.lianyi.paimonsnotebook.common.database.app_widget_binding.entity.AppWidgetBinding
import com.lianyi.paimonsnotebook.ui.widgets.widget.AppWidgetCommon2X2

/*
* 实时便笺2*2远端视图
*
* 复用2*1的布局与内容,仅把targetCls换为2*2组件。
* 上游1.4.2把原本挂在2*2上的视图改名为2*1,导致2*2组件至今没有任何视图可配
* (RemoteViewsIndexes以视图类名为key,一个视图类只能挂一个尺寸,故必须派生子类)
* */
class DailyNote2X2RemoteViews(
    appWidgetBinding: AppWidgetBinding
) : DailyNote2X1RemoteViews(appWidgetBinding, AppWidgetCommon2X2::class.java)
