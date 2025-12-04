package com.tuorg.notasmultimedia.model.db

import androidx.room.*

@Entity(tableName = "sync_outbox")
data class SyncOpEntity(
    @PrimaryKey val id: String,
    val type: String,
    val payloadJson: String,
    val createdAt: Long
)

@Dao
interface SyncOutboxDao {
    @Query("SELECT * FROM sync_outbox ORDER BY createdAt ASC")
    suspend fun pending(): List<SyncOpEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(op: SyncOpEntity)

    @Query("DELETE FROM sync_outbox WHERE id = :id")
    suspend fun delete(id: String)
}
