package app.planner.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import app.planner.data.local.entity.*
import kotlinx.coroutines.flow.Flow

interface BaseDao<T> {
    @Insert suspend fun insert(item: T): Long
    @Update suspend fun update(item: T)
}

@Dao
interface CourseDao : BaseDao<CourseEntity> {
    @Query("SELECT * FROM courses WHERE deletedAt IS NULL ORDER BY name")
    fun observeActive(): Flow<List<CourseEntity>>

    @Query("SELECT * FROM courses WHERE id = :id")
    suspend fun get(id: Long): CourseEntity?

    @Query("UPDATE courses SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: Long, now: Long)
}

@Dao
interface StudyEventDao : BaseDao<StudyEventEntity> {
    @Query("SELECT * FROM study_events WHERE deletedAt IS NULL ORDER BY dayOfWeek, startMinute")
    fun observeActive(): Flow<List<StudyEventEntity>>

    @Query("SELECT * FROM study_events WHERE deletedAt IS NULL")
    suspend fun getActive(): List<StudyEventEntity>

    @Query("SELECT * FROM study_events WHERE id = :id")
    suspend fun get(id: Long): StudyEventEntity?

    @Query("UPDATE study_events SET deletedAt = :now, updatedAt = :now WHERE courseId = :courseId AND deletedAt IS NULL")
    suspend fun softDeleteByCourse(courseId: Long, now: Long)

    @Query("UPDATE study_events SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: Long, now: Long)
}

@Dao
interface StudySessionDao : BaseDao<StudySessionEntity> {
    @Query("SELECT * FROM study_sessions WHERE deletedAt IS NULL ORDER BY dateEpochDay, startMinute")
    fun observeAll(): Flow<List<StudySessionEntity>>

    @Query("SELECT * FROM study_sessions WHERE id = :id")
    suspend fun get(id: Long): StudySessionEntity?

    @Query("UPDATE study_sessions SET completed = :done, updatedAt = :now WHERE id = :id")
    suspend fun setCompleted(id: Long, done: Boolean, now: Long)

    @Query("UPDATE study_sessions SET deletedAt = :now, updatedAt = :now WHERE courseId = :courseId AND deletedAt IS NULL")
    suspend fun softDeleteByCourse(courseId: Long, now: Long)

    @Query("UPDATE study_sessions SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: Long, now: Long)
}

@Dao
interface ExamDao : BaseDao<ExamEntity> {
    @Query("SELECT * FROM exams WHERE deletedAt IS NULL ORDER BY dateEpochDay, startMinute")
    fun observeAll(): Flow<List<ExamEntity>>

    @Query("SELECT * FROM exams WHERE deletedAt IS NULL")
    suspend fun getActive(): List<ExamEntity>

    @Query("SELECT * FROM exams WHERE id = :id")
    suspend fun get(id: Long): ExamEntity?

    @Query("UPDATE exams SET deletedAt = :now, updatedAt = :now WHERE courseId = :courseId AND deletedAt IS NULL")
    suspend fun softDeleteByCourse(courseId: Long, now: Long)

    @Query("UPDATE exams SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: Long, now: Long)
}

@Dao
interface AssignmentDao : BaseDao<AssignmentEntity> {
    @Query("SELECT * FROM assignments WHERE deletedAt IS NULL ORDER BY completed, dueEpochDay, dueMinute")
    fun observeAll(): Flow<List<AssignmentEntity>>

    @Query("SELECT * FROM assignments WHERE deletedAt IS NULL AND completed = 0")
    suspend fun getIncomplete(): List<AssignmentEntity>

    @Query("SELECT * FROM assignments WHERE id = :id")
    suspend fun get(id: Long): AssignmentEntity?

    @Query("UPDATE assignments SET completed = :done, completedAt = :completedAt, updatedAt = :now WHERE id = :id")
    suspend fun setCompleted(id: Long, done: Boolean, completedAt: Long?, now: Long)

    @Query("UPDATE assignments SET deletedAt = :now, updatedAt = :now WHERE courseId = :courseId AND deletedAt IS NULL")
    suspend fun softDeleteByCourse(courseId: Long, now: Long)

    @Query("UPDATE assignments SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: Long, now: Long)
}

@Dao
interface NotificationRecordDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: NotificationRecordEntity): Long

    @Query("SELECT * FROM notification_records WHERE ownerType = :type AND ownerId = :ownerId")
    suspend fun forOwner(type: NotificationOwnerType, ownerId: Long): List<NotificationRecordEntity>

    @Query("SELECT * FROM notification_records WHERE status = 'SCHEDULED'")
    suspend fun allScheduled(): List<NotificationRecordEntity>

    @Query("SELECT * FROM notification_records WHERE status IN ('SCHEDULED', 'BLOCKED_NO_PERMISSION') ORDER BY triggerAtMillis")
    suspend fun allActive(): List<NotificationRecordEntity>

    @Query("SELECT * FROM notification_records WHERE status IN ('SCHEDULED', 'BLOCKED_NO_PERMISSION') ORDER BY triggerAtMillis")
    fun observeActive(): Flow<List<NotificationRecordEntity>>

    @Query("SELECT * FROM notification_records")
    suspend fun all(): List<NotificationRecordEntity>

    @Query("SELECT * FROM notification_records WHERE requestCode = :code")
    suspend fun byRequestCode(code: Int): NotificationRecordEntity?

    @Query("UPDATE notification_records SET status = :status, firedAt = :now, updatedAt = :now WHERE requestCode = :requestCode")
    suspend fun markFired(requestCode: Int, status: NotificationStatus, now: Long)

    @Query("UPDATE notification_records SET status = :status, updatedAt = :now WHERE id = :id")
    suspend fun setStatus(id: Long, status: NotificationStatus, now: Long)
}

@Dao
interface AppSettingDao {
    @Query("SELECT value FROM app_settings WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Query("SELECT value FROM app_settings WHERE `key` = :key")
    fun observe(key: String): Flow<String?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(setting: AppSettingEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun putIfAbsent(setting: AppSettingEntity): Long
}
