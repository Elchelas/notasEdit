package com.tuorg.notasmultimedia.data.remote

data class NoteDTO(
    val id: String,
    val title: String,
    val content: String,
    val type: String,
    val updatedAt: Long,
    val isDeleted: Boolean
)

data class NoteGraphDTO(
    val note: NoteDTO,
    val attachments: List<AttachmentDTO>,
    val reminders: List<ReminderDTO>
)

data class AttachmentDTO(val id: String, val noteId: String, val uri: String)
data class ReminderDTO(val id: String, val noteId: String, val at: Long)
