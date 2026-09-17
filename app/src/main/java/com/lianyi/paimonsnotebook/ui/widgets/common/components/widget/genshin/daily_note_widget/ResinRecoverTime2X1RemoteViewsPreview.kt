package com.lianyi.paimonsnotebook.ui.widgets.common.components.widget.genshin.daily_note_widget

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lianyi.paimonsnotebook.R
import com.lianyi.paimonsnotebook.common.extension.modifier.radius.radius
import com.lianyi.paimonsnotebook.ui.screen.app_widget.data.RemoteViewsPreviewAnimData
import kotlin.math.roundToInt

/*
* 原粹树脂恢复时间2*1预览
*
* 该预览展示"原粹树脂/数量/恢复时间",与ResinRecoverTime2X1RemoteViews
* (布局widget_layout_resin_2_1_recover_time)的内容一致。
* 函数与文件名中的2X2是上游把该视图由2*2改名为2*1时遗留的旧名,内容从未改变。
* */
@Composable
fun ResinRecoverTime2X1RemoteViewsPreview(
    previewAnimData: RemoteViewsPreviewAnimData
) {
    Column(
        modifier = Modifier
            .radius(previewAnimData.backgroundRadius.value.roundToInt().dp)
            .background(previewAnimData.backgroundColor.value)
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(id = R.drawable.icon_resin),
                contentDescription = null,
                modifier = Modifier.size(40.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Column {
                Text(
                    text = "原粹树脂",
                    fontSize = 10.sp,
                    color = previewAnimData.textColor.value
                )
                Text(
                    text = "160/160",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp,
                    color = previewAnimData.textColor.value
                )
                Text(
                    text = "恢复时间:99小时99分钟",
                    fontSize = 10.sp,
                    color = previewAnimData.textColor.value
                )
            }
        }
    }
}