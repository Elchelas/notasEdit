package com.tuorg.notasmultimedia.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.tuorg.notasmultimedia.di.Graph

class NotesSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = try {

        Graph.notes.syncNow()
        Result.success()
    } catch (t: Throwable) {
        Result.retry()
    }
}
