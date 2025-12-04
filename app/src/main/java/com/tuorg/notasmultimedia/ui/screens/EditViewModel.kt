package com.tuorg.notasmultimedia.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tuorg.notasmultimedia.data.NoteRepository
import com.tuorg.notasmultimedia.di.Graph
import com.tuorg.notasmultimedia.model.db.ItemType
import com.tuorg.notasmultimedia.model.db.NoteEntity
import com.tuorg.notasmultimedia.model.db.ReminderEntity
import com.tuorg.notasmultimedia.ui.common.AlarmScheduler
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

class EditViewModel : ViewModel() {

    var noteId: String = UUID.randomUUID().toString()
    var title: String = ""
    var description: String = ""
    var content: String = ""
    var type: ItemType = ItemType.NOTE
    var createdAt: LocalDateTime = LocalDateTime.now()
    var dueAt: LocalDateTime? = null
    var completed: Boolean = false

    fun save(repo: NoteRepository) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()

            val note = NoteEntity(
                id = noteId,
                title = title,
                content = content,
                type = type,
                updatedAt = now,
                isDeleted = false,
                dirty = false,
                description = description,
                createdAt = createdAt,
                dueAt = dueAt,
                completed = completed
            )

            // 1. Convertir la fecha
            val triggerMillis = dueAt?.atZone(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()

            // 2. Crear la lista de recordatorios
            val reminders = if (type == ItemType.TASK && triggerMillis != null) {
                listOf(ReminderEntity(
                    noteId = noteId,
                    triggerAt = triggerMillis
                ))
            } else {
                emptyList()
            }

            // 3. Guardar
            repo.upsertGraph(
                note = note,
                attachments = emptyList(),
                reminders = reminders
            )

            // 4. Programar la alarma
            if (reminders.isNotEmpty()) {
                try {
                    val scheduler = AlarmScheduler(Graph.appContext)
                    scheduler.schedule(reminders.first(), note.title)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}