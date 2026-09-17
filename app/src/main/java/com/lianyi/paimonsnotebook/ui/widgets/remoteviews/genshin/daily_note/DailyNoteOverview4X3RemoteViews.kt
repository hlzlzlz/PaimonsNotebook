package com.lianyi.paimonsnotebook.ui.widgets.remoteviews.genshin.daily_note

import com.lianyi.paimonsnotebook.common.database.app_widget_binding.entity.AppWidgetBinding
import com.lianyi.paimonsnotebook.ui.widgets.widget.AppWidgetCommon4X3

/*
* 实时便笺4*3远端视图
*
* 复用3*2的布局与内容,仅把targetCls换为4*3组件。
* 4*3组件自上游至今没有任何视图登记,配置界面必然空白。
*
* 已知取舍:父视图把dpHeight固定为161f(与布局widget_layout_daily_note_overview_3_2
* 的固定高161dp一致,背景正好贴合布局),而4*3组件最小高180dp,
* 因此组件内会有约19dp的空白区域(背景不会延伸过去)。
* 这只是观感问题,不影响数据与点击;要完全贴合需另做一份4*3专用布局,
* 因无真机确认视觉效果,暂不新增。详见memory/known-issues.md。
* */
class DailyNoteOverview4X3RemoteViews(
    appWidgetBinding: AppWidgetBinding
) : DailyNoteOverview3X2RemoteViews(appWidgetBinding, AppWidgetCommon4X3::class.java)
