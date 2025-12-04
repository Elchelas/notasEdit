package com.tuorg.notasmultimedia.model.db

import androidx.room.*
import java.time.LocalDateTime

enum class ItemType { NOTE, TASK }
enum class AttachmentType { IMAGE, VIDEO, AUDIO, FILE }

@Entity(tableName = "notes")
data class NoteEntity(

    @PrimaryKey val id: String,
    val title: String,
    val content: String,
    val type: ItemType,
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false,
    val dirty: Boolean = false,
    val description: String,
    val createdAt: LocalDateTime,
    val dueAt: LocalDateTime? = null,
    val completed: Boolean = false
)

@Entity(
    tableName = "attachments",
    foreignKeys = [ForeignKey(
        entity = NoteEntity::class,
        parentColumns = ["id"],
        childColumns = ["noteId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("noteId")]
)
data class AttachmentEntity(
    @PrimaryKey(autoGenerate = true) val attId: Long = 0,
    val noteId: String,
    val type: AttachmentType,
    val uri: String,                 // preparado para miniaturas
    val description: String? = null
)

@Entity(
    tableName = "reminders",
    foreignKeys = [ForeignKey(
        entity = NoteEntity::class,
        parentColumns = ["id"],
        childColumns = ["noteId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("noteId")]
)
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val remId: Long = 0,
    val noteId: String,
    val triggerAt: Long,
    val isRecurring: Boolean = false,
    val intervalMinutes: Long = 0
)

data class NoteWithRelations(
    @Embedded val note: NoteEntity,
    @Relation(parentColumn = "id", entityColumn = "noteId")
    val attachments: List<AttachmentEntity>,
    @Relation(parentColumn = "id", entityColumn = "noteId")
    val reminders: List<ReminderEntity>
)
