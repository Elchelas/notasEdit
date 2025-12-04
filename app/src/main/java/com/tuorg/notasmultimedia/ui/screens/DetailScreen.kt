@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.tuorg.notasmultimedia.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.tuorg.notasmultimedia.model.db.ItemType
import androidx.compose.material3.ExperimentalMaterial3Api
import com.tuorg.notasmultimedia.model.db.AttachmentType
import com.tuorg.notasmultimedia.model.db.AttachmentEntity
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import coil.compose.AsyncImage
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.ui.graphics.Color
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import androidx.compose.ui.text.style.TextOverflow

// ===============
//  MÓVIL (igual)
// ===============
@Composable
fun DetailScreen(
    nav: NavController,
    id: String,
    vm: NoteDetailViewModel = viewModel(factory = NoteDetailViewModel.provideFactory(id))
) {
    DetailScaffold(
        id = id,
        vm = vm,
        onEdit = { nav.navigate("edit/$id") },
        onDeleted = { nav.popBackStack() },
        // Acción al hacer clic en un adjunto (Móvil navega directo)
        onViewAttachment = { uri ->
            try {
                val encodedUri = URLEncoder.encode(uri, StandardCharsets.UTF_8.toString())
                nav.navigate("viewer/$encodedUri")
            } catch (e: Exception) { e.printStackTrace() }
        }
    )
}

// ================================
//  TABLET: detalle autónomo (panel)
// ================================
@Composable
fun DetailScreenStandalone(
    id: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onViewAttachment: (String) -> Unit, // <--- NUEVO CALLBACK
    vm: NoteDetailViewModel = viewModel(factory = NoteDetailViewModel.provideFactory(id))
) {
    DetailScaffold(
        id = id,
        vm = vm,
        onEdit = onEdit,
        onDeleted = onDelete,
        onViewAttachment = onViewAttachment // <--- Conectamos
    )
}

@Composable
fun TabletDetailPlaceholder() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Selecciona una nota o tarea", style = MaterialTheme.typography.titleMedium)
    }
}

// ================================
//  UI compartida
// ================================
@Composable
private fun DetailScaffold(
    id: String,
    vm: NoteDetailViewModel,
    onEdit: () -> Unit,
    onDeleted: () -> Unit,
    onViewAttachment: (String) -> Unit // <--- Recibimos la acción
) {
    val noteWithR by vm.note.collectAsState()
    var showConfirm by remember { mutableStateOf(false) }
    val n = noteWithR?.note
    // Obtenemos la lista de adjuntos (o vacía si es null)
    val attachments = noteWithR?.attachments ?: emptyList()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Detalle") },
                actions = {
                    TextButton(onClick = onEdit) { Text("Editar") }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { showConfirm = true }) { Text("Eliminar") }
                }
            )
        }
    ) { pads ->
        Column(
            modifier = Modifier.padding(pads).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (n == null) {
                Text("Cargando…")
            } else {
                Text("Título: ${n.title}", style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = if (n.type == ItemType.TASK) "Fecha objetivo: ${n.dueAt ?: "Sin fecha"}" else "Nota personal",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                HorizontalDivider()
                Text(n.description.ifBlank { "Sin descripción" }, style = MaterialTheme.typography.bodyLarge)

                // --- AQUÍ MOSTRAMOS LOS ADJUNTOS ---
                if (attachments.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Text("Adjuntos:", style = MaterialTheme.typography.titleSmall)
                    ReadOnlyAttachmentsList(
                        attachments = attachments,
                        onItemClick = onViewAttachment
                    )
                }
            }
        }
    }

    if (showConfirm) {
        // ... (Tu código del diálogo de borrar se queda igual) ...
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("Eliminar") },
            text = { Text("¿Seguro que deseas eliminar?") },
            confirmButton = {
                TextButton(onClick = {
                    showConfirm = false
                    vm.delete { onDeleted() }
                }) { Text("Sí, eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) { Text("Cancelar") }
            }
        )
    }
}
@Composable
fun ReadOnlyAttachmentsList(
    attachments: List<AttachmentEntity>,
    onItemClick: (String) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(attachments) { attachment ->
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(100.dp) // Un poco más grandes para ver bien
                    .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
                    .clickable { onItemClick(attachment.uri) } // Al clic, abrimos visor
            ) {
                if (attachment.type == AttachmentType.IMAGE || attachment.type == AttachmentType.VIDEO) {
                    AsyncImage(
                        model = attachment.uri,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize()
                    )
                    if (attachment.type == AttachmentType.VIDEO) {
                        // Icono de Play encima para saber que es video
                        Icon(Icons.Default.PlayCircle, contentDescription = null, tint = Color.White.copy(alpha=0.8f), modifier = Modifier.size(32.dp))
                    }
                } else {
                    // Audio o Archivo
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(8.dp)
                    ) {
                        Icon(
                            imageVector = if (attachment.type == AttachmentType.AUDIO) Icons.Default.AudioFile else Icons.Default.InsertDriveFile,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                        if (!attachment.description.isNullOrBlank()) {
                            Text(
                                text = attachment.description,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}