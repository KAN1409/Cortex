package com.kareem.cortex.prime.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface PerceptionDao {
    @Insert
    fun insert(p: Perception): Long

    @Query("SELECT * FROM perception WHERE evidenceId = :evidenceId ORDER BY id")
    fun forEvidence(evidenceId: Long): List<Perception>

    @Query("SELECT * FROM perception WHERE evidenceId = :evidenceId ORDER BY id")
    fun observeForEvidence(evidenceId: Long): kotlinx.coroutines.flow.Flow<List<Perception>>

    @Query("SELECT * FROM perception WHERE kind IN ('transcript', 'ocr')")
    fun observeTranscripts(): kotlinx.coroutines.flow.Flow<List<Perception>>
}
