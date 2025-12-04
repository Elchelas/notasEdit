package com.tuorg.notasmultimedia.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tuorg.notasmultimedia.di.Graph
import com.tuorg.notasmultimedia.model.db.NoteEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DetailViewModel(id: String) : ViewModel() {
    private val repo = Graph.notes
    val note = repo.byId(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun delete() {
        viewModelScope.launch {
            note.value?.let { repo.deleteNote(it.note) }
        }
    }
}
