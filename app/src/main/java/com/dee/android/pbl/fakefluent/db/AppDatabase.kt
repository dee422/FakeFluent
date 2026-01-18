package com.dee.android.pbl.fakefluent.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [FavoriteWord::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun favoriteWordDao(): FavoriteWordDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "fake_fluent_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}