package com.lianyi.paimonsnotebook.common.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.lianyi.paimonsnotebook.common.application.PaimonsNotebookApplication
import com.lianyi.paimonsnotebook.common.database.abyss.dao.AbyssSeasonSnapshotDao
import com.lianyi.paimonsnotebook.common.database.abyss.entity.AbyssSeasonSnapshot
import com.lianyi.paimonsnotebook.common.database.achievement.dao.AchievementUserDao
import com.lianyi.paimonsnotebook.common.database.achievement.dao.AchievementsDao
import com.lianyi.paimonsnotebook.common.database.achievement.entity.AchievementUser
import com.lianyi.paimonsnotebook.common.database.achievement.entity.Achievements
import com.lianyi.paimonsnotebook.common.database.app_widget_binding.dao.AppWidgetBindingDao
import com.lianyi.paimonsnotebook.common.database.app_widget_binding.entity.AppWidgetBinding
import com.lianyi.paimonsnotebook.common.database.cultivate.dao.CultivateEntityDao
import com.lianyi.paimonsnotebook.common.database.cultivate.dao.CultivateItemMaterialsDao
import com.lianyi.paimonsnotebook.common.database.cultivate.dao.CultivateItemsDao
import com.lianyi.paimonsnotebook.common.database.cultivate.dao.CultivateProjectDao
import com.lianyi.paimonsnotebook.common.database.cultivate.entity.CultivateEntity
import com.lianyi.paimonsnotebook.common.database.cultivate.entity.CultivateItemMaterials
import com.lianyi.paimonsnotebook.common.database.cultivate.entity.CultivateItems
import com.lianyi.paimonsnotebook.common.database.cultivate.entity.CultivateProject
import com.lianyi.paimonsnotebook.common.database.daily_note.dao.DailyNoteDao
import com.lianyi.paimonsnotebook.common.database.daily_note.dao.DailyNoteWidgetDao
import com.lianyi.paimonsnotebook.common.database.daily_note.entity.DailyNote
import com.lianyi.paimonsnotebook.common.database.daily_note.entity.DailyNoteWidget
import com.lianyi.paimonsnotebook.common.database.disk_cache.dao.DiskCacheDao
import com.lianyi.paimonsnotebook.common.database.disk_cache.entity.DiskCache
import com.lianyi.paimonsnotebook.common.database.gacha.dao.BeyondGachaItemsDao
import com.lianyi.paimonsnotebook.common.database.gacha.dao.GachaItemsDao
import com.lianyi.paimonsnotebook.common.database.gacha.entity.BeyondGachaItems
import com.lianyi.paimonsnotebook.common.database.gacha.entity.GachaItems
import com.lianyi.paimonsnotebook.common.database.ledger.dao.LedgerMonthSnapshotDao
import com.lianyi.paimonsnotebook.common.database.ledger.entity.LedgerMonthSnapshot
import com.lianyi.paimonsnotebook.common.database.user.dao.UserDao
import com.lianyi.paimonsnotebook.common.database.user.entity.User

@Database(
    entities = [
        GachaItems::class,
        DiskCache::class,
        AppWidgetBinding::class,
        User::class,
        DailyNote::class,
        DailyNoteWidget::class,
        AchievementUser::class,
        Achievements::class,
        CultivateProject::class,
        CultivateEntity::class,
        CultivateItems::class,
        CultivateItemMaterials::class,
        BeyondGachaItems::class,
        LedgerMonthSnapshot::class,
        AbyssSeasonSnapshot::class
    ],
    autoMigrations = [
        AutoMigration(1, 2),
        AutoMigration(2, 3),
        AutoMigration(3, 4),
        /*
        * 4 -> 5:cultivate_item_materials 新增 owned_count(玩家实际持有数)。
        *
        * 该列在实体上带 defaultValue = "-1",故 AutoMigration 能自动补出
        * 这条 ALTER TABLE(老数据一律填 -1 = 未知,UI 据此隐藏该行)。
        * Room 会在编译期校验 schema,若迁移不可行会直接构建失败 ——
        * 这比运行时崩溃好得多。
        * */
        AutoMigration(4, 5),
        /*
        * 5 -> 6:新增 beyond_gacha_items 表(千星奇域/UGC 祈愿记录)。
        *
        * 这是**新建表**而非给已有表加列,故无需 defaultValue ——
        * AutoMigration 会生成 CREATE TABLE,老数据不受影响(新表为空)。
        * 与 4->5 的区别:那次是加列(必须给默认值),这次是加表。
        * */
        AutoMigration(5, 6),
        /*
        * 6 -> 7:新增 ledger_month_snapshots 表(旅行者札记月度收支快照)。
        * 同 5->6,新建表无需 defaultValue。
        * */
        AutoMigration(6, 7),
        /*
        * 7 -> 8:新增 abyss_season_snapshots 表(深境螺旋每期成绩快照)。
        * 同样只新建表,无需 defaultValue。
        * */
        AutoMigration(7, 8)
    ],
    version = 9,
    exportSchema = true
)
abstract class PaimonsNotebookDatabase : RoomDatabase() {

