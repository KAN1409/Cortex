package com.kareem.cortex.visualmemory

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.kareem.cortex.visualmemory.data.db.PicBrainDatabase

object VisualMemoryStore {
    @Volatile private var instance: PicBrainDatabase? = null

    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE media_items ADD COLUMN origin TEXT NOT NULL DEFAULT 'UNKNOWN'")
            db.execSQL("ALTER TABLE media_items ADD COLUMN selfReferenceScore REAL NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE media_items ADD COLUMN derivationDepth INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE media_items ADD COLUMN knowledgeEligible INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE media_items ADD COLUMN provenanceReason TEXT")
        }
    }

    @JvmStatic
    fun database(context: Context): PicBrainDatabase =
        instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                PicBrainDatabase::class.java,
                "cortex_visual_memory.db"
            )
                .addMigrations(MIGRATION_4_5)
                .build()
                .also { instance = it }
        }
}
