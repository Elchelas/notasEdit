@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.tuorg.notasmultimedia.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.tuorg.notasmultimedia.model.db.AttachmentEntity
import com.tuorg.notasmultimedia.model.db.AttachmentType
import com.tuorg.notasmultimedia.model.db.ItemType
import com.tuorg.notasmultimedia.nav.Routes
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@Composable
fun EditScreen(
    navController: NavController,
    noteId: String? = null,
    viewModel: NoteEditViewModel = viewModel(factory = NoteEditViewModel.provideFactory(noteId))
) {
    val ui by viewModel.state.collectAsState()
    val ctx = LocalContext.current

    // --- Screen is now "dumb" and only reacts to ViewModel state ---

    // Launcher for taking a picture
    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
        onResult = { success -> viewModel.onMediaCaptured(success) }
    )

    // Launcher for recording a video
    val recordVideoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CaptureVideo(),
        onResult = { success -> viewModel.onMediaCaptured(success) }
    )

    val pickVisualMediaLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val mimeType = ctx.contentResolver.getType(uri) ?: "image/jpeg"
            viewModel.onMediaSelected(uri, mimeType)
        }
    }
    val pickFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val mime = ctx.contentResolver.getType(uri) ?: "application/octet-stream"
            viewModel.onMediaSelected(uri, mime)
        }
    }

    val pickAudioLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) viewModel.onMediaSelected(uri, "audio/mpeg")
    }

    // This effect listens for changes in the ViewModel state to launch the camera
    LaunchedEffect(ui.captureMediaAction, ui.tempFileUri) {
        val action = ui.captureMediaAction
        val tempFileUri = ui.tempFileUri

        if (action != null && tempFileUri != null) {
            when (action) {
                CaptureMediaAction.PHOTO -> takePictureLauncher.launch(tempFileUri)
                CaptureMediaAction.VIDEO -> recordVideoLauncher.launch(tempFileUri)
            }
        }
    }

    // --- Dialogs ---

    if (ui.showMediaPicker) {
        Dialog(onDismissRequest = { viewModel.showMediaPicker(false) }) {
            Surface(shape = MaterialTheme.shapes.large, tonalElevation = 8.dp) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Añadir multimedia", style = MaterialTheme.typography.titleLarge)
                    // Buttons now just notify the ViewModel of the user's intent
                    TextButton(modifier = Modifier.fillMaxWidth(), onClick = {
                        viewModel.showMediaPicker(false)
                        viewModel.prepareToCaptureMedia(CaptureMediaAction.PHOTO)
                    }) { Text("Tomar foto") }
                    TextButton(modifier = Modifier.fillMaxWidth(), onClick = {
                        viewModel.showMediaPicker(false)
                        viewModel.prepareToCaptureMedia(CaptureMediaAction.VIDEO)
                    }) { Text("Grabar video") }
                    TextButton(modifier = Modifier.fillMaxWidth(), onClick = {
                        viewModel.showMediaPicker(false)
                        pickVisualMediaLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                    }) { Text("Elegir de la galería") }
                    TextButton(modifier = Modifier.fillMaxWidth(), onClick = {
                        viewModel.showMediaPicker(false)
                        pickAudioLauncher.launch("audio/*")
                    }) { Text("Audio") }

                    TextButton(modifier = Modifier.fillMaxWidth(), onClick = {
                        viewModel.showMediaPicker(false)
                        pickFileLauncher.launch(arrayOf("application/*", "text/*"))
                    }) { Text("Archivo (PDF, Doc...)") }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { viewModel.showMediaPicker(false) }) { Text("Cancelar") }
                    }
                }
            }
        }
    }

    val goBack: () -> Unit = {
        if (navController.previousBackStackEntry != null) {
            navController.popBackStack()
        } else {
            navController.navigate("done") { popUpTo(0) { inclusive = true } }
        }
    }

    val dueDate = ui.dueAt?.toLocalDate() ?: LocalDate.now()
    val dueTime = ui.dueAt?.toLocalTime() ?: LocalTime.of(9, 0)

    fun pickDate() {
        DatePickerDialog(ctx, { _, y, m, d ->
            val date = LocalDate.of(y, m + 1, d)
            val time = ui.dueAt?.toLocalTime() ?: LocalTime.of(9, 0)
            viewModel.setDue(LocalDateTime.of(date, time))
        }, dueDate.year, dueDate.monthValue - 1, dueDate.dayOfMonth).show()
    }

    fun pickTime() {
        TimePickerDialog(ctx, { _, h, min ->
            val date = ui.dueAt?.toLocalDate() ?: LocalDate.now()
            viewModel.setDue(LocalDateTime.of(date, LocalTime.of(h, min)))
        }, dueTime.hour, dueTime.minute, true).show()
    }

    // --- Main UI ---
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(if (ui.isNewNote) "Nueva nota" else "Editar nota") },
                navigationIcon = { TextButton(onClick = goBack) { Text("Cancelar") } },
                actions = { TextButton(enabled = ui.title.isNotBlank(), onClick = { viewModel.save { goBack() } }) { Text("Guardar") } }
            )
        }
    ) { pads ->
        Column(
            Modifier
                .padding(pads)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(value = ui.title, onValueChange = viewModel::setTitle, label = { Text("Título") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = ui.description, onValueChange = viewModel::setDescription, label = { Text("Descripción") }, minLines = 3, modifier = Modifier.fillMaxWidth())

            Text("Tipo", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                FilterChip(selected = ui.type == ItemType.TASK, onClick = { viewModel.setType(ItemType.TASK) }, label = { Text("Tarea") })
                FilterChip(selected = ui.type == ItemType.NOTE, onClick = { viewModel.setType(ItemType.NOTE) }, label = { Text("Nota") })
            }

            if (ui.type == ItemType.TASK) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ElevatedButton(onClick = ::pickDate) { Text("Fecha: $dueDate") }
                    ElevatedButton(onClick = ::pickTime) { Text("Hora: %02d:%02d".format(dueTime.hour, dueTime.minute)) }
                }
                Text("Repetir recordatorio:", style = MaterialTheme.typography.titleSmall)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = ui.recurringInterval == 0L, onClick = { viewModel.setRecurring(0) }, label = { Text("Nunca") })
                    FilterChip(selected = ui.recurringInterval == 5L, onClick = { viewModel.setRecurring(5) }, label = { Text("5 min") })
                    FilterChip(selected = ui.recurringInterval == 60L, onClick = { viewModel.setRecurring(60) }, label = { Text("1 hora") })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = ui.completed, onCheckedChange = viewModel::setCompleted)
                    Text("Marcar como completada")
                }
            }

            Text("Adjuntos", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ElevatedButton(onClick = { viewModel.showMediaPicker(true) }) { Text("+ Multimedia/archivos") }
            }

            Attachments(
                attachments = ui.attachments,
                onRemove = viewModel::onAttachmentRemoved,
                navController = navController
            )
        }
    }
}

@Composable
private fun Attachments(
    attachments: List<AttachmentEntity>,
    onRemove: (AttachmentEntity) -> Unit,
    navController: NavController,
    modifier: Modifier = Modifier
) {
    if (attachments.isEmpty()) {
        Text(
            text = "No hay adjuntos",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.padding(vertical = 16.dp)
        )
    } else {
        LazyRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(attachments) { attachment ->
                Box(contentAlignment = Alignment.TopEnd) {
                    val encodedUri = URLEncoder.encode(attachment.uri, StandardCharsets.UTF_8.toString())
                    val clickableModifier = Modifier.clickable { navController.navigate(Routes.MEDIA_VIEWER.replace("{mediaUri}", encodedUri)) }

                    if (attachment.type == AttachmentType.IMAGE || attachment.type == AttachmentType.VIDEO) {
                        AsyncImage(
                            model = attachment.uri,
                            contentDescription = null,
                            modifier = Modifier
                                .size(96.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .then(clickableModifier)
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .size(96.dp)
                                .background(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.shapes.small)
                                .then(clickableModifier),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = if (attachment.type == AttachmentType.AUDIO) Icons.Default.AudioFile else Icons.Default.InsertDriveFile,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            if (!attachment.description.isNullOrBlank()) {
                                Text(
                                    text = attachment.description,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )
                            }
                        }
                    }
                    IconButton(
                        onClick = { onRemove(attachment) },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Eliminar adjunto",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f), CircleShape)
                                .padding(4.dp)
                        )
                    }
                }
            }
        }
    }
}
