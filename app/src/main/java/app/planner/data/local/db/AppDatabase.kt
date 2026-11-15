package app.planner.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import app.planner.data.local.dao.*
import app.planner.data.local.entity.*

@Database(
    entities = [
        CourseEntity::class, StudyEventEntity::class, StudySessionEntity::class,
        ExamEntity::class, AssignmentEntity::class,
        NotificationRecordEntity::class, AppSettingEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun courseDao(): CourseDao
    abstract fun studyEventDao(): StudyEventDao
    abstract fun studySessionDao(): StudySessionDao
    abstract fun examDao(): ExamDao
    abstract fun assignmentDao(): AssignmentDao
    abstract fun notificationRecordDao(): NotificationRecordDao
    abstract fun appSettingDao(): AppSettingDao

    companion object {
        /** One explicit Migration per schema bump. Destructive fallback is deliberately NOT enabled. */
        /** v2: study sessions can have a seconds part (timer). Existing rows keep their minutes. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE study_sessions ADD COLUMN extraSeconds INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** v3: alternating classes (every 2nd week) and an optional topic label. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE study_events ADD COLUMN repeatEveryWeeks INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE study_events ADD COLUMN anchorWeekStart INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE study_events ADD COLUMN topic TEXT")
            }
        }

        /** v4: classes name the day they end on. Rows that already ended "before" they started passed midnight. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE study_events ADD COLUMN endDayOffset INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE study_events SET endDayOffset = 1 WHERE endMinute <= startMinute")
            }
        }

        val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "planner.db")
                .addMigrations(*MIGRATIONS)
                .build()
    }
}
