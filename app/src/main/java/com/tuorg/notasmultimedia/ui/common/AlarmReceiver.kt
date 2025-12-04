package com.tuorg.notasmultimedia.ui.common

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.tuorg.notasmultimedia.MainActivity
import com.tuorg.notasmultimedia.R
import com.tuorg.notasmultimedia.di.Graph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Recibimos el ID específico del recordatorio
        val reminderId = intent.getLongExtra("REMINDER_ID", -1L)
        if (reminderId == -1L) return

        // Mantenemos vivo el Receiver para consultar la BD
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = Graph.db
                val reminder = db.reminderDao().getById(reminderId)

                // Si el recordatorio ya no existe, abortamos
                if (reminder == null) return@launch

                // 1. VERIFICAR SI YA SE COMPLETÓ
                val isCompleted = db.noteDao().isCompleted(reminder.noteId)

                if (isCompleted) {
                    // Si ya terminó, borramos este recordatorio para que deje de molestar
                    db.reminderDao().deleteById(reminderId)
                } else {
                    // 2. MOSTRAR NOTIFICACIÓN
                    val note = db.noteDao().getById(reminder.noteId)
                    val title = note?.title ?: "Tarea pendiente"

                    showNotification(context, reminder.noteId, title)

                    // 3. LÓGICA DE REPETICIÓN (Hasta que se complete)
                    if (reminder.isRecurring && reminder.intervalMinutes > 0) {
                        // Calcular nueva hora sumando minutos
                        val nextTime = reminder.triggerAt + (reminder.intervalMinutes * 60 * 1000)

                        val newReminder = reminder.copy(triggerAt = nextTime)

                        // Actualizar en BD y reprogramar en Android
                        db.reminderDao().update(newReminder)

                        val scheduler = AlarmScheduler(context)
                        scheduler.schedule(newReminder, title)
                    } else {
                        // Si no es recurrente, ya sonó y lo borramos
                        db.reminderDao().deleteById(reminderId)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun showNotification(context: Context, noteId: String, title: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "tasks_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Recordatorios", NotificationManager.IMPORTANCE_HIGH)
            manager.createNotificationChannel(channel)
        }

        // 👇 INTENT DE NAVEGACIÓN (Deep Link)
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("NAV_NOTE_ID", noteId) // <--- Esta es la clave para navegar
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            noteId.hashCode(),
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle("Recordatorio")
            .setContentText(title)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent) // <--- Conectamos el click
            .setAutoCancel(true)
            .build()

        manager.notify(noteId.hashCode(), notification)
    }
}