package com.tuorg.notasmultimedia.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tuorg.notasmultimedia.BuildConfig
import com.tuorg.notasmultimedia.di.Graph
import com.tuorg.notasmultimedia.model.db.AttachmentEntity
import com.tuorg.notasmultimedia.model.db.AttachmentType
import com.tuorg.notasmultimedia.model.db.ItemType
import com.tuorg.notasmultimedia.model.db.NoteEntity
import com.tuorg.notasmultimedia.model.db.ReminderEntity
import com.tuorg.notasmultimedia.ui.common.AlarmScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime
import java.util.UUID

enum class CaptureMediaAction {
    PHOTO, VIDEO
}

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
    val recurringInterval: Long = 0,
    // New state for camera actions
    val captureMediaAction: CaptureMediaAction? = null,
    val tempFileUri: Uri? = null
)

class NoteEditViewModel(
    noteId: String?,
) : ViewModel() {

    private val repo = Graph.notes
    private val contentResolver = Graph.contentResolver


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

    // --- Start of New/Modified Media Logic ---

    fun prepareToCaptureMedia(action: CaptureMediaAction) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = Graph.appContext
            val extension = when (action) {
                CaptureMediaAction.PHOTO -> ".jpg"
                CaptureMediaAction.VIDEO -> ".mp4"
            }
            // Create a file in the external cache directory
            val file = File.createTempFile("capture_", extension, context.externalCacheDir)
            // Get a content URI for the file using our FileProvider
            val uri = FileProvider.getUriForFile(
                context,
                "${BuildConfig.APPLICATION_ID}.provider", // Authority must match AndroidManifest
                file
            )

            // Update the state on the main thread to trigger the UI
            withContext(Dispatchers.Main) {
                _state.value = _state.value.copy(
                    captureMediaAction = action,
                    tempFileUri = uri
                )
            }
        }
    }

    fun onMediaCaptured(success: Boolean) {
        val capturedUri = _state.value.tempFileUri
        val action = _state.value.captureMediaAction

        // Reset state immediately to prevent re-triggering the camera on config change
        _state.value = _state.value.copy(
            captureMediaAction = null,
            tempFileUri = null
        )

        if (success && capturedUri != null) {
            val mimeType = when (action) {
                CaptureMediaAction.PHOTO -> "image/jpeg"
                CaptureMediaAction.VIDEO -> "video/mp4"
                else -> null
            }
            // Now that the file is captured, treat it like any other selected media
            onMediaSelected(capturedUri, mimeType)
        }
    }

    fun onMediaSelected(uri: Uri, mimeType: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Only take persistable permission for URIs from external providers (e.g., Gallery).
                // URIs from our own FileProvider don't need this and will cause a crash if we try.
                if (uri.scheme == "content" && uri.authority != "${BuildConfig.APPLICATION_ID}.provider") {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            } catch (e: SecurityException) {
                // Log the error, the user might have picked a file from a strange provider
                e.printStackTrace()
            }

            val attachmentType = when {
                mimeType?.startsWith("image") == true -> AttachmentType.IMAGE
                mimeType?.startsWith("video") == true -> AttachmentType.VIDEO
                mimeType?.startsWith("audio") == true -> AttachmentType.AUDIO
                else -> AttachmentType.FILE
            }

            var fileName: String? = null
            if (attachmentType == AttachmentType.FILE || attachmentType == AttachmentType.AUDIO) {
                fileName = getFileNameFromUri(contentResolver, uri)
            }
            val newAttachment = AttachmentEntity(
                noteId = stableNoteId,
                type = attachmentType,
                uri = uri.toString(),
                description = fileName
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

    // --- End of New/Modified Media Logic ---


    private fun getFileNameFromUri(resolver: android.content.ContentResolver, uri: Uri): String? {
        var name: String? = null
        val cursor = resolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    name = it.getString(index)
                }
            }
        }
        return name
    }

    fun save(onSaved: () -> Unit) {

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
