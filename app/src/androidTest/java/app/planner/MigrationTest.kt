package app.planner

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.planner.data.local.db.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @Test fun migration1To2AddsSecondsAndKeepsExistingSessions() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(null) // in-memory
            .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE study_sessions (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, courseId INTEGER, " +
                            "title TEXT NOT NULL, dateEpochDay INTEGER NOT NULL, startMinute INTEGER NOT NULL, " +
                            "durationMinutes INTEGER NOT NULL, notes TEXT, completed INTEGER NOT NULL, " +
                            "createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, deletedAt INTEGER)"
                    )
                    db.execSQL(
                        "INSERT INTO study_sessions (title, dateEpochDay, startMinute, durationMinutes, completed, createdAt, updatedAt) " +
                            "VALUES ('Old session', 20000, 600, 45, 0, 1, 1)"
                    )
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        val db = helper.writableDatabase

        AppDatabase.MIGRATION_1_2.migrate(db)

        db.query("SELECT title, durationMinutes, extraSeconds FROM study_sessions").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Old session", c.getString(0))
            assertEquals(45, c.getInt(1))
            assertEquals(0, c.getInt(2))
        }
        helper.close()
    }

    @Test fun migration2To3AddsAlternatingWeeksAndKeepsExistingClasses() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(null)
            .callback(object : SupportSQLiteOpenHelper.Callback(2) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE study_events (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, courseId INTEGER NOT NULL, " +
                            "type TEXT NOT NULL, attendance TEXT NOT NULL, dayOfWeek INTEGER NOT NULL, startMinute INTEGER NOT NULL, " +
                            "endMinute INTEGER NOT NULL, location TEXT, onlineLink TEXT, notes TEXT, " +
                            "createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, deletedAt INTEGER)"
                    )
                    db.execSQL(
                        "INSERT INTO study_events (courseId, type, attendance, dayOfWeek, startMinute, endMinute, createdAt, updatedAt) " +
                            "VALUES (1, 'LECTURE', 'PHYSICAL', 6, 480, 570, 1, 1)"
                    )
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        val db = helper.writableDatabase

        AppDatabase.MIGRATION_2_3.migrate(db)

        db.query("SELECT dayOfWeek, startMinute, repeatEveryWeeks, anchorWeekStart, topic FROM study_events").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(6, c.getInt(0))
            assertEquals(480, c.getInt(1))
            assertEquals(1, c.getInt(2))      // old classes keep repeating every week
            assertEquals(0L, c.getLong(3))
            assertTrue(c.isNull(4))
        }
        helper.close()
    }

    @Test fun migration3To4MarksExistingOvernightClassesAsEndingTheNextDay() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(null)
            .callback(object : SupportSQLiteOpenHelper.Callback(3) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE study_events (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, courseId INTEGER NOT NULL, " +
                            "type TEXT NOT NULL, attendance TEXT NOT NULL, dayOfWeek INTEGER NOT NULL, startMinute INTEGER NOT NULL, " +
                            "endMinute INTEGER NOT NULL, location TEXT, onlineLink TEXT, notes TEXT, " +
                            "repeatEveryWeeks INTEGER NOT NULL DEFAULT 1, anchorWeekStart INTEGER NOT NULL DEFAULT 0, topic TEXT, " +
                            "createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, deletedAt INTEGER)"
                    )
                    db.execSQL("INSERT INTO study_events (courseId, type, attendance, dayOfWeek, startMinute, endMinute, createdAt, updatedAt) VALUES (1, 'LECTURE', 'PHYSICAL', 6, 480, 570, 1, 1)")
                    db.execSQL("INSERT INTO study_events (courseId, type, attendance, dayOfWeek, startMinute, endMinute, createdAt, updatedAt) VALUES (1, 'LECTURE', 'PHYSICAL', 5, 1380, 60, 1, 1)")
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        val db = helper.writableDatabase

        AppDatabase.MIGRATION_3_4.migrate(db)

        db.query("SELECT dayOfWeek, endDayOffset FROM study_events ORDER BY id").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(6, c.getInt(0)); assertEquals("an ordinary class ends the same day", 0, c.getInt(1))
            assertTrue(c.moveToNext())
            assertEquals(5, c.getInt(0)); assertEquals("11 PM -> 1 AM ends the next day", 1, c.getInt(1))
        }
        helper.close()
    }
}
