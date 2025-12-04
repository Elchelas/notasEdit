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

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra("REMINDER_ID", -1L)
        Log.d("AlarmReceiver", "1. Recibido ID: $reminderId") // LOG 1

        if (reminderId == -1L) return

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = Graph.db
                val reminder = db.reminderDao().getById(reminderId)

                // Verificamos si existe
                if (reminder == null) {
                    Log.e("AlarmReceiver", "2. ERROR: El recordatorio es NULL en BD. ¿Se borró?")
                    return@launch
                }
                Log.d("AlarmReceiver", "2. Recordatorio encontrado: ${reminder.noteId}")

                // Verificamos si la tarea ya se completó
                val isCompleted = db.noteDao().isCompleted(reminder.noteId)
                if (isCompleted) {
                    Log.d("AlarmReceiver", "3. Tarea completada, borrando alarma.")
                    db.reminderDao().deleteById(reminderId)
                    return@launch
                }

                // MOSTRAR NOTIFICACIÓN
                Log.d("AlarmReceiver", "3. Intentando mostrar notificación...")
                val note = db.noteDao().getById(reminder.noteId)
                val title = note?.title ?: "Tarea pendiente"
                showNotification(context, reminder.noteId, title)

                // LÓGICA DE REPETICIÓN
                Log.d("AlarmReceiver", "4. Revisando repetición. Recurring=${reminder.isRecurring}, Min=${reminder.intervalMinutes}")

                if (reminder.isRecurring && reminder.intervalMinutes > 0) {
                    // Calcular nueva hora
                    val nextTime = System.currentTimeMillis() + (reminder.intervalMinutes * 60 * 1000)
                    Log.d("AlarmReceiver", "5. Reprogramando para dentro de ${reminder.intervalMinutes} min (Time: $nextTime)")

                    val newReminder = reminder.copy(triggerAt = nextTime)

                    // IMPORTANTE: Actualizar BD
                    db.reminderDao().update(newReminder)

                    // IMPORTANTE: Reprogramar AlarmManager
                    val scheduler = AlarmScheduler(context)
                    scheduler.schedule(newReminder, title)
                    Log.d("AlarmReceiver", "6. ¡Reprogramación enviada al Scheduler!")
                } else {
                    Log.d("AlarmReceiver", "5. No es recurrente, borrando.")
                    db.reminderDao().deleteById(reminderId)
                }

            } catch (e: Exception) {
                Log.e("AlarmReceiver", "ERROR CRÍTICO: ${e.message}")
                e.printStackTrace()
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun showNotification(context: Context, noteId: String, title: String) {
        try {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channelId = "tasks_channel"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Importancia ALTA es vital para que suene
                val channel = NotificationChannel(channelId, "Recordatorios", NotificationManager.IMPORTANCE_HIGH)
                channel.description = "Canal para alarmas de tareas"
                channel.enableVibration(true)
                manager.createNotificationChannel(channel)
            }

            val tapIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("NAV_NOTE_ID", noteId)
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                noteId.hashCode(), // RequestCode único
                tapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_stat_notification) // TU ICONO NUEVO
                .setContentTitle("¡Recordatorio!")
                .setContentText(title)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM) // Categoría ALARMA ayuda a sonar fuerte
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

            manager.notify(noteId.hashCode(), notification)
            Log.d("AlarmReceiver", "Notificación enviada al sistema (Si no sale, es permiso de Android)")

        } catch (e: Exception) {
            Log.e("AlarmReceiver", "Error creando notificación: ${e.message}")
        }
    }
}