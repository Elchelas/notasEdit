package com.tuorg.notasmultimedia.data

import com.tuorg.notasmultimedia.data.local.LocalNoteDataSource
import com.tuorg.notasmultimedia.data.remote.RemoteNoteDataSource
import com.tuorg.notasmultimedia.data.sync.SyncStateStore
import com.tuorg.notasmultimedia.model.db.AppDatabase
import com.tuorg.notasmultimedia.model.db.AttachmentEntity
import com.tuorg.notasmultimedia.model.db.ItemType
import com.tuorg.notasmultimedia.model.db.NoteEntity
import com.tuorg.notasmultimedia.model.db.NoteWithRelations
import com.tuorg.notasmultimedia.model.db.ReminderEntity
import kotlinx.coroutines.flow.Flow

/**
 * Implementación del repositorio que delega en:
 *  - LocalNoteDataSource (Room)
 *  - RemoteNoteDataSource (stub por ahora)
 *  - SyncStateStore (DataStore para estado de sync)
 *
 * NOTA: Este repo asume que existe una interfaz `NoteRepository` con las mismas
 * firmas de métodos. Si en tu proyecto `NoteRepository` todavía es una clase,
 * conviértela en interfaz o cambia el tipo en Graph de `NoteRepository` a
 * `DefaultNoteRepository`.
 */
class DefaultNoteRepository(
    private val local: LocalNoteDataSource,
    @Suppress("UNUSED_PARAMETER")
    private val remote: RemoteNoteDataSource,
    @Suppress("UNUSED_PARAMETER")
    private val db: AppDatabase,               
    private val syncState: SyncStateStore
) : NoteRepository {

    override fun all(): Flow<List<NoteWithRelations>> =
        local.all()

    override fun byType(type: ItemType): Flow<List<NoteWithRelations>> =
        local.byType(type)

    override fun byId(id: String): Flow<NoteWithRelations?> =
        local.byId(id)

    override fun search(q: String): Flow<List<NoteEntity>> =
        local.search(q)

    /**
     * Inserta/actualiza nota + relaciones en Room y marca como "dirty".
     * Además encola operación en el outbox (lo hace el LocalNoteDataSource).
     */
    override suspend fun upsertGraph(
        note: NoteEntity,
        attachments: List<AttachmentEntity>,
        reminders: List<ReminderEntity>
    ) {
        local.upsertGraphDirty(note, attachments, reminders)
    }

    /**
     * Marca la nota como eliminada (tombstone) y encola delete en outbox.
     */
    override suspend fun deleteNote(note: NoteEntity) {
        local.tombstone(note)
    }

    /**
     * Sincroniza con backend.
     * En esta versión mínima:
     *  - lee el último timestamp (por si quieres filtrar al hacer pull)
     *  - (TODO) push: enviar outbox
     *  - (TODO) pull: traer cambios remotos > aplicar con local.applyRemoteGraphReplace(...)
     *  - actualiza el timestamp de último sync
     */
    override suspend fun syncNow() {

        val since = syncState.getLastSync()

        // -----------------------------------------------
        // TODO: PUSH
        //   - leer operaciones de outbox (db.syncOutboxDao())
        //   - enviar al servidor
        //   - si éxito -> limpiar esas operaciones del outbox
        // -----------------------------------------------

        // -----------------------------------------------
        // TODO: PULL
        //   - pedir cambios al servidor desde 'since'
        //   - por cada grafo remoto más nuevo:
        //       local.applyRemoteGraphReplace(note, attachments, reminders)
        // -----------------------------------------------


        syncState.setLastSync(System.currentTimeMillis())
    }
}
