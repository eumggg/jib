package com.jib.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.jib.app.data.model.Station

@Database(entities = [Station::class], version = 1, exportSchema = false)
abstract class JibDatabase : RoomDatabase() {
    abstract fun stationDao(): StationDao
}
