package com.tuorg.notasmultimedia.di

import com.tuorg.notasmultimedia.ui.screens.NoteEditViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val appModule = module {
    // El repositorio ya no es necesario aquí, ya que el ViewModel lo obtiene del Graph
    // single { Graph.notes } // Esta línea ya no es necesaria para el ViewModel

    // Esta es la "receta" correcta para crear un NoteEditViewModel.
    // Le dice a Koin: "Cuando alguien pida un NoteEditViewModel con un parámetro, pásale ese parámetro al constructor".
    viewModel { (noteId: String?) ->
        NoteEditViewModel(noteId = noteId)
    }
}
