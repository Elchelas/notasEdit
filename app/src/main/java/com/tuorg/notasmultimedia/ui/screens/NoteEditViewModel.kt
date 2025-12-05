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
import android.media.MediaRecorder
import android.os.Build
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
    val reminders: List<ReminderEntity> = emptyList(),
    val showMediaPicker: Boolean = false,
    val isRecording: Boolean = false,
    val recurringInterval: Long = 0,
    val captureMediaAction: CaptureMediaAction? = null,
    val tempFileUri: Uri? = null
)
enum class TimeUnit(val label: String, val minutes: Long) {
    MINUTES("Min", 1),
    HOURS("Horas", 60),
    DAYS("Días", 1440) // 24 * 60
}
class NoteEditViewModel(
    noteId: String?,
) : ViewModel() {

    private val repo = Graph.notes
    private val contentResolver = Graph.contentResolver
    private val stableNoteId = noteId ?: UUID.randomUUID().toString()
    private val _state = MutableStateFlow(EditUiState(id = stableNoteId, isNewNote = noteId == null))

    private var mediaRecorder: MediaRecorder? = null
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
                        reminders = nwr.reminders
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

    /**
     * Genera N recordatorios hacia atrás.
     * @param count Cantidad de avisos.
     * @param interval Valor del intervalo (ej. 2).
     * @param unit Unidad de tiempo (Minutos, Horas, Días).
     */
    fun generatePreDeadlineReminders(count: Int, interval: Int, unit: TimeUnit) {
        val deadline = _state.value.dueAt ?: return

        val newReminders = mutableListOf<ReminderEntity>()
        val zoneId = java.time.ZoneId.systemDefault()
        val deadlineMillis = deadline.atZone(zoneId).toInstant().toEpochMilli()

        // Un minuto en milisegundos
        val oneMinuteMillis = 60 * 1000L

        for (i in 1..count) {
            val minutesOffset = i * interval * unit.minutes
            val totalMillisOffset = minutesOffset * oneMinuteMillis

            val triggerTime = deadlineMillis - totalMillisOffset

            // Solo agregamos si es futuro
            if (triggerTime > System.currentTimeMillis()) {
                newReminders.add(
                    ReminderEntity(
                        noteId = stableNoteId,
                        triggerAt = triggerTime,
                        isRecurring = false,
                        intervalMinutes = 0
                    )
                )
            }
        }

        // Ordenamos para que queden cronológicos
        val sortedReminders = newReminders.sortedBy { it.triggerAt }

        _state.value = _state.value.copy(reminders = sortedReminders)
    }

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
                "${BuildConfig.APPLICATION_ID}.provider",
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

    fun startRecording() {
        viewModelScope.launch(Dispatchers.IO) {
            val context = Graph.appContext
            // Crear archivo temporal .mp3 o .m4a
            val file = File.createTempFile("audio_rec_", ".mp3", context.externalCacheDir)
            val uri = FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.provider", file)

            withContext(Dispatchers.Main) {
                try {
                    // Inicializar Recorder según versión de Android
                    mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        MediaRecorder(context)
                    } else {
                        @Suppress("DEPRECATION")
                        MediaRecorder()
                    }.apply {
                        setAudioSource(MediaRecorder.AudioSource.MIC)
                        setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                        setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                        setOutputFile(file.absolutePath)
                        prepare()
                        start()
                    }

                    // Actualizar estado para mostrar UI de "Grabando..."
                    _state.value = _state.value.copy(
                        isRecording = true,
                        tempFileUri = uri
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                    _state.value = _state.value.copy(isRecording = false)
                }
            }
        }
    }

    fun stopRecording() {
        try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        mediaRecorder = null

        // Guardar el adjunto
        val uri = _state.value.tempFileUri
        if (uri != null) {
            onMediaSelected(uri, "audio/mpeg")
        }

        // Restaurar estado
        _state.value = _state.value.copy(isRecording = false, tempFileUri = null, showMediaPicker = false)
    }

    fun cancelRecording() {
        try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
        } catch (e: Exception) { e.printStackTrace() }
        mediaRecorder = null
        _state.value = _state.value.copy(isRecording = false, tempFileUri = null)
    }

    override fun onCleared() {
        super.onCleared()
        mediaRecorder?.release()
        mediaRecorder = null
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

    /**
     * Genera N recordatorios hacia atrás desde la fecha límite.
     * @param count Cantidad de avisos (ej. 3 avisos).
     * @param intervalMinutes Minutos entre cada aviso (ej. 15 min).
     */
    fun generatePreDeadlineReminders(count: Int, intervalMinutes: Int) {
        val deadline = _state.value.dueAt ?: return

        val newReminders = mutableListOf<ReminderEntity>()

        val zoneId = java.time.ZoneId.systemDefault()
        val deadlineMillis = deadline.atZone(zoneId).toInstant().toEpochMilli()
        val oneMinuteMillis = 60 * 1000

        for (i in 1..count) {
            // Calculamos el tiempo hacia atrás: Deadline - (i * intervalo)
            val timeOffset = i * intervalMinutes * oneMinuteMillis
            val triggerTime = deadlineMillis - timeOffset

            // Solo agregamos si el aviso es en el futuro (para no sonar de golpe si ya pasó)
            if (triggerTime > System.currentTimeMillis()) {
                newReminders.add(
                    ReminderEntity(
                        noteId = stableNoteId,
                        triggerAt = triggerTime,
                        isRecurring = false,
                        intervalMinutes = 0
                    )
                )
            }
        }

        // Actualizamos el estado reemplazando los recordatorios anteriores
        _state.value = _state.value.copy(reminders = newReminders)
    }
    fun save(onSaved: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val s = _state.value
            val now = LocalDateTime.now()
            val id = s.id

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

            // 1. Decidir qué recordatorios guardar
            val finalReminders = if (s.reminders.isNotEmpty()) {
                // Si el usuario generó avisos múltiples, usamos esos
                s.reminders
            } else if (s.type == ItemType.TASK && s.dueAt != null) {
                // Si no, creamos uno básico para la fecha límite
                val triggerMillis = s.dueAt.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                listOf(ReminderEntity(
                    noteId = id,
                    triggerAt = triggerMillis,
                    isRecurring = s.recurringInterval > 0,
                    intervalMinutes = s.recurringInterval
                ))
            } else {
                emptyList()
            }

            // 2. Guardar en BD
            repo.upsertGraph(note = entity, attachments = s.attachments, reminders = finalReminders)

            // 3. Obtener los datos reales (con IDs generados)
            val savedData = repo.byId(id).firstOrNull()
            val realReminders = savedData?.reminders ?: emptyList()

            // 4. Programar TODAS las alarmas en Android
            if (realReminders.isNotEmpty()) {
                try {
                    val scheduler = AlarmScheduler(Graph.appContext)
                    realReminders.forEach { reminder ->
                        scheduler.schedule(reminder, entity.title)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

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
