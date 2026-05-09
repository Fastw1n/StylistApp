package com.example.app1

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction

@Entity(tableName = "wardrobe_items")
data class WardrobeItemEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
    val category: String?,
    @ColumnInfo(name = "image_url") val imageUrl: String?,
    @ColumnInfo(name = "image_uri") val imageUri: String?,
    val subcategory: String?,
    val season: String?,
    @ColumnInfo(name = "warmth_level") val warmthLevel: Int?,
    @ColumnInfo(name = "colors_json") val colorsJson: String?
)

@Dao
abstract class WardrobeItemDao {
    @Query("SELECT * FROM wardrobe_items ORDER BY sort_order ASC")
    abstract suspend fun getAll(): List<WardrobeItemEntity>

    @Query("SELECT COUNT(*) FROM wardrobe_items")
    abstract suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAll(items: List<WardrobeItemEntity>)

    @Query("DELETE FROM wardrobe_items")
    abstract suspend fun clear()

    @Transaction
    open suspend fun replaceAll(items: List<WardrobeItemEntity>) {
        clear()
        if (items.isNotEmpty()) {
            insertAll(items)
        }
    }
}

@Database(
    entities = [WardrobeItemEntity::class],
    version = 1,
    exportSchema = false
)
abstract class WardrobeCacheDatabase : RoomDatabase() {
    abstract fun wardrobeItemDao(): WardrobeItemDao

    companion object {
        @Volatile
        private var instance: WardrobeCacheDatabase? = null

        fun getInstance(context: Context): WardrobeCacheDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    WardrobeCacheDatabase::class.java,
                    "wardrobe_cache.db"
                ).build().also { instance = it }
            }
        }
    }
}
