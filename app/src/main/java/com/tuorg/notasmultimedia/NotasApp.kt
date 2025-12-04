package com.tuorg.notasmultimedia

import android.app.Application
import com.tuorg.notasmultimedia.di.Graph
import com.tuorg.notasmultimedia.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class NotasApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Primero, inicializamos tu Grafo para que la base de datos y el repositorio existan.
        Graph.init(this)

        // Después, y solo después, iniciamos Koin.
        // Koin ahora podrá usar el repositorio que Graph ya ha creado.
        startKoin {
            androidLogger()
            androidContext(this@NotasApp)
            modules(appModule)
        }
    }
}
