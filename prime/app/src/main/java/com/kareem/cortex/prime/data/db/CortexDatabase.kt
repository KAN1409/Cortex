package com.kareem.cortex.prime.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Evidence::class, Perception::class, Job::class], version = 1, exportSchema = true)
abstract class CortexDatabase : RoomDatabase() {
    abstract fun evidence(): EvidenceDao
    abstract fun perception(): PerceptionDao
    abstract fun jobs(): JobDao

    companion object {
        const val NAME = "cortex_prime.db"

        fun build(context: Context, inMemory: Boolean = false): CortexDatabase {
            val b = if (inMemory) Room.inMemoryDatabaseBuilder(context, CortexDatabase::class.java)
            else Room.databaseBuilder(context, CortexDatabase::class.java, NAME)
            return b.build()
        }
    }
}
