package com.tuorg.notasmultimedia.data

import com.tuorg.notasmultimedia.model.db.*
import kotlinx.coroutines.flow.Flow

interface NoteRepository {
    fun all(): Flow<List<NoteWithRelations>>
    fun byType(type: ItemType): Flow<List<NoteWithRelations>>
    fun byId(id: String): Flow<NoteWithRelations?>
    fun search(q: String): Flow<List<NoteEntity>>

    suspend fun upsertGraph(
        note: NoteEntity,
        attachments: List<AttachmentEntity>,
        reminders: List<ReminderEntity>
    )

    suspend fun deleteNote(note: NoteEntity)

    suspend fun syncNow()
}
