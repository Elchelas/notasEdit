package com.tuorg.notasmultimedia.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

@Composable
fun MediaViewerScreen(uri: String, onBackPressed: () -> Unit) {
    val context = LocalContext.current
    // Decodificamos la URI que llega como argumento de navegación
    val decodedUri = remember { Uri.parse(URLDecoder.decode(uri, StandardCharsets.UTF_8.name())) }
    val mimeType = remember { context.contentResolver.getType(decodedUri) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        // Lógica para mostrar Video o Imagen
        when {
            mimeType?.startsWith("video/") == true -> {
                val exoPlayer = remember {
                    ExoPlayer.Builder(context).build().apply {
                        setMediaItem(MediaItem.fromUri(decodedUri))
                        prepare()
                        playWhenReady = true // Auto-play
                    }
                }

                // Usamos AndroidView para hostear la vista clásica de PlayerView
                AndroidView(
                    factory = { PlayerView(it).apply { player = exoPlayer } },
                    modifier = Modifier.fillMaxSize()
                )

                // El DisposableEffect es CRUCIAL para liberar el reproductor
                // cuando el Composable se va de la pantalla.
                DisposableEffect(Unit) {
                    onDispose {
                        exoPlayer.release()
                    }
                }
            }
            mimeType?.startsWith("image/") == true -> {
                AsyncImage(
                    model = decodedUri,
                    contentDescription = "Image Viewer",
                    modifier = Modifier.fillMaxSize()
                )
            }
            else -> {
                // Fallback por si la URI no es ni video ni imagen
            }
        }

        when {
            // Aceptamos video O audio
            mimeType?.startsWith("video/") == true || mimeType?.startsWith("audio/") == true -> {
                val exoPlayer = remember {
                    ExoPlayer.Builder(context).build().apply {
                        setMediaItem(MediaItem.fromUri(decodedUri))
                        prepare()
                        playWhenReady = true
                    }
                }

                // PlayerView maneja controles de audio automáticamente (muestra barra de progreso)
                AndroidView(
                    factory = {
                        PlayerView(it).apply {
                            player = exoPlayer
                            // Si es audio, podemos ocultar el "shutter" negro o personalizarlo
                            controllerShowTimeoutMs = 0 // Mantener controles visibles para audio
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
                // ... (DisposableEffect igual)
            }
            mimeType?.startsWith("image/") == true -> {
                // ... (Igual que antes)
            }
            else -> {
                // Caso PDF/Archivo: No tenemos visor interno, intentamos abrir app externa
                LaunchedEffect(Unit) {
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                        setDataAndType(decodedUri, mimeType)
                        flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    }
                    try {
                        context.startActivity(intent)
                        onBackPressed() // Cerramos el visor negro porque abrimos app externa
                    } catch (e: Exception) {
                        // Si no hay app para abrirlo, mostrar error o quedarse
                    }
                }
                Text("Abriendo archivo externo...", color = Color.White)
            }
        }
        // Botón para volver atrás
        IconButton(
            onClick = onBackPressed,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color.White
            )
        }
    }
}
