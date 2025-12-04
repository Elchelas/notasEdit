@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.tuorg.notasmultimedia.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.tuorg.notasmultimedia.BuildConfig
import com.tuorg.notasmultimedia.model.db.AttachmentEntity
import com.tuorg.notasmultimedia.model.db.ItemType
import com.tuorg.notasmultimedia.nav.Routes
import java.io.File
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.Objects

@Composable
fun EditScreen(
    nav: NavController,
    noteId: String? = null,
    vm: NoteEditViewModel = viewModel(factory = NoteEditViewModel.provideFactory(noteId))
) {
    val ui by vm.state.collectAsState()
    val ctx = LocalContext.current
    var tempUri by remember { mutableStateOf<Uri?>(null) }

    // --- LÓGICA DE NAVEGACIÓN INTELIGENTE ---
    // Detecta si estamos en Móvil (pila normal) o Tablet (Dialog root)
    val goBack: () -> Unit = {
        if (nav.previousBackStackEntry != null) {
            nav.popBackStack()
        } else {
            // Si no hay atrás, estamos en el Dialog -> vamos a "done" para cerrar
            nav.navigate("done") {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    // --- Funciones y Launchers para Multimedia ---

    fun getUriForFile(context: Context, extension: String): Uri {
        val file = File.createTempFile("temp_media_${System.currentTimeMillis()}", extension, context.externalCacheDir)
        return FileProvider.getUriForFile(Objects.requireNonNull(context), BuildConfig.APPLICATION_ID + ".provider", file)
    }

    val takePhotoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) { tempUri?.let { vm.onMediaSelected(it, "image/jpeg") } }
    }

    val recordVideoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo()) { success ->
        if (success) { tempUri?.let { vm.onMediaSelected(it, "video/mp4") } }
    }

    val pickVisualMediaLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val mimeType = ctx.contentResolver.getType(uri) ?: "image/jpeg"
            vm.onMediaSelected(uri, mimeType)
        }
    }

    // --- Diálogos ---

    if (ui.showMediaPicker) {
        Dialog(onDismissRequest = { vm.showMediaPicker(false) }) {
            Surface(shape = MaterialTheme.shapes.large, tonalElevation = 8.dp) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Añadir multimedia", style = MaterialTheme.typography.titleLarge)
                    TextButton(modifier = Modifier.fillMaxWidth(), onClick = {
                        vm.showMediaPicker(false)
                        val uri = getUriForFile(ctx, ".jpg")
                        tempUri = uri
                        takePhotoLauncher.launch(uri)
                    }) { Text("Tomar foto") }
                    TextButton(modifier = Modifier.fillMaxWidth(), onClick = {
                        vm.showMediaPicker(false)
                        val uri = getUriForFile(ctx, ".mp4")
                        tempUri = uri
                        recordVideoLauncher.launch(uri)
                    }) { Text("Grabar video") }
                    TextButton(modifier = Modifier.fillMaxWidth(), onClick = {
                        vm.showMediaPicker(false)
                        pickVisualMediaLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                    }) { Text("Elegir de la galería") }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { vm.showMediaPicker(false) }) { Text("Cancelar") }
                    }
                }
            }
        }
    }

    val dueDate = ui.dueAt?.toLocalDate() ?: LocalDate.now()
    val dueTime = ui.dueAt?.toLocalTime() ?: LocalTime.of(9, 0)

    fun pickDate() {
        DatePickerDialog(ctx, { _, y, m, d ->
            val date = LocalDate.of(y, m + 1, d)
            val time = ui.dueAt?.toLocalTime() ?: LocalTime.of(9, 0)
            vm.setDue(LocalDateTime.of(date, time))
        }, dueDate.year, dueDate.monthValue - 1, dueDate.dayOfMonth).show()
    }

    fun pickTime() {
        TimePickerDialog(ctx, { _, h, min ->
            val date = ui.dueAt?.toLocalDate() ?: LocalDate.now()
            vm.setDue(LocalDateTime.of(date, LocalTime.of(h, min)))
        }, dueTime.hour, dueTime.minute, true).show()
    }

    // --- UI Principal ---
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(if (ui.isNewNote) "Nueva nota" else "Editar nota") },
                // USAMOS goBack() aquí
                navigationIcon = { TextButton(onClick = { goBack() }) { Text("Cancelar") } },
                // USAMOS goBack() en el callback de save
                actions = { TextButton(enabled = ui.title.isNotBlank(), onClick = { vm.save { goBack() } }) { Text("Guardar") } }
            )
        }
    ) { pads ->
        Column(
            Modifier
                .padding(pads)
                .padding(16.dp)
            // Hacemos scrollable si el contenido es largo (especialmente en horizontal)
            // .verticalScroll(rememberScrollState())
            ,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(value = ui.title, onValueChange = vm::setTitle, label = { Text("Título") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = ui.description, onValueChange = vm::setDescription, label = { Text("Descripción") }, minLines = 3, modifier = Modifier.fillMaxWidth())

            Text("Tipo", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                FilterChip(selected = ui.type == ItemType.TASK, onClick = { vm.setType(ItemType.TASK) }, label = { Text("Tarea") })
                FilterChip(selected = ui.type == ItemType.NOTE, onClick = { vm.setType(ItemType.NOTE) }, label = { Text("Nota") })
            }

            if (ui.type == ItemType.TASK) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ElevatedButton(onClick = ::pickDate) { Text("Fecha: $dueDate") }
                    ElevatedButton(onClick = ::pickTime) { Text("Hora: %02d:%02d".format(dueTime.hour, dueTime.minute)) }
                }
                Text("Repetir recordatorio:", style = MaterialTheme.typography.titleSmall)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = ui.recurringInterval == 0L,
                        onClick = { vm.setRecurring(0) },
                        label = { Text("Nunca") }
                    )
                    FilterChip(
                        selected = ui.recurringInterval == 5L,
                        onClick = { vm.setRecurring(5) },
                        label = { Text("5 min") }
                    )
                    FilterChip(
                        selected = ui.recurringInterval == 60L,
                        onClick = { vm.setRecurring(60) },
                        label = { Text("1 hora") }
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = ui.completed, onCheckedChange = vm::setCompleted)
                    Text("Marcar como completada")
                }
            }

            Text("Adjuntos", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ElevatedButton(onClick = { vm.showMediaPicker(true) }) { Text("+ Foto/Video") }
                ElevatedButton(onClick = { /* TODO */ }) { Text("+ Archivo") }
                ElevatedButton(onClick = { /* TODO */ }) { Text("+ Audio") }
            }

            Attachments(
                attachments = ui.attachments,
                onRemove = vm::onAttachmentRemoved,
                nav = nav
            )
        }
    }
}

@Composable
private fun Attachments(
    attachments: List<AttachmentEntity>,
    onRemove: (AttachmentEntity) -> Unit,
    nav: NavController,
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
        LazyRow(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(attachments) { attachment ->
                Box(contentAlignment = Alignment.TopEnd) {
                    AsyncImage(
                        model = attachment.uri,
                        contentDescription = attachment.description,
                        modifier = Modifier
                            .size(96.dp)
                            .clickable {
                                try {
                                    val encodedUri = URLEncoder.encode(attachment.uri, StandardCharsets.UTF_8.toString())
                                    // NOTA: En modo Tablet (Dialog), esta ruta debe existir en el NavHost temporal
                                    // o esta llamada lanzará excepción. Si falla, catch para evitar crash.
                                    nav.navigate("${Routes.MEDIA_VIEWER}/$encodedUri")
                                } catch (e: Exception) {
                                    // Fallback por seguridad
                                    e.printStackTrace()
                                }
                            }
                    )
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