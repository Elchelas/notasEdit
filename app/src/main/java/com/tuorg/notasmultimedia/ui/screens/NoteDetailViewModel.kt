package com.tuorg.notasmultimedia.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tuorg.notasmultimedia.di.Graph
import com.tuorg.notasmultimedia.data.NoteRepository
import com.tuorg.notasmultimedia.model.db.NoteWithRelations
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NoteDetailViewModel(
    private val noteId: String,
    private val repo: NoteRepository = Graph.notes
) : ViewModel() {

    val note: StateFlow<NoteWithRelations?> =
        repo.byId(noteId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun delete(onDeleted: () -> Unit) {
        val n = note.value?.note ?: return
        viewModelScope.launch {
            repo.deleteNote(n)
            onDeleted()
        }
    }

    companion object {
        fun provideFactory(noteId: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return NoteDetailViewModel(noteId) as T
                }
            }
    }
}
