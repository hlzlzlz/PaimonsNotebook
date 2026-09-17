package com.lianyi.paimonsnotebook.common.database.util

import com.lianyi.paimonsnotebook.common.database.PaimonsNotebookDatabase
import com.lianyi.paimonsnotebook.common.database.disk_cache.entity.DiskCache

object PaimonsNoteBookDatabaseHelper {

    /*
    * 同一url在此时间窗内不重复写库(毫秒)
    * NetworkImage在LazyColumn的item里被大量使用,滚动时同一个url会被反复组合,
    * 若每次都写库会造成大量无意义的UPDATE与并发竞争。
    * */
    private const val USE_INFO_THROTTLE_MS = 60_000L

    private val lastWriteTimeMap = mutableMapOf<String, Long>()

    //保护"查询是否存在→插入/更新"这一读改写序列
    private val insertLock = Any()

    //更新图片信息
    fun updateImageUseInfo(diskCache: DiskCache) {
        val url = diskCache.url

        //节流:同一url在窗口期内只写一次
        val now = System.currentTimeMillis()

        synchronized(lastWriteTimeMap) {
            val last = lastWriteTimeMap[url] ?: 0L

            if (now - last < USE_INFO_THROTTLE_MS) {
                return
            }

            lastWriteTimeMap[url] = now

            //避免长期运行后map无限增长
            if (lastWriteTimeMap.size > 512) {
                val threshold = now - USE_INFO_THROTTLE_MS
                lastWriteTimeMap.entries.removeAll { it.value < threshold }
            }
        }

        PaimonsNotebookDatabase.database.diskCacheDao.let { dao ->
            diskCache.apply {
                //整个读改写放在同一把锁内,避免并发时重复insert或丢失计数
                synchronized(insertLock) {
                    if (dao.queryDataCountByUrl(url) > 0) {
                        dao.updateUseInfo(url, lastUseFrom, lastUseTime)
                    } else {
                        dao.insert(this)
                    }
                }
            }
        }
    }
}