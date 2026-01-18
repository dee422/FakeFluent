package com.dee.android.pbl.fakefluent.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorite_words")
data class FavoriteWord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val originalText: String,    // 选中的单词或句子
    val correction: String = "", // 纠错建议
    val scene: String = "",      // 来源场景
    val timestamp: Long = System.currentTimeMillis()
)