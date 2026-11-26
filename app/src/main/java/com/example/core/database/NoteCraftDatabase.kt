package com.example.core.database

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val bpm: Float,
    val keyLabel: String,
    val trackCount: Int,
    val artworkSeed: Int,
    val isDemo: Boolean,
    val filePath: String,
    val createdAt: Long,
    val modifiedAt: Long
)

@Entity(tableName = "samples")
data class SampleEntity(
    @PrimaryKey val id: String,
    val name: String,
    val category: String,
    val folder: String,
    val filePath: String,
    val durationMs: Long,
    val sampleRate: Int,
    val channels: Int,
    val slotIndex: Int,
    val isFavorite: Boolean,
    val lastUsedAt: Long,
    val peaksCsv: String
)

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY modifiedAt DESC")
    fun observeAllProjects(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects ORDER BY modifiedAt DESC")
    suspend fun getAllProjectsOnce(): List<ProjectEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProject(project: ProjectEntity)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun deleteProjectById(id: String)
}

@Dao
interface SampleDao {
    @Query("SELECT * FROM samples ORDER BY lastUsedAt DESC, name ASC")
    fun observeAllSamples(): Flow<List<SampleEntity>>

    @Query("SELECT * FROM samples")
    suspend fun getAllSamplesOnce(): List<SampleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSample(sample: SampleEntity)

    @Query("UPDATE samples SET isFavorite = :fav WHERE id = :id")
    suspend fun setFavorite(id: String, fav: Boolean)

    @Query("UPDATE samples SET lastUsedAt = :ts WHERE id = :id")
    suspend fun touchSample(id: String, ts: Long)

    @Query("DELETE FROM samples WHERE id = :id")
    suspend fun deleteSampleById(id: String)
}

@Database(
    entities = [ProjectEntity::class, SampleEntity::class],
    version = 1,
    exportSchema = false
)
abstract class NoteCraftDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun sampleDao(): SampleDao

    companion object {
        @Volatile
        private var INSTANCE: NoteCraftDatabase? = null

        fun getInstance(context: Context): NoteCraftDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    NoteCraftDatabase::class.java,
                    "notecraft_studio.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
