package com.kareem.cortex.prime.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

enum class LoopState { UNCONFIRMED, OPEN, DONE, DISMISSED }
enum class Area { LIFE, FAMILY, HEALTH, WORK }

/** Something Cortex thinks you might forget, always pointing back to the exact source words. */
@Entity(tableName = "open_loop", indices = [Index("evidenceId"), Index("state")])
data class OpenLoop(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val evidenceId: Long,
    val action: String,
    val sourceText: String,
    val spanStart: Int,
    val spanEnd: Int,
    val dueAt: Long?,
    val duePrecision: String?,
    val confidence: Double,
    val state: LoopState,
    val area: Area,
    val createdBy: String,
    val createdAt: Long,
    val updatedAt: Long,
)

enum class ReminderState { SCHEDULED, FIRED, CANCELLED }

@Entity(tableName = "reminder", indices = [Index("openLoopId"), Index("state")])
data class Reminder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val openLoopId: Long,
    val fireAt: Long,
    val state: ReminderState,
)

data class LoopWithSource(@androidx.room.Embedded val loop: OpenLoop, val sourceKind: EvidenceKind)

@Dao
interface LoopDao {
    @Query("SELECT open_loop.*, evidence.kind AS sourceKind FROM open_loop JOIN evidence ON evidence.id = open_loop.evidenceId WHERE open_loop.state IN ('OPEN','UNCONFIRMED') ORDER BY CASE WHEN open_loop.dueAt IS NULL THEN 1 ELSE 0 END, open_loop.dueAt, open_loop.id")
    fun observeActiveWithSource(): Flow<List<LoopWithSource>>

    @Insert fun insert(l: OpenLoop): Long
    @Update fun update(l: OpenLoop)
    @Query("SELECT * FROM open_loop WHERE id = :id") fun byId(id: Long): OpenLoop?
    @Query("SELECT COUNT(*) FROM open_loop WHERE evidenceId = :evidenceId") fun countFor(evidenceId: Long): Int
    @Query("SELECT * FROM open_loop WHERE state IN ('OPEN','UNCONFIRMED') ORDER BY CASE WHEN dueAt IS NULL THEN 1 ELSE 0 END, dueAt, id")
    fun observeActive(): Flow<List<OpenLoop>>

    @Insert fun insertReminder(r: Reminder): Long
    @Query("SELECT * FROM reminder WHERE openLoopId = :loopId AND state = 'SCHEDULED'") fun scheduledFor(loopId: Long): List<Reminder>
    @Query("SELECT * FROM reminder WHERE state = 'SCHEDULED'") fun allScheduled(): List<Reminder>
    @Query("UPDATE reminder SET state = :state WHERE id = :id") fun setReminderState(id: Long, state: ReminderState)
    @Query("SELECT * FROM reminder WHERE id = :id") fun reminder(id: Long): Reminder?
}
