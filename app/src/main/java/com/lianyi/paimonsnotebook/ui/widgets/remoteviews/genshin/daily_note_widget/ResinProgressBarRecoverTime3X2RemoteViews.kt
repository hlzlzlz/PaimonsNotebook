package com.lianyi.paimonsnotebook.ui.widgets.remoteviews.genshin.daily_note_widget

import android.content.Intent
import android.widget.RemoteViews
import com.lianyi.paimonsnotebook.R
import com.lianyi.paimonsnotebook.common.database.app_widget_binding.entity.AppWidgetBinding
import com.lianyi.paimonsnotebook.common.util.time.TimeHelper
import com.lianyi.paimonsnotebook.common.web.hoyolab.takumi.game_record.daily_note.DailyNoteWidgetData
import com.lianyi.paimonsnotebook.ui.widgets.core.BaseRemoteViews
import com.lianyi.paimonsnotebook.ui.widgets.widget.AppWidgetCommon3X2

/*
* 小组件树脂进度条3*2远端视图
* */
internal class ResinProgressBarRecoverTime3X2RemoteViews(
    private val appWidgetBinding: AppWidgetBinding
) : BaseRemoteViews(
    appWidgetBinding.appWidgetId,
    //本视图名为3X2、布局为widget_layout_resin_3_2,
    //registry(RemoteViewsIndexes)登记的也是AppWidgetCommon3X2,原先误写为3X1
    AppWidgetCommon3X2::class.java,
    R.layout.widget_layout_resin_3_2
) {
    init {
        setOnClickPendingIntent(R.id.container, updatePendingIntent)
    }

    override suspend fun setDailyNoteWidget(dailyNoteWidgetData: DailyNoteWidgetData) {
        setProgressBar(
            R.id.progress_bar,
            dailyNoteWidgetData.max_resin,
            dailyNoteWidgetData.current_resin,
            false
        )

        setTextViewText(
            R.id.resin,
            "${dailyNoteWidgetData.current_resin}/${dailyNoteWidgetData.max_resin}"
        )

        val text = if(dailyNoteWidgetData.current_resin == dailyNoteWidgetData.max_resin){
            "原粹树脂恢复完毕"
        }else{
            TimeHelper.getRecoverTime(dailyNoteWidgetData.resin_recovery_time.toLongOrNull() ?: 0L)
        }

        setTextViewText(
            R.id.recover_time,
            text
        )
    }

    override suspend fun onUpdateContent(intent: Intent?): RemoteViews? {
        setCommonStyle(appWidgetBinding.configuration)

        return super.onUpdateContent(intent)
    }
}