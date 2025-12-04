@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.tuorg.notasmultimedia.ui.screens

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.navigation.NavController
import com.tuorg.notasmultimedia.R

// Versión para navegación en móvil
@Composable
fun SettingsScreen(nav: NavController) {
    SettingsScreen(
        onFinished = { nav.navigateUp() }
    )
}

// Versión reutilizable (tablet + móvil)
@Composable
fun SettingsScreen(onFinished: () -> Unit) {

    // -------- Tema oscuro: leer modo actual --------
    val initialDark = remember {
        when (AppCompatDelegate.getDefaultNightMode()) {
            AppCompatDelegate.MODE_NIGHT_YES -> true
            AppCompatDelegate.MODE_NIGHT_NO -> false
            // Si está FOLLOW_SYSTEM o AUTO, lo tomamos como "no forzado a oscuro"
            else -> false
        }
    }
    var dark by remember { mutableStateOf(initialDark) }

    // -------- Idioma: leer locales actuales --------
    val isSpanishInitial = remember {
        val tags = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        tags.startsWith("es")
    }
    var spanish by remember { mutableStateOf(isSpanishInitial) }

    // Para habilitar/deshabilitar el botón Guardar
    var hasChanges by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(id = R.string.settings_title)) }
            )
        }
    ) { pads ->
        Column(
            modifier = Modifier
                .padding(pads)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(id = R.string.settings_section_prefs),
                style = MaterialTheme.typography.titleMedium
            )

            // ------- Switch tema oscuro -------
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(stringResource(id = R.string.settings_dark))
                Switch(
                    checked = dark,
                    onCheckedChange = { checked ->
                        dark = checked
                        hasChanges = true
                    }
                )
            }

            // ------- Switch idioma -------
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(stringResource(id = R.string.settings_lang))
                Switch(
                    checked = spanish,
                    onCheckedChange = { checked ->
                        spanish = checked
                        hasChanges = true
                    }
                )
            }

            // ------- Botón Guardar -------
            Button(
                modifier = Modifier.align(Alignment.End),
                enabled = hasChanges,
                onClick = {
                    hasChanges = false

                    // Aplicar tema oscuro
                    val nightMode = if (dark) {
                        AppCompatDelegate.MODE_NIGHT_YES
                    } else {
                        AppCompatDelegate.MODE_NIGHT_NO
                    }
                    AppCompatDelegate.setDefaultNightMode(nightMode)

                    // Aplicar idioma
                    val locales = if (spanish) {
                        LocaleListCompat.forLanguageTags("es")
                    } else {
                        LocaleListCompat.forLanguageTags("en")
                    }
                    AppCompatDelegate.setApplicationLocales(locales)

                    // Cerrar ajustes (vuelve a la pantalla anterior / cierra panel en tablet)
                    onFinished()
                }
            ) {
                Text(text = stringResource(id = R.string.action_save))
            }
        }
    }
}
