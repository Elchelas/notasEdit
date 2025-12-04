package com.tuorg.notasmultimedia.ui.screens

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tuorg.notasmultimedia.di.Graph
import com.tuorg.notasmultimedia.model.db.AttachmentEntity
import com.tuorg.notasmultimedia.model.db.AttachmentType
import com.tuorg.notasmultimedia.model.db.ItemType
import com.tuorg.notasmultimedia.model.db.NoteEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.util.UUID
import android.content.Intent
import com.tuorg.notasmultimedia.model.db.ReminderEntity
import com.tuorg.notasmultimedia.ui.common.AlarmScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class EditUiState(
    val id: String,
    val isNewNote: Boolean = true,
    val title: String = "",
    val description: String = "",
    val content: String = "",
    val type: ItemType = ItemType.NOTE,
    val createdAt: LocalDateTime? = null,
    val dueAt: LocalDateTime? = null,
    val completed: Boolean = false,
    val attachments: List<AttachmentEntity> = emptyList(),
    val showMediaPicker: Boolean = false,
    val recurringInterval: Long = 0
)

class NoteEditViewModel(
    noteId: String?, // ID que llega desde la navegación, puede ser null
) : ViewModel() {

    private val repo = Graph.notes
    private val contentResolver = Graph.contentResolver

    // ID estable para la sesión de edición. O el que llega, o uno nuevo.
    private val stableNoteId = noteId ?: UUID.randomUUID().toString()

    private val _state = MutableStateFlow(EditUiState(id = stableNoteId, isNewNote = noteId == null))
    val state = _state.asStateFlow()
    fun setRecurring(minutes: Long) { _state.value = _state.value.copy(recurringInterval = minutes) }
    init {
        // Si el noteId NO es nulo, significa que estamos editando una nota existente.
        // Por lo tanto, cargamos sus datos.
        if (noteId != null) {
            viewModelScope.launch {
                repo.byId(noteId).firstOrNull()?.let { nwr ->
                    val n = nwr.note
                    _state.value = EditUiState(
                        id = n.id,
                        isNewNote = false,
                        title = n.title,
                        description = n.description,
                        content = n.content,
                        type = n.type,
                        createdAt = n.createdAt,
                        dueAt = n.dueAt,
                        completed = n.completed,
                        attachments = nwr.attachments,
                    )
                }
            }
        }
    }

    fun setTitle(v: String)       { _state.value = _state.value.copy(title = v) }
    fun setDescription(v: String) { _state.value = _state.value.copy(description = v) }
    fun setContent(v: String)     { _state.value = _state.value.copy(content = v) }
    fun setType(t: ItemType)      { _state.value = _state.value.copy(type = t) }
    fun setDue(dt: LocalDateTime?){ _state.value = _state.value.copy(dueAt = dt) }
    fun setCompleted(c: Boolean)  { _state.value = _state.value.copy(completed = c) }

    fun showMediaPicker(show: Boolean) { _state.value = _state.value.copy(showMediaPicker = show) }

    fun onMediaSelected(uri: Uri, mimeType: String?) {
        viewModelScope.launch(Dispatchers.IO) { // <--- CAMBIO: Ejecutar en hilo de IO
            try {
                // Esto es una operación de disco/base de datos, debe ir en IO
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            val attachmentType = when {
                mimeType?.startsWith("image") == true -> AttachmentType.IMAGE
                mimeType?.startsWith("video") == true -> AttachmentType.VIDEO
                else -> return@launch
            }

            val newAttachment = AttachmentEntity(
                noteId = stableNoteId,
                type = attachmentType,
                uri = uri.toString(),
                description = null
            )

            // Actualizamos el estado en el hilo principal
            withContext(Dispatchers.Main) {
                _state.value = _state.value.copy(
                    attachments = _state.value.attachments + newAttachment,
                    showMediaPicker = false
                )
            }
        }
    }

    fun save(onSaved: () -> Unit) {
        // CAMBIO IMPORTANTE: Usar Dispatchers.IO para todo el proceso de guardado
        viewModelScope.launch(Dispatchers.IO) {
            val s = _state.value
            val now = LocalDateTime.now()
            val id = s.id // Ya no es nullable gracias a tu lógica

            // ... (creación de NoteEntity igual que antes) ...
            val entity = NoteEntity(
                id = id,
                title = s.title.trim(),
                content = s.content.trim(),
                type = s.type,
                updatedAt = System.currentTimeMillis(),
                isDeleted = false,
                dirty = false,
                description = s.description.trim(),
                createdAt = s.createdAt ?: now,
                dueAt = if (s.type == ItemType.TASK) s.dueAt else null,
                completed = if (s.type == ItemType.TASK) s.completed else false
            )

            // ... (lógica de reminders igual que antes) ...
            val triggerMillis = s.dueAt?.atZone(java.time.ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
            val reminders = if (s.type == ItemType.TASK && triggerMillis != null) {
                listOf(ReminderEntity(
                    noteId = id,
                    triggerAt = triggerMillis,
                    isRecurring = s.recurringInterval > 0,
                    intervalMinutes = s.recurringInterval
                ))
            } else {
                emptyList()
            }

            // 4. Guardar (Ahora es seguro porque estamos en IO)
            repo.upsertGraph(note = entity, attachments = s.attachments, reminders = reminders)

            val savedData = repo.byId(id).firstOrNull()
            val realReminders = savedData?.reminders ?: emptyList()

            // 5. Programar Alarmas
            if (realReminders.isNotEmpty()) {
                try {
                    val scheduler = AlarmScheduler(Graph.appContext)
                    scheduler.schedule(realReminders.first(), entity.title)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // 6. Volver al hilo principal para navegar
            withContext(Dispatchers.Main) {
                onSaved()
            }
        }
    }
    fun onAttachmentRemoved(attachment: AttachmentEntity) {
        _state.value = _state.value.copy(attachments = _state.value.attachments.filterNot { it.uri == attachment.uri })
    }

    companion object {
        fun provideFactory(noteId: String?): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return NoteEditViewModel(noteId) as T
                }
            }
    }
}
