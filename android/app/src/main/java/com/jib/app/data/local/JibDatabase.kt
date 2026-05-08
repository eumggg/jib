package com.jib.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.jib.app.data.model.Station

// Bumped to 3 when avgRating + recentCheckInAt were added to Station for Phase 2.
// Cache is rebuildable, so the DI module uses fallbackToDestructiveMigration().
@Database(entities = [Station::class], version = 3, exportSchema = false)
abstract class JibDatabase : RoomDatabase() {
    abstract fun stationDao(): StationDao
}
