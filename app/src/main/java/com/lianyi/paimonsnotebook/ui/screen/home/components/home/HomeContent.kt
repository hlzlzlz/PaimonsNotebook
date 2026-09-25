package com.lianyi.paimonsnotebook.ui.screen.home.components.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.lianyi.paimonsnotebook.R
import com.lianyi.paimonsnotebook.common.components.lazy.ContentSpacerLazyColumn
import com.lianyi.paimonsnotebook.common.components.media.LazyBanner
import com.lianyi.paimonsnotebook.common.components.spacer.StatusBarPaddingSpacer
import com.lianyi.core.ui.components.text.PrimaryText
import com.lianyi.paimonsnotebook.common.database.disk_cache.entity.DiskCache
import com.lianyi.paimonsnotebook.common.database.disk_cache.util.DiskCacheDataType
import com.lianyi.paimonsnotebook.common.web.hoyolab.bbs.NearActivityData
import com.lianyi.paimonsnotebook.common.web.hoyolab.bbs.OfficialRecommendedPostsData
import com.lianyi.paimonsnotebook.common.web.hoyolab.bbs.WebHomeData
import com.lianyi.paimonsnotebook.common.web.hoyolab.takumi.game_record.act_calendar.ActCalendarData
import com.lianyi.paimonsnotebook.common.web.hoyolab.takumi.event.miyolive.MiyoliveCodeData
import com.lianyi.paimonsnotebook.common.web.hoyolab.takumi.game_record.ledger.LedgerData
import com.lianyi.paimonsnotebook.ui.screen.home.components.card.card_pool.CardPoolCard
import com.lianyi.paimonsnotebook.ui.screen.home.components.CollapsibleSection
import com.lianyi.paimonsnotebook.ui.screen.home.components.card.activity.HomeActivityList
import com.lianyi.paimonsnotebook.ui.screen.home.components.card.miyolive.MiyoliveCodeCard
import com.lianyi.paimonsnotebook.ui.screen.home.components.card.travelers_diary.TravelersDiaryCard
import com.lianyi.paimonsnotebook.ui.screen.travelers_diary.view.TravelersDiaryScreen
import com.lianyi.paimonsnotebook.ui.screen.home.util.HomeHelper
import com.lianyi.paimonsnotebook.ui.screen.home.components.BannerItem
import com.lianyi.paimonsnotebook.ui.screen.home.components.notice.HomeEventNotice
import com.lianyi.paimonsnotebook.ui.screen.home.util.PostType
import com.lianyi.paimonsnotebook.ui.theme.White

/*
* 活动区块的副标题(收起状态下也能看出有没有内容)
* */
private fun activitySubtitle(nearCount: Int, calendarCount: Int): String {
    val parts = buildList {
        if (nearCount > 0) add("近期 $nearCount")
        if (calendarCount > 0) add("日历 $calendarCount")
    }

    return parts.joinToString(" · ")
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun HomeContent(
    bannerList: List<WebHomeData.Carousel>,
    nearActivity: List<NearActivityData.Hots.Group2.Children.NearActivity>,
    noticeList: List<OfficialRecommendedPostsData.OfficialRecommendedPost>,
    travelersDiaryData: LedgerData?,
    cardPools: List<ActCalendarData.CardPool>,
    calendarActs: List<ActCalendarData.Act>,
    miyoliveCodes: List<MiyoliveCodeData.CodeWrapper>,
    goPostDetail: (String, PostType) -> Unit,
) {
    Box {
        //顶部背景
        Image(
            painter = painterResource(id = R.drawable.bg_home_background),
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp),
            contentScale = ContentScale.Crop
        )

        ContentSpacerLazyColumn(Modifier.fillMaxSize()) {
            item {
                StatusBarPaddingSpacer()
            }
            //轮播图
            item {
                LazyBanner(
                    bannerData = bannerList,
                    contentPaddingValues = PaddingValues(30.dp, 0.dp),
                    modifier = Modifier
                        .height(180.dp),
                    enableAutoLoop = true,
                    itemSpacing = (-30).dp
                ) { item, index, pageCurrent ->
                    BannerItem(
                        index = index,
                        pageCurrent = pageCurrent,
                        item = item,
                        diskCache = DiskCache(
                            url = item.cover,
                            name = "轮播图",
                            createFrom = "首页",
                            description = "首页顶部轮播图",
                            type = DiskCacheDataType.Temp,
                            lastUseFrom = "首页"
                        )
                    ) {
                        goPostDetail(it.path, PostType.Banner)
                    }
                }
            }


            //旅行者札记摘要
            item {
                TravelersDiaryCard(
                    ledgerData = travelersDiaryData,
                    onClick = {
                        HomeHelper.goActivity(TravelersDiaryScreen::class.java)
                    }
                )
            }

            //当期卡池(可折叠)
            if (cardPools.isNotEmpty()) {
                item {
                    CollapsibleSection(
                        title = "当期卡池",
                        subtitle = "${cardPools.size} 个卡池"
                    ) {
                        CardPoolCard(pools = cardPools)
                    }
                }
            }

            /*
            * 活动(合并原「活动日历」与「近期活动」)
            *
            * 两者都是"当前有什么活动",并列展示既重复又占地方。
            * 数据层不合并(见 HomeActivityList 的注释):
            * 近期活动在前(有图可点),活动日历在后(无图,用状态色块占位)。
            * */
            if (nearActivity.isNotEmpty() || calendarActs.isNotEmpty()) {
                item {
                    CollapsibleSection(
                        title = "活动",
                        subtitle = activitySubtitle(nearActivity.size, calendarActs.size)
                    ) {
                        HomeActivityList(
                            nearActivities = nearActivity,
                            calendarActs = calendarActs,
                            onClickNearActivity = { url ->
                                goPostDetail(url, PostType.Notice)
                            }
                        )
                    }
                }
            }

            //前瞻直播兑换码
            item {
                MiyoliveCodeCard(codes = miyoliveCodes)
            }

            //公告列表
            item {
                com.lianyi.core.ui.components.text.PrimaryText(
                    text = "公告",
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(White)
                        .padding(8.dp)
                )
            }

            items(noticeList) {
                HomeEventNotice(
                    item = it,
                    modifier = Modifier
                        .background(White)
                        .padding(8.dp, 4.dp),
                    diskCache = DiskCache(
                        url = it.banner,
                        name = "公告列表图片",
                        createFrom = "首页",
                        description = it.subject,
                        type = DiskCacheDataType.Temp,
                        lastUseFrom = "首页"
                    )
                ) { postId ->
                    goPostDetail(postId, PostType.Notice)
                }
            }
        }

    }
}