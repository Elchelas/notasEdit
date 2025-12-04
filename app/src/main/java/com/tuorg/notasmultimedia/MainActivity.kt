@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.tuorg.notasmultimedia

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.util.Consumer
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.tuorg.notasmultimedia.di.Graph
import com.tuorg.notasmultimedia.nav.AppNavHost
import com.tuorg.notasmultimedia.ui.common.LockLandscapeIf
import com.tuorg.notasmultimedia.ui.common.isTabletLandscape
import com.tuorg.notasmultimedia.ui.screens.AppDrawerContent
import com.tuorg.notasmultimedia.ui.screens.DetailScreenStandalone
import com.tuorg.notasmultimedia.ui.screens.EditScreen
import com.tuorg.notasmultimedia.ui.screens.HomeListOnly
import com.tuorg.notasmultimedia.ui.screens.SettingsScreen
import com.tuorg.notasmultimedia.ui.screens.TabletDetailPlaceholder
import com.tuorg.notasmultimedia.ui.tablet.TabletHome
import com.tuorg.notasmultimedia.ui.theme.AppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.runtime.saveable.rememberSaveable
import com.tuorg.notasmultimedia.ui.screens.MediaViewerScreen
import com.tuorg.notasmultimedia.ui.screens.NoteEditViewModel
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Graph.init(applicationContext)
        setContent { App() }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        this.intent = intent
    }
}

@Composable
fun App() {
    AppTheme {
        val context = LocalContext.current
        val activity = context as? Activity
        val isTablet = isTabletLandscape()

        LockLandscapeIf(isTablet)

        // --- GESTIÓN DE PERMISOS
        val launcher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->

        }

        LaunchedEffect(Unit) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val permission = Manifest.permission.POST_NOTIFICATIONS
                if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                    launcher.launch(permission)
                }
            }
        }
        // aqui es donde se debide el flujo en nuestros dispositivos diferentes
        // --- FLUJO MÓVIL ---
        if (!isTablet) {
            val nav = rememberNavController()

            DisposableEffect(Unit) {
                val listener = Consumer<Intent> { newIntent ->
                    val noteId = newIntent.getStringExtra("NAV_NOTE_ID")
                    if (noteId != null) {
                        // SOLUCIÓN: Usar el scope de la actividad para no bloquear el receptor del intent
                        val activity = context as? androidx.activity.ComponentActivity
                        activity?.lifecycleScope?.launch(Dispatchers.Main) {
                            nav.navigate("detail/$noteId")
                            newIntent.removeExtra("NAV_NOTE_ID")
                        }
                    }
                }

                val currentActivity = context as? androidx.activity.ComponentActivity
                currentActivity?.addOnNewIntentListener(listener)

                // Chequeo inicial (también protegido)
                activity?.intent?.let { intent ->
                    val noteId = intent.getStringExtra("NAV_NOTE_ID")
                    if (noteId != null) {
                        nav.navigate("detail/$noteId")
                        intent.removeExtra("NAV_NOTE_ID")
                    }
                }

                onDispose {
                    currentActivity?.removeOnNewIntentListener(listener)
                }
            }

            AppNavHost(nav)
            return@AppTheme
        }

        // --- FLUJO TABLET ---
        var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
        var showSettings by rememberSaveable { mutableStateOf(false) }
        var showEditor by rememberSaveable { mutableStateOf(false) }
        var editingId by rememberSaveable { mutableStateOf<String?>(null) }
        var viewingMediaUri by rememberSaveable { mutableStateOf<String?>(null) }

        TabletHome(
            drawerContent = {
                AppDrawerContent(
                    onHome = {
                        showSettings = false
                        selectedId = null
                    },
                    onNew = {
                        showSettings = false
                        editingId = null
                        showEditor = true
                    },
                    onSettings = {
                        selectedId = null
                        showSettings = true
                    }
                )
            },
            homePane = { onItemClick, onOpenDrawer, onCreateNew ->
                HomeListOnly(
                    onOpenDetail = { id ->
                        showSettings = false
                        selectedId = id
                        onItemClick(id)
                    },
                    onCreateNew = {
                        showSettings = false
                        editingId = null
                        showEditor = true
                        onCreateNew()
                    },
                    onOpenDrawer = onOpenDrawer
                )
            },
            rightPane = {
                when {
                    showSettings -> SettingsScreen { showSettings = false }
                    selectedId != null -> DetailScreenStandalone(
                        id = selectedId!!,
                        onEdit = {
                            editingId = selectedId
                            showEditor = true
                        },
                        onDelete = { selectedId = null },
                        // 2. CONECTAMOS EL CALLBACK
                        onViewAttachment = { uri ->
                            viewingMediaUri = uri // Guardamos la URI para abrir el visor
                        }
                    )
                    else -> TabletDetailPlaceholder()
                }
            }
        )

        // --- Editor (Dialog)
        if (showEditor) {
            Dialog(
                onDismissRequest = { showEditor = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Surface {
                    Scaffold(
                        topBar = {
                            CenterAlignedTopAppBar(
                                title = {
                                    Text(if (editingId == null) "Nueva nota/tarea" else "Editar nota")
                                },
                                navigationIcon = {
                                    IconButton(onClick = { showEditor = false }) {
                                        Text("Cerrar")
                                    }
                                },
                                colors = TopAppBarDefaults.centerAlignedTopAppBarColors()
                            )
                        }
                    ) { pads ->
                        Box(modifier = Modifier.padding(pads).fillMaxSize()) {
                            val dialogNav = rememberNavController()

                            NavHost(navController = dialogNav, startDestination = "edit") {
                                composable("edit") {
                                    // El ViewModel se obtiene con Koin, pasándole el ID
                                    // de la nota a editar. Si el ID es null, Koin creará
                                    // un ViewModel para una nota nueva.
                                    val viewModel: NoteEditViewModel = koinViewModel(parameters = { parametersOf(editingId) })
                                    EditScreen(navController = dialogNav, viewModel = viewModel)
                                }
                                composable("done") {
                                }
                            }

                            val backStackEntry by dialogNav.currentBackStackEntryAsState()
                            LaunchedEffect(backStackEntry) {
                                if (backStackEntry?.destination?.route == "done") {
                                    showEditor = false
                                }
                            }
                        }
                    }
                }
            }
        }
        if (viewingMediaUri != null) {
            Dialog(
                onDismissRequest = { viewingMediaUri = null },
                properties = DialogProperties(usePlatformDefaultWidth = false) // Pantalla completa
            ) {
                // Truco: Codificamos la URI porque MediaViewerScreen espera recibirla codificada
                // (ya que fue diseñado para leer argumentos de navegación web/route)
                val encoded = URLEncoder.encode(viewingMediaUri, StandardCharsets.UTF_8.toString())

                MediaViewerScreen(
                    uri = encoded,
                    onBackPressed = { viewingMediaUri = null }
                )
            }
        }
    }
}
