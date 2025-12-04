package com.tuorg.notasmultimedia.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tuorg.notasmultimedia.R

@Composable
fun AppDrawerContent(
    onHome: () -> Unit,
    onNew: () -> Unit,
    onSettings: () -> Unit
) {
    ModalDrawerSheet {
        Text(
            text = stringResource(id = R.string.app_name),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(16.dp)
        )
        NavigationDrawerItem(
            label = { Text(stringResource(R.string.menu_home)) },
            selected = true, onClick = onHome
        )
        NavigationDrawerItem(
            label = { Text(stringResource(R.string.menu_new)) },
            selected = false, onClick = onNew
        )
        NavigationDrawerItem(
            label = { Text(stringResource(R.string.menu_settings)) },
            selected = false, onClick = onSettings
        )
    }
}
