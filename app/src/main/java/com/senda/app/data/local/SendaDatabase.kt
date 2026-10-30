package com.senda.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.senda.app.data.local.dao.AppGroupDao
import com.senda.app.data.local.dao.ChallengeReasonDao
import com.senda.app.data.local.dao.DailyUsageLogDao
import com.senda.app.data.local.dao.GroupItemDao
import com.senda.app.data.local.dao.GroupScheduleDao
import com.senda.app.data.local.entity.AppGroupEntity
import com.senda.app.data.local.entity.ChallengeReasonEntity
import com.senda.app.data.local.entity.DailyUsageLogEntity
import com.senda.app.data.local.entity.GroupItemEntity
import com.senda.app.data.local.entity.GroupScheduleEntity
import com.senda.app.data.local.entity.SessionUsageEntity
import com.senda.app.data.local.entity.ReportDayEntity
import com.senda.app.data.local.entity.DailyGoalEntity
import com.senda.app.data.local.entity.ReportMonitorCheckpointEntity
import com.senda.app.data.local.dao.ReportHistoryDao

@Database(
    entities = [
        AppGroupEntity::class,
        GroupItemEntity::class,
        GroupScheduleEntity::class,
        DailyUsageLogEntity::class,
        ChallengeReasonEntity::class,
        SessionUsageEntity::class,
        ReportDayEntity::class,
        DailyGoalEntity::class,
        ReportMonitorCheckpointEntity::class
    ],
    version = 8,
    exportSchema = true
)
@TypeConverters(SendaConverters::class)
abstract class SendaDatabase : RoomDatabase() {

    abstract fun appGroupDao(): AppGroupDao

    abstract fun groupItemDao(): GroupItemDao

    abstract fun groupScheduleDao(): GroupScheduleDao

    abstract fun dailyUsageLogDao(): DailyUsageLogDao

    abstract fun challengeReasonDao(): ChallengeReasonDao

    abstract fun reportHistoryDao(): ReportHistoryDao

    companion object {
        private const val DATABASE_NAME = "senda.db"

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE group_schedules ADD COLUMN ruleId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE group_schedules ADD COLUMN name TEXT NOT NULL DEFAULT ''")
                db.execSQL("UPDATE group_schedules SET ruleId = 'legacy-schedule-' || id")
                listOf("dailyLimitMinutes", "sessionLimitMinutes", "cooldownMinutes", "maxDailyOpens",
                    "sharedLimitAcrossApps", "breathingSeconds", "mathEquationCount", "progressiveFrictionEnabled",
                    "progressiveSecondsPerOpen").forEach { db.execSQL("ALTER TABLE group_schedules ADD COLUMN custom_$it INTEGER") }
                listOf("sessionCountMode", "interventionType", "mathDifficulty", "alternativeAppPackage").forEach {
                    db.execSQL("ALTER TABLE group_schedules ADD COLUMN custom_$it TEXT")
                }
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS `report_days` (
                    `date` TEXT NOT NULL PRIMARY KEY, `coveredMillis` INTEGER NOT NULL,
                    `expectedMillis` INTEGER NOT NULL, `goalsRecorded` INTEGER NOT NULL, `zoneId` TEXT NOT NULL,
                    `goalsReliable` INTEGER NOT NULL)
                """.trimIndent())
                db.execSQL("""CREATE TABLE IF NOT EXISTS `daily_goals` (
                    `date` TEXT NOT NULL, `usageKey` TEXT NOT NULL, `groupId` TEXT NOT NULL,
                    `groupName` TEXT NOT NULL, `targetName` TEXT NOT NULL,
                    `limitSeconds` INTEGER NOT NULL, `shared` INTEGER NOT NULL, PRIMARY KEY(`date`, `usageKey`))
                """.trimIndent())
                db.execSQL("""CREATE TABLE IF NOT EXISTS `report_monitor_checkpoint` (
                    `id` INTEGER NOT NULL PRIMARY KEY, `epochMillis` INTEGER NOT NULL,
                    `elapsedRealtime` INTEGER NOT NULL, `bootCount` INTEGER NOT NULL,
                    `healthy` INTEGER NOT NULL, `zoneId` TEXT NOT NULL,
                    `screenOff` INTEGER NOT NULL, `processToken` TEXT NOT NULL)
                """.trimIndent())
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `session_usage` (
                        `sessionId` TEXT NOT NULL,
                        `date` TEXT NOT NULL,
                        `usageKey` TEXT NOT NULL,
                        `accountedSeconds` INTEGER NOT NULL,
                        PRIMARY KEY(`sessionId`, `date`)
                    )
                """.trimIndent())
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `challenge_reasons` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `packageName` TEXT NOT NULL,
                        `displayName` TEXT NOT NULL,
                        `groupId` TEXT NOT NULL,
                        `reason` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS `index_challenge_reasons_createdAt`
                    ON `challenge_reasons` (`createdAt`)
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    ALTER TABLE app_groups
                    ADD COLUMN mathDifficulty TEXT NOT NULL DEFAULT 'EASY'
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    ALTER TABLE app_groups
                    ADD COLUMN mathEquationCount INTEGER NOT NULL DEFAULT 3
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    ALTER TABLE app_groups
                    ADD COLUMN sharedLimitAcrossApps INTEGER NOT NULL DEFAULT 1
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    ALTER TABLE daily_usage_logs
                    ADD COLUMN totalTimeUsedSeconds INTEGER NOT NULL DEFAULT 0
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    UPDATE daily_usage_logs
                    SET totalTimeUsedSeconds = totalTimeUsedMinutes * 60
                    """.trimIndent()
                )
            }
        }

        @Volatile
        private var instance: SendaDatabase? = null

        fun getInstance(context: Context): SendaDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    SendaDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
                    .addCallback(INTEGRITY_CALLBACK)
                    .build()
                    .also { instance = it }
            }

        /**
         * O banco é o coração do app; se ele estiver corrompido, toda tela falha
         * com exceção de SQLite. O `quick_check` roda uma vez na abertura (base
         * pequena, custo desprezível) e o resultado aparece em Ajustes.
         */
        private val INTEGRITY_CALLBACK = object : RoomDatabase.Callback() {
            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                runCatching {
                    db.query("PRAGMA quick_check").use { cursor ->
                        val result = if (cursor.moveToFirst()) cursor.getString(0) else null
                        if (result.equals("ok", ignoreCase = true)) {
                            com.senda.app.core.DatabaseHealth.markHealthy()
                        } else {
                            com.senda.app.core.DatabaseHealth.markCorrupted(result)
                        }
                    }
                }.onFailure { error ->
                    com.senda.app.core.DatabaseHealth.markCorrupted(error.message)
                }
            }
        }
    }
}
