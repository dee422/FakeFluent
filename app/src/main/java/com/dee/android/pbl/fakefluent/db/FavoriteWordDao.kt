package com.dee.android.pbl.fakefluent.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteWordDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(word: FavoriteWord)

    @Delete
    suspend fun delete(word: FavoriteWord)

    @Query("SELECT * FROM favorite_words ORDER BY timestamp DESC")
    fun getAllWords(): Flow<List<FavoriteWord>>

    @Query("SELECT EXISTS(SELECT * FROM favorite_words WHERE originalText = :text)")
    suspend fun isFavorite(text: String): Boolean
}