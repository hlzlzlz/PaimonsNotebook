package com.lianyi.paimonsnotebook.ui.widgets.remoteviews.genshin.daily_note

import com.lianyi.paimonsnotebook.common.database.app_widget_binding.entity.AppWidgetBinding
import com.lianyi.paimonsnotebook.ui.widgets.widget.AppWidgetCommon4X3

/*
* 实时便笺4*3远端视图
*
* 复用3*2的布局与内容,仅把targetCls换为4*3组件。
* 4*3组件自上游至今没有任何视图登记,配置界面必然空白。
* 布局高161dp小于组件最小高180dp,组件会多出约19dp的留白,
* 背景由setCommonStyle按组件实际高度绘制,不会露出未绘制的区域。
* */
class DailyNoteOverview4X3RemoteViews(
    appWidgetBinding: AppWidgetBinding
) : DailyNoteOverview3X2RemoteViews(appWidgetBinding, AppWidgetCommon4X3::class.java)
