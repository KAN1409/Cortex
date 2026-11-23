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
}
