package com.lianyi.paimonsnotebook.ui.widgets.remoteviews.genshin.daily_note

import android.content.Intent
import android.widget.RemoteViews
import com.lianyi.paimonsnotebook.R
import com.lianyi.paimonsnotebook.common.database.app_widget_binding.entity.AppWidgetBinding
import com.lianyi.paimonsnotebook.common.util.time.TimeHelper
import com.lianyi.paimonsnotebook.common.web.hoyolab.takumi.game_record.daily_note.DailyNoteData
import com.lianyi.paimonsnotebook.ui.widgets.core.BaseAppWidget
import com.lianyi.paimonsnotebook.ui.widgets.core.BaseRemoteViews
import com.lianyi.paimonsnotebook.ui.widgets.util.RemoteViewsContentHelper
import com.lianyi.paimonsnotebook.ui.widgets.widget.AppWidgetCommon3X2

/*
* 实时便笺3*2远端视图
*
* targetCls可由子类覆写:4*3组件复用同一套布局与内容,只换绑定的标准组件
* */
open class DailyNoteOverview3X2RemoteViews(
    protected val appWidgetBinding: AppWidgetBinding,
    targetCls: Class<out BaseAppWidget>
) : BaseRemoteViews(
    appWidgetBinding.appWidgetId,
    targetCls,
    R.layout.widget_layout_daily_note_overview_3_2,
    161f
) {
    //RemoteViewsIndexes通过getConstructor(AppWidgetBinding::class.java)反射实例化,
    //带默认值的主构造器不会生成单参重载,必须显式声明
    constructor(appWidgetBinding: AppWidgetBinding) :
            this(appWidgetBinding, AppWidgetCommon3X2::class.java)
    override suspend fun setDailyNote(dailyNoteData: DailyNoteData) {
        setTextViewText(
            R.id.nick_name,
            appWidgetBinding.configuration.bindingGameRole?.nickname ?: ""
        )

        setTextViewText(
            R.id.resin_text,
            "${dailyNoteData.current_resin}"
        )

        val second = dailyNoteData.resin_recovery_time.toLongOrNull() ?: 0L

        setTextViewText(
            R.id.recover_time,
            "${TimeHelper.getRecoverTime(second)}\n${TimeHelper.getDiffDayText(second)}"
        )

        setTextViewText(
            R.id.home_coin_text,
            "${dailyNoteData.current_home_coin}/${dailyNoteData.max_home_coin}"
        )

        setTextViewText(
            R.id.daily_task_text,
            RemoteViewsContentHelper.getDailyTaskContentByState(dailyNoteData)
        )

        setTextViewText(
            R.id.quality_convert_text,
            dailyNoteData.transformer.getRecoveryTimeText()
        )
    }

    override suspend fun onUpdateContent(intent: Intent?): RemoteViews? {

        setCommonStyle(
            appWidgetBinding.configuration, textIds = intArrayOf(
                R.id.resin_text,
                R.id.recover_time,
                R.id.home_coin_text,
                R.id.daily_task_text,
                R.id.expedition_text,
                R.id.quality_convert_text,
            )
        )

        return super.onUpdateContent(intent)
    }

}