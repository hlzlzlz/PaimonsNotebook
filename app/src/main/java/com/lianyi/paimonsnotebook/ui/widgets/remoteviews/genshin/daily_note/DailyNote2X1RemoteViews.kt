package com.lianyi.paimonsnotebook.ui.widgets.remoteviews.genshin.daily_note

import android.content.Intent
import android.widget.RemoteViews
import com.lianyi.paimonsnotebook.R
import com.lianyi.paimonsnotebook.common.database.app_widget_binding.entity.AppWidgetBinding
import com.lianyi.paimonsnotebook.common.web.hoyolab.takumi.game_record.daily_note.DailyNoteData
import com.lianyi.paimonsnotebook.ui.widgets.core.BaseAppWidget
import com.lianyi.paimonsnotebook.ui.widgets.core.BaseRemoteViews
import com.lianyi.paimonsnotebook.ui.widgets.util.RemoteViewsContentHelper
import com.lianyi.paimonsnotebook.ui.widgets.widget.AppWidgetCommon2X1

/*
* 实时便笺2*1远端视图
*
* targetCls可由子类覆写:2*2组件复用同一套布局与内容,只换绑定的标准组件
* (布局高105dp与2*2组件的最小高110dp基本吻合,无需另建布局)
* */
open class DailyNote2X1RemoteViews(
    protected val appWidgetBinding: AppWidgetBinding,
    targetCls: Class<out BaseAppWidget>
) : BaseRemoteViews(
    appWidgetBinding.appWidgetId,
    targetCls,
    R.layout.widget_layout_daily_note_2_1
) {
    //RemoteViewsIndexes通过getConstructor(AppWidgetBinding::class.java)反射实例化,
    //带默认值的主构造器不会生成单参重载,必须显式声明
    constructor(appWidgetBinding: AppWidgetBinding) :
            this(appWidgetBinding, AppWidgetCommon2X1::class.java)

    init {
        setOnClickPendingIntent(R.id.container, updatePendingIntent)
    }

    override suspend fun setDailyNote(dailyNoteData: DailyNoteData) {
        setTextViewText(
            R.id.resin_text,
            "${dailyNoteData.current_resin}/${dailyNoteData.max_resin}"
        )

        setTextViewText(
            R.id.daily_task_text,
            RemoteViewsContentHelper.getDailyTaskContentByState(dailyNoteData)
        )
        setTextViewText(
            R.id.home_coin_text,
            "${dailyNoteData.current_home_coin}/${dailyNoteData.max_home_coin}"
        )
        setTextViewText(
            R.id.secret_tower_text,
            "${dailyNoteData.remain_resin_discount_num}/${dailyNoteData.resin_discount_num_limit}"
        )
    }

    override suspend fun onUpdateContent(intent: Intent?): RemoteViews? {
        setCommonStyle(
            configuration = appWidgetBinding.configuration,
            textIds = intArrayOf(
                R.id.resin_text,
                R.id.daily_task_text,
                R.id.home_coin_text,
                R.id.secret_tower_text,
            )
        )

        return super.onUpdateContent(intent)
    }

}