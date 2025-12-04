package com.tuorg.notasmultimedia.data.remote

import com.tuorg.notasmultimedia.model.db.SyncOpEntity

class RemoteNoteDataSource {

    suspend fun pullSince(since: Long): List<NoteGraphDTO> = emptyList()

    suspend fun push(op: SyncOpEntity) { /* no-op por ahora */ }
}