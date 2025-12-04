package com.tuorg.notasmultimedia

import android.app.Application
import coil.Coil
import coil.ImageLoader
import coil.decode.VideoFrameDecoder
import com.tuorg.notasmultimedia.di.Graph
import com.tuorg.notasmultimedia.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class NotasApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // 1. Inicializar Graph (tu base de datos y repo)
        Graph.init(this)

        // 2. Crear el ImageLoader de Coil con soporte para video
        val imageLoader = ImageLoader.Builder(this)
            .components {
                add(VideoFrameDecoder.Factory())
            }
            .build()

        // 3. Establecer el ImageLoader para toda la app
        Coil.setImageLoader(imageLoader)

        // 4. Inicializar Koin (después de Graph)
        startKoin {
            androidLogger()
            androidContext(this@NotasApp)
            modules(appModule)
        }
    }
}