package com.example.blinkerleague.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

// MVP에서는 스키마 내보내기 없이 시작함. 출시 후 스키마가 바뀌면 exportSchema + Migration 도입
@Database(entities = [TripEntity::class, TripEventEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "blinkerleague.db").build()
    }
}
