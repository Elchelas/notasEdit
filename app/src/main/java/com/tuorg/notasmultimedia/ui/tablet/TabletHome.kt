package com.tuorg.notasmultimedia.ui.tablet

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Divider
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabletHome(
    drawerContent: @Composable () -> Unit,
    homePane: @Composable (
        onItemClick: (String) -> Unit,
        onOpenDrawer: () -> Unit,
        onCreateNew: () -> Unit,
    ) -> Unit,
    rightPane: @Composable () -> Unit
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = { ModalDrawerSheet { drawerContent() } }
    ) {
        Row(Modifier.fillMaxSize()) {

            // Panel izquierdo: lista / home
            Surface(Modifier.weight(1f)) {
                homePane(
                    { /* onItemClick: el padre decide qué hacer con el id en su lambda */ },
                    { scope.launch { drawerState.open() } },
                    { /* onCreateNew: también se resuelve en la lambda del padre */ }
                )
            }

            // Separador
            Divider(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(1.dp)
            )

            // Panel derecho: detalle / ajustes / etc.
            Surface(Modifier.weight(1f)) {
                rightPane()
            }
        }
    }
}
