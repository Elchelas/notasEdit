package com.tuorg.notasmultimedia.model

import java.time.LocalDateTime

enum class ItemType { NOTE, TASK }
enum class AttachmentType { IMAGE, VIDEO, AUDIO, FILE }

data class Attachment(
    val id: String,
    val type: AttachmentType,
    val description: String? = null
)

data class NoteItem(
    val id: String,
    val title: String,
    val description: String,
    val type: ItemType,
    val createdAt: LocalDateTime,
    val dueAt: LocalDateTime? = null,
    val reminders: List<LocalDateTime> = emptyList(),
    val attachments: List<Attachment> = emptyList()
)
