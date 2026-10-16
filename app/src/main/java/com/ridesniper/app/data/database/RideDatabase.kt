package com.ridesniper.app.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [RideEntity::class], version = 2, exportSchema = true)
abstract class RideDatabase : RoomDatabase() {

    abstract fun rideDao(): RideDao

    companion object {
        @Volatile private var INSTANCE: RideDatabase? = null

        fun getInstance(context: Context): RideDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    RideDatabase::class.java,
                    "ride_sniper.db"
                )
                    // v2 switched every column from miles/dollars to km/rand. Old rows would be
                    // misread under the new units, so they are dropped rather than migrated.
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
    }
}
