package com.tuorg.notasmultimedia.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.tuorg.notasmultimedia.ui.screens.DetailScreen
import com.tuorg.notasmultimedia.ui.screens.EditScreen
import com.tuorg.notasmultimedia.ui.screens.HomeScreen
import com.tuorg.notasmultimedia.ui.screens.MediaViewerScreen
import com.tuorg.notasmultimedia.ui.screens.NoteEditViewModel
import com.tuorg.notasmultimedia.ui.screens.SettingsScreen
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

object Routes {
    const val HOME = "home"

    // Edit
    const val EDIT = "edit"              // crear nueva
    const val EDIT_WITH_ID = "edit/{id}" // editar existente

    // Detail
    const val DETAIL = "detail/{id}"

    // Media Viewer
    const val MEDIA_VIEWER = "viewer" // CORREGIDO: Ruta base para el visor

    // Settings
    const val SETTINGS = "settings"
}

@Composable
fun AppNavHost(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Routes.HOME
    ) {
        // Home
        composable(Routes.HOME) {
            HomeScreen(nav = navController)
        }

        // Nueva nota/tarea
        composable(Routes.EDIT) {
            val viewModel: NoteEditViewModel = koinViewModel(parameters = { parametersOf(null as String?) })
            EditScreen(navController = navController, viewModel = viewModel)
        }

        // Editar existente
        composable(
            route = Routes.EDIT_WITH_ID,
            arguments = listOf(navArgument("id") { type = NavType.StringType })
        ) { backStack ->
            val id = backStack.arguments?.getString("id")
            val viewModel: NoteEditViewModel = koinViewModel(parameters = { parametersOf(id) })
            EditScreen(navController = navController, viewModel = viewModel)
        }

        // Detalle
        composable(
            route = Routes.DETAIL,
            arguments = listOf(navArgument("id") { type = NavType.StringType })
        ) { backStack ->
            val id = backStack.arguments?.getString("id") ?: ""
            DetailScreen(nav = navController, id = id)
        }

        // Visor de Media
        composable(
            route = "${Routes.MEDIA_VIEWER}/{uri}", // CORREGIDO: Ruta completa con argumento
            arguments = listOf(navArgument("uri") { type = NavType.StringType })
        ) { backStack ->
            // ¡LA SOLUCIÓN! Decodificamos la URI para restaurar su valor original
            val encodedUri = backStack.arguments?.getString("uri") ?: ""
            val uri = URLDecoder.decode(encodedUri, StandardCharsets.UTF_8.toString())
            MediaViewerScreen(uri = uri, onBackPressed = { navController.popBackStack() })
        }

        // Ajustes
        composable(Routes.SETTINGS) {
            SettingsScreen(nav = navController)
        }
    }
}
