package com.kareem.cortex.prime

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kareem.cortex.prime.data.db.CortexDatabase

fun testDb(): CortexDatabase =
    Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), CortexDatabase::class.java)
        .allowMainThreadQueries().build()
