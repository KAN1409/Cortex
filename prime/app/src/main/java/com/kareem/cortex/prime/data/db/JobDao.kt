package com.kareem.cortex.prime.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface JobDao {
    /** One job per (stage, evidence): returns -1 when it already exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(j: Job): Long

    @Query("SELECT * FROM job WHERE id = :id")
    fun byId(id: Long): Job

    @Query("SELECT * FROM job WHERE evidenceId = :evidenceId AND stage = :stage LIMIT 1")
    fun forEvidence(evidenceId: Long, stage: String = "perceive"): Job?

    /** Next claimable job: waiting and due, or running with an expired lease. */
    @Query(
        "SELECT * FROM job WHERE stage = :stage AND " +
            "((state = 'WAITING' AND nextRunAt <= :now) OR (state = 'RUNNING' AND leaseUntil < :now)) " +
            "ORDER BY nextRunAt, id LIMIT 1",
    )
    fun nextClaimable(stage: String, now: Long): Job?

    /** Conditional update so two workers can never take the same job. */
    @Query(
        "UPDATE job SET state = 'RUNNING', attempts = attempts + 1, leaseUntil = :leaseUntil, updatedAt = :now " +
            "WHERE id = :id AND state = :expectedState AND updatedAt = :expectedUpdatedAt",
    )
    fun takeLease(id: Long, expectedState: JobState, expectedUpdatedAt: Long, leaseUntil: Long, now: Long): Int

    @Query("UPDATE job SET nextRunAt = :now, lastError = NULL, updatedAt = :now WHERE state = 'WAITING' AND lastError = :reason")
    fun unpark(reason: String, now: Long): Int

    @Update
    fun update(j: Job)

    @Query("SELECT * FROM job WHERE evidenceId = :evidenceId")
    fun observeForEvidence(evidenceId: Long): Flow<List<Job>>

    @Query("SELECT * FROM job")
    fun observeAll(): Flow<List<Job>>
}
