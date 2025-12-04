package com.tuorg.notasmultimedia.data.sync

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Guarda/lee estado simple de sincronización usando DataStore Preferences.
 */
class SyncStateStore(private val context: Context) {

    private val Context.syncDataStore by preferencesDataStore(name = "sync_state")

    private val LAST_SYNC_MS = longPreferencesKey("last_sync_ms")

    /** Flow con el último timestamp de sync (ms) */
    fun lastSyncFlow(): Flow<Long> =
        context.syncDataStore.data.map { prefs -> prefs[LAST_SYNC_MS] ?: 0L }

    /** Lee el último timestamp de sync (ms). */
    suspend fun getLastSync(): Long = lastSyncFlow().first()

    /** Actualiza el timestamp de último sync (ms). */
    suspend fun setLastSync(millis: Long) {
        context.syncDataStore.edit { prefs ->
            prefs[LAST_SYNC_MS] = millis
        }
    }
}
