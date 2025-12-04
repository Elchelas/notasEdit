package com.tuorg.notasmultimedia.data.sync

import com.tuorg.notasmultimedia.data.remote.AttachmentDTO
import com.tuorg.notasmultimedia.data.remote.NoteDTO
import com.tuorg.notasmultimedia.data.remote.NoteGraphDTO
import com.tuorg.notasmultimedia.data.remote.ReminderDTO
import com.tuorg.notasmultimedia.model.db.AttachmentEntity
import com.tuorg.notasmultimedia.model.db.ItemType
import com.tuorg.notasmultimedia.model.db.NoteEntity
import com.tuorg.notasmultimedia.model.db.ReminderEntity
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId



data class NoteSlim(
    val id: String,
    val title: String,
    val content: String,
    val type: String,
    val updatedAt: Long,
    val isDeleted: Boolean
)

data class AttachmentSlim(
    val id: String,
    val noteId: String,
    val uri: String
)

data class ReminderSlim(
    val id: String,
    val noteId: String,
    val at: Long
)

data class NoteGraphPayload(
    val note: NoteSlim,
    val attachments: List<AttachmentSlim>,
    val reminders: List<ReminderSlim>
) {
    fun toJson(): String {
        val root = JSONObject()

        val n = JSONObject()
            .put("id", note.id)
            .put("title", note.title)
            .put("content", note.content)
            .put("type", note.type)
            .put("updatedAt", note.updatedAt)
            .put("isDeleted", note.isDeleted)
        root.put("note", n)

        // Enviamos listas vacías por ahora (evita dependencias de nombres de campos)
        root.put("attachments", JSONArray())
        root.put("reminders", JSONArray())

        return root.toString()
    }

    companion object {
        fun fromJson(s: String): NoteGraphPayload {
            val root = JSONObject(s)
            val n = root.getJSONObject("note")
            val note = NoteSlim(
                id = n.getString("id"),
                title = n.getString("title"),
                content = n.getString("content"),
                type = n.getString("type"),
                updatedAt = n.getLong("updatedAt"),
                isDeleted = n.getBoolean("isDeleted")
            )
            return NoteGraphPayload(note, emptyList(), emptyList())
        }

        fun fromEntities(
            note: NoteEntity,
            attachments: List<AttachmentEntity>,
            reminders: List<ReminderEntity>
        ): NoteGraphPayload {
            return NoteGraphPayload(
                note = NoteSlim(
                    id = note.id,
                    title = note.title,
                    content = note.content,
                    type = note.type.name,
                    updatedAt = note.updatedAt,
                    isDeleted = note.isDeleted
                ),
                attachments = emptyList(),
                reminders = emptyList()
            )
        }
    }
}

/* ---------- Mappers a DTO remoto; válidos aunque las listas vayan vacías ---------- */

private fun NoteSlim.toDTO(): NoteDTO = NoteDTO(
    id = id,
    title = title,
    content = content,
    type = type,
    updatedAt = updatedAt,
    isDeleted = isDeleted
)

fun NoteGraphPayload.toDto(): NoteGraphDTO = NoteGraphDTO(
    note = note.toDTO(),
    attachments = attachments.map { AttachmentDTO(id = it.id, noteId = it.noteId, uri = it.uri) },
    reminders = reminders.map { ReminderDTO(id = it.id, noteId = it.noteId, at = it.at) }
)

/* ---------- Helper inverso si haces pull remoto ---------- */
/**
 *  NoteEntity tiene los campos:
 *  id, title, content, type, updatedAt, isDeleted, dirty, description, createdAt, dueAt, completed
 *
 * Como el DTO remoto no trae description/createdAt/dueAt/completed, aquí:
 *  - conservamos los valores del registro local (si lo pasas en existing),
 *  - y si no hay existing, usamos defaults razonables.
 */
fun NoteDTO.toEntity(existing: NoteEntity? = null): NoteEntity {
    val nowLdt: LocalDateTime = millisToLdt(updatedAt)

    return NoteEntity(
        id = id,
        title = title,
        content = content,
        type = ItemType.valueOf(type),
        updatedAt = updatedAt,
        isDeleted = isDeleted,
        dirty = false,
        description = existing?.description ?: "",
        createdAt = existing?.createdAt ?: nowLdt,
        dueAt = existing?.dueAt,
        completed = existing?.completed ?: false
    )
}

/* ---------- Utils time ---------- */
private fun millisToLdt(millis: Long): LocalDateTime =
    LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault())