    //祈愿记录
    abstract val gachaItemsDao: GachaItemsDao

    //千星奇域祈愿记录
    abstract val beyondGachaItemsDao: BeyondGachaItemsDao

    //旅行者札记月度快照
    abstract val ledgerMonthSnapshotDao: LedgerMonthSnapshotDao

    //深境螺旋每期成绩快照
    abstract val abyssSeasonSnapshotDao: AbyssSeasonSnapshotDao

    //硬盘缓存
    abstract val diskCacheDao: DiskCacheDao

    //桌面组件绑定
    abstract val appWidgetBindingDao: AppWidgetBindingDao

    //用户
    abstract val userDao: UserDao

    //实时便笺
    abstract val dailyNoteDao: DailyNoteDao

    //实时便笺桌面组件
    abstract val dailyNoteWidgetDao: DailyNoteWidgetDao

    //成就管理用户
    abstract val achievementUserDao: AchievementUserDao

    //成就管理数据表
    abstract val achievementsDao: AchievementsDao

    //养成计算计划表
    abstract val cultivateProjectDao: CultivateProjectDao

    //养成计划实体表
    abstract val cultivateEntityDao: CultivateEntityDao

    //养成计划计算项表
    abstract val cultivateItemsDao: CultivateItemsDao

    //养成计划计算项材料表
    abstract val cultivateItemMaterialsDao: CultivateItemMaterialsDao


    companion object {
        private const val DB_NAME = "paimonsnotebook_database.db"

        /*
        * 8 -> 9:修正 ledger_month_snapshots 的主键,把 year 纳入。
        *
        * ⚠️ **改主键不能用 AutoMigration**,必须手写(新建表 → 拷数据 → 删旧表 → 改名)。
        * 原主键是 (uid, month),而接口的 month 只有 1~12 不带年份 ⇒
        * "去年 12 月"与"今年 12 月"算同一条,配合 IGNORE 策略会让
        * **第二年起每个月的快照全部存不进去**。
        *
        * 旧表已有数据:按 (uid, year, month) 拷贝;旧表本来就按 (uid, month)
        * 唯一,加上 year 后只会更宽松,故**不会丢行**,也无需去重。
        * */
        private val migration_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `ledger_month_snapshots_new` (
                        `uid` TEXT NOT NULL,
                        `month` INTEGER NOT NULL,
                        `year` INTEGER NOT NULL,
                        `nickname` TEXT NOT NULL,
                        `region` TEXT NOT NULL,
                        `current_primogems` INTEGER NOT NULL,
                        `current_mora` INTEGER NOT NULL,
                        `last_primogems` INTEGER NOT NULL,
                        `last_mora` INTEGER NOT NULL,
                        `group_by` TEXT NOT NULL,
                        `saved_at` INTEGER NOT NULL,
                        PRIMARY KEY(`uid`, `year`, `month`)
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    INSERT INTO `ledger_month_snapshots_new`
                    SELECT `uid`, `month`, `year`, `nickname`, `region`,
                           `current_primogems`, `current_mora`,
                           `last_primogems`, `last_mora`, `group_by`, `saved_at`
                    FROM `ledger_month_snapshots`
                    """.trimIndent()
                )

                db.execSQL("DROP TABLE `ledger_month_snapshots`")
                db.execSQL(
                    "ALTER TABLE `ledger_month_snapshots_new` RENAME TO `ledger_month_snapshots`"
                )
            }
        }

        val database by lazy {
            synchronized(this) {
                Room.databaseBuilder(
                    PaimonsNotebookApplication.context,
                    PaimonsNotebookDatabase::class.java,
                    DB_NAME
                )
                    .addMigrations(migration_8_9)
                    .build()
            }
        }
    }

}