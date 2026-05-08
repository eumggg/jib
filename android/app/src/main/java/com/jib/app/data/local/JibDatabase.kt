package com.jib.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.jib.app.data.model.Station

// Bumped to 2 when `address` was added to Station. Cache is rebuildable, so
// the DI module uses fallbackToDestructiveMigration() — no migration plan needed.
@Database(entities = [Station::class], version = 2, exportSchema = false)
abstract class JibDatabase : RoomDatabase() {
    abstract fun stationDao(): StationDao
}
