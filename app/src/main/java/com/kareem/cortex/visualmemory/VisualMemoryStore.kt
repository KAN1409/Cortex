package com.kareem.cortex.visualmemory

import android.content.Context
import androidx.room.Room
import com.kareem.cortex.visualmemory.data.db.PicBrainDatabase

object VisualMemoryStore {
    @Volatile private var instance: PicBrainDatabase? = null

    @JvmStatic
    fun database(context: Context): PicBrainDatabase =
        instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                PicBrainDatabase::class.java,
                "cortex_visual_memory.db"
            ).build().also { instance = it }
        }
}
