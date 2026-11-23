package com.kareem.cortex.prime.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface EvidenceDao {
    /** Returns the new row id, or -1 when the same content (sha256) is already stored. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(e: Evidence): Long

    @Query("SELECT * FROM evidence WHERE sha256 = :sha LIMIT 1")
    fun bySha(sha: String): Evidence?

    @Query("SELECT * FROM evidence WHERE id = :id")
    fun byId(id: Long): Evidence?

    @Query("SELECT * FROM evidence ORDER BY capturedAt DESC, id DESC")
    fun timeline(): Flow<List<Evidence>>
}
