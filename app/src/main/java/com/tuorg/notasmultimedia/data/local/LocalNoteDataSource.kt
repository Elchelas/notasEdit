package com.tuorg.notasmultimedia.data.local

import androidx.room.withTransaction
import com.tuorg.notasmultimedia.data.sync.NoteGraphPayload
import com.tuorg.notasmultimedia.model.db.AppDatabase
import com.tuorg.notasmultimedia.model.db.AttachmentEntity
import com.tuorg.notasmultimedia.model.db.ItemType
import com.tuorg.notasmultimedia.model.db.NoteEntity
import com.tuorg.notasmultimedia.model.db.NoteWithRelations
import com.tuorg.notasmultimedia.model.db.ReminderEntity
import com.tuorg.notasmultimedia.model.db.SyncOpEntity
import kotlinx.coroutines.flow.Flow

class LocalNoteDataSource(
    private val db: AppDatabase
) {

    fun all(): Flow<List<NoteWithRelations>> =
        db.noteDao().observeAll()

    fun byType(type: ItemType): Flow<List<NoteWithRelations>> =
        db.noteDao().observeByType(type)

    fun byId(id: String): Flow<NoteWithRelations?> =
        db.noteDao().observeById(id)

    fun search(q: String): Flow<List<NoteEntity>> =
        db.noteDao().search(q)

    suspend fun upsertGraphDirty(
        note: NoteEntity,
        attachments: List<AttachmentEntity>,
        reminders: List<ReminderEntity>
    ) = db.withTransaction {
        val now = System.currentTimeMillis()

        // upsert nota y relaciones
        db.noteDao().upsert(note.copy(updatedAt = now, dirty = true, isDeleted = false))
        db.attachmentDao().deleteByNote(note.id)
        db.reminderDao().deleteByNote(note.id)
        if (attachments.isNotEmpty()) db.attachmentDao().insertAll(attachments)
        if (reminders.isNotEmpty()) db.reminderDao().insertAll(reminders)

        // payload JSON (org.json, sin dependencias externas)
        val payloadJson = NoteGraphPayload
            .fromEntities(note.copy(updatedAt = now), attachments, reminders)
            .toJson()

        // encola operación
        db.syncOutboxDao().insert(
            SyncOpEntity(
                id = "op-${note.id}-$now",
                type = "UPSERT_NOTE",
                payloadJson = payloadJson,
                createdAt = now
            )
        )
    }

    suspend fun tombstone(note: NoteEntity) = db.withTransaction {
        val now = System.currentTimeMillis()

        db.noteDao().upsert(note.copy(isDeleted = true, dirty = true, updatedAt = now))

        val payloadJson = NoteGraphPayload
            .fromEntities(note.copy(isDeleted = true, updatedAt = now), emptyList(), emptyList())
            .toJson()

        db.syncOutboxDao().insert(
            SyncOpEntity(
                id = "del-${note.id}-$now",
                type = "DELETE_NOTE",
                payloadJson = payloadJson,
                createdAt = now
            )
        )
    }

    suspend fun applyRemoteGraphReplace(
        note: NoteEntity,
        attachments: List<AttachmentEntity>,
        reminders: List<ReminderEntity>
    ) = db.withTransaction {
        val local = db.noteDao().getById(note.id)
        if (local == null || note.updatedAt > local.updatedAt) {
            db.noteDao().upsert(note.copy(dirty = false))
            db.attachmentDao().deleteByNote(note.id)
            db.reminderDao().deleteByNote(note.id)
            if (attachments.isNotEmpty()) db.attachmentDao().insertAll(attachments)
            if (reminders.isNotEmpty()) db.reminderDao().insertAll(reminders)
        }
    }
}
