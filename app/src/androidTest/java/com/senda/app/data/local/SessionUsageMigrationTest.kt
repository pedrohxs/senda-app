package com.senda.app.data.local

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class SessionUsageMigrationTest {
    @Test
    fun migraBancoRealDaVersaoCincoPreservandoHistorico() = migrateFrom(5)

    @Test
    fun migraBancoRealDaVersaoSeisPreservandoContabilizacao() = migrateFrom(6)

    @Test
    fun migraBancoRealDaVersaoSetePreservandoHorarios() = migrateFrom(7)

    private fun migrateFrom(version: Int) = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val name = "migration-${UUID.randomUUID()}.db"
        val schema = instrumentation.context.assets.open("com.senda.app.data.local.SendaDatabase/$version.json")
            .bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(version) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        val entities = schema.getJSONArray("entities")
                        for (index in 0 until entities.length()) {
                            val entity = entities.getJSONObject(index)
                            val table = entity.getString("tableName")
                            db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                            val indices = entity.optJSONArray("indices") ?: JSONArray()
                            for (i in 0 until indices.length()) {
                                db.execSQL(indices.getJSONObject(i).getString("createSql").replace("\${TABLE_NAME}", table))
                            }
                        }
                        val setup = schema.getJSONArray("setupQueries")
                        for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                }).build()
        )
        try {
            helper.writableDatabase.execSQL("""INSERT INTO app_groups
                (id,name,colorHex,isEnabled,dailyLimitMinutes,sessionLimitMinutes,cooldownMinutes,maxDailyOpens,sharedLimitAcrossApps,
                 sessionCountMode,interventionType,breathingSeconds,mathDifficulty,mathEquationCount,progressiveFrictionEnabled,
                 progressiveSecondsPerOpen,alternativeAppPackage,temporaryDisableUntil)
                VALUES ('migration-group','Redes','#A1B976',1,30,5,10,15,1,'ACTIVE_SCREEN_TIME','BREATHING',10,'EASY',3,1,5,NULL,NULL)
            """.trimIndent())
            helper.writableDatabase.execSQL("""INSERT INTO group_schedules
                (id,groupId,scheduleType,daysOfWeek,startHour,startMinute,endHour,endMinute)
                VALUES (42,'migration-group','LIMITED','7,1',0,0,0,0)
            """.trimIndent())
            helper.writableDatabase.execSQL("""
                INSERT INTO daily_usage_logs
                (date, groupId, totalTimeUsedMinutes, totalTimeUsedSeconds, totalOpens, impulsesAborted)
                VALUES ('2026-10-26', 'migration-group', 2, 125, 3, 1)
            """.trimIndent())
            if (version >= 6) helper.writableDatabase.execSQL("""
                INSERT INTO session_usage (sessionId, date, usageKey, accountedSeconds)
                VALUES ('old-session', '2026-10-26', 'migration-group', 10)
            """.trimIndent())
            helper.close()
            val migrated = Room.databaseBuilder(context, SendaDatabase::class.java, name)
                .addMigrations(SendaDatabase.MIGRATION_5_6, SendaDatabase.MIGRATION_6_7, SendaDatabase.MIGRATION_7_8).build()
            try {
                val dao = migrated.dailyUsageLogDao()
                assertEquals(emptyList<com.senda.app.data.local.entity.DailyGoalEntity>(),
                    migrated.reportHistoryDao().goalsForDate("2026-10-26"))
                if (version >= 6) assertEquals(10, dao.findSessionUsage("old-session", "2026-10-26")?.accountedSeconds)
                val schedule = migrated.groupScheduleDao().getForGroup("migration-group").single()
                assertEquals("legacy-schedule-42", schedule.ruleId)
                assertEquals(null, schedule.customRules)
                val config = com.senda.app.data.local.entity.LimitRules(dailyLimitMinutes = 90, sessionLimitMinutes = 15, cooldownMinutes = 3)
                migrated.groupScheduleDao().upsert(schedule.copy(customRules = config, name = "Fim de semana"))
                assertEquals(config, migrated.groupScheduleDao().getForGroup("migration-group").single().customRules)
                assertEquals(125, dao.findByDateAndGroup("2026-10-26", "migration-group")?.totalTimeUsedSeconds)
                repeat(2) { dao.recordSessionUsage("new-session", "migration-group", mapOf("2026-10-26" to 10)) }
                assertEquals(135, dao.findByDateAndGroup("2026-10-26", "migration-group")?.totalTimeUsedSeconds)
            } finally {
                migrated.close()
            }
        } finally {
            helper.close()
            context.deleteDatabase(name)
        }
    }
}
