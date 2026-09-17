package com.lianyi.paimonsnotebook.ui.widgets.util

import androidx.compose.runtime.Composable
import com.lianyi.paimonsnotebook.ui.screen.app_widget.data.RemoteViewsPreviewAnimData
import com.lianyi.paimonsnotebook.ui.widgets.common.components.widget.genshin.daily_note.DailyNote2X1RemoteViewsPreview
import com.lianyi.paimonsnotebook.ui.widgets.common.components.widget.genshin.daily_note.DailyNoteOverview3X2RemoteViewsPreview
import com.lianyi.paimonsnotebook.ui.widgets.common.components.widget.genshin.daily_note.Expedition3X1RemoteViewsPreview
import com.lianyi.paimonsnotebook.ui.widgets.common.components.widget.genshin.daily_note_widget.DailyNoteWidget2X1RemoteViewsPreview
import com.lianyi.paimonsnotebook.ui.widgets.common.components.widget.genshin.daily_note_widget.HomeCoinRingProgressBar1X1RemoteViewsPreview
import com.lianyi.paimonsnotebook.ui.widgets.common.components.widget.genshin.daily_note_widget.RemoteViews2X1Preview
import com.lianyi.paimonsnotebook.ui.widgets.common.components.widget.genshin.daily_note_widget.ResinProgressBarRecoverTime3X2RemoteViewsPreview
import com.lianyi.paimonsnotebook.ui.widgets.common.components.widget.genshin.daily_note_widget.ResinRecoverTime2X1RemoteViewsPreview
import com.lianyi.paimonsnotebook.ui.widgets.common.components.widget.genshin.daily_note_widget.ResinRingProgressBar1X1RemoteViewsPreview
import com.lianyi.paimonsnotebook.ui.widgets.common.components.widget.genshin.util.DailyMaterial3X2Preview
import com.lianyi.paimonsnotebook.ui.widgets.common.components.widget.state.ErrorRemoteViewsPreview
import com.lianyi.paimonsnotebook.ui.widgets.common.components.widget.util.Shortcut3X2Preview
import com.lianyi.paimonsnotebook.ui.widgets.remoteviews.genshin.daily_note.DailyNote2X1RemoteViews
import com.lianyi.paimonsnotebook.ui.widgets.remoteviews.genshin.daily_note.DailyNote2X2RemoteViews
import com.lianyi.paimonsnotebook.ui.widgets.remoteviews.genshin.daily_note.DailyNoteOverview3X2RemoteViews
import com.lianyi.paimonsnotebook.ui.widgets.remoteviews.genshin.daily_note.DailyNoteOverview4X3RemoteViews
import com.lianyi.paimonsnotebook.ui.widgets.remoteviews.genshin.daily_note.Expedition3X1RemoteViews
import com.lianyi.paimonsnotebook.ui.widgets.remoteviews.genshin.daily_note_widget.DailyNoteWidget2X1RemoteViews
import com.lianyi.paimonsnotebook.ui.widgets.remoteviews.genshin.daily_note_widget.HomeCoinRingProgressBar1X1RemoteViews
import com.lianyi.paimonsnotebook.ui.widgets.remoteviews.genshin.daily_note_widget.Resin2X1RemoteViews
import com.lianyi.paimonsnotebook.ui.widgets.remoteviews.genshin.daily_note_widget.ResinProgressBarRecoverTime3X2RemoteViews
import com.lianyi.paimonsnotebook.ui.widgets.remoteviews.genshin.daily_note_widget.ResinRecoverTime2X1RemoteViews
import com.lianyi.paimonsnotebook.ui.widgets.remoteviews.genshin.daily_note_widget.ResinRingProgressBar1X1RemoteViews
import com.lianyi.paimonsnotebook.ui.widgets.remoteviews.genshin.util.DailyMaterial3X2RemoteViews
import com.lianyi.paimonsnotebook.ui.widgets.remoteviews.util.Shortcut3X2RemoteViews

/*
* 远端视图预览
* 用于配置远端视图在compose中的显示内容
* */
object RemoteViewsPreviewHelper {

    //远端视图在compose中的预览
    private val remoteViewsComposePreviewMap by lazy {
        mapOf<String, @Composable (RemoteViewsPreviewAnimData) -> Unit>(
            Resin2X1RemoteViews::class.java.name to { RemoteViews2X1Preview(it) },
            Expedition3X1RemoteViews::class.java.name to { Expedition3X1RemoteViewsPreview(it) },
            //该视图内容为"原粹树脂/数量/恢复时间",对应ResinRecoverTime2X1预览
            //(该预览原名为RemoteViews2X2Preview,是上游改名后遗留的旧名)
            ResinRecoverTime2X1RemoteViews::class.java.name to { ResinRecoverTime2X1RemoteViewsPreview(it) },
            ResinRingProgressBar1X1RemoteViews::class.java.name to { ResinRingProgressBar1X1RemoteViewsPreview(it) },
            ResinProgressBarRecoverTime3X2RemoteViews::class.java.name to { ResinProgressBarRecoverTime3X2RemoteViewsPreview(it) },
            Shortcut3X2RemoteViews::class.java.name to { Shortcut3X2Preview(it) },
            DailyNote2X1RemoteViews::class.java.name to { DailyNote2X1RemoteViewsPreview(it) },
            DailyNoteWidget2X1RemoteViews::class.java.name to { DailyNoteWidget2X1RemoteViewsPreview(it) },
            HomeCoinRingProgressBar1X1RemoteViews::class.java.name to { HomeCoinRingProgressBar1X1RemoteViewsPreview(it) },
            DailyMaterial3X2RemoteViews::class.java.name to { DailyMaterial3X2Preview(it) },
            DailyNoteOverview3X2RemoteViews::class.java.name to { DailyNoteOverview3X2RemoteViewsPreview(it)},
            //复用相邻尺寸的预览:两者布局与内容完全相同,仅绑定的标准组件不同
            DailyNote2X2RemoteViews::class.java.name to { DailyNote2X1RemoteViewsPreview(it) },
            DailyNoteOverview4X3RemoteViews::class.java.name to { DailyNoteOverview3X2RemoteViewsPreview(it) }
        )
    }

    fun getPreviewByRemoteViewsClassName(clsName: String) =
        remoteViewsComposePreviewMap[clsName] ?: { ErrorRemoteViewsPreview() }
}