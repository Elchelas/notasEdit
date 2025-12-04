package com.tuorg.notasmultimedia.ui.common

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tuorg.notasmultimedia.di.Graph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {

            // Usamos el Scheduler y el Dao
            val scheduler = AlarmScheduler(context)
            val reminderDao = Graph.db.reminderDao()
            val noteDao = Graph.db.noteDao() // Necesario para obtener el título

            CoroutineScope(Dispatchers.IO).launch {
                // 1. Obtener todos los recordatorios que aún no han pasado
                val now = System.currentTimeMillis()
                val pendingReminders = reminderDao.getAllFuture(now)

                // 2. Reprogramar cada uno
                for (reminder in pendingReminders) {
                    // Buscamos la nota para saber su título
                    val note = noteDao.getById(reminder.noteId)
                    val title = note?.title ?: "Tarea pendiente"

                    // Programamos de nuevo la alarma
                    scheduler.schedule(reminder, title)
                }
            }
        }
    }
}