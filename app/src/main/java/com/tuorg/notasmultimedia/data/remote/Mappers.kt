package com.tuorg.notasmultimedia.data.remote

import com.tuorg.notasmultimedia.model.db.ItemType
import com.tuorg.notasmultimedia.model.db.NoteEntity
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

// ---------- NoteEntity -> NoteDTO (para push) ----------
fun NoteEntity.toDTO(): NoteDTO = NoteDTO(
    id = id,
    title = title,
    content = content,
    type = type.name,
    updatedAt = updatedAt,
    isDeleted = isDeleted
)

// ---------- NoteDTO -> NoteEntity (para pull) ----------
fun NoteDTO.toEntity(existing: NoteEntity? = null): NoteEntity {
    val inferredCreatedAt: LocalDateTime = millisToLdt(updatedAt)

    return NoteEntity(
        id = id,
        title = title,
        content = content,
        type = ItemType.valueOf(type),
        updatedAt = updatedAt,
        isDeleted = isDeleted,

        // Al venir del servidor, el registro NO está "sucio"
        dirty = false,

        // Campos que tu DTO no incluye:
        description = existing?.description ?: "",
        createdAt   = existing?.createdAt ?: inferredCreatedAt,
        dueAt       = existing?.dueAt,            // conserva si ya existía
        completed   = existing?.completed ?: false
    )
}

// ---------- Utils tiempo ----------
private fun millisToLdt(millis: Long): LocalDateTime =
    LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault())
