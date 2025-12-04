package com.tuorg.notasmultimedia.ui.common

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import android.content.Context
import android.content.ContextWrapper

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun isTabletLandscape(): Boolean {
    val cfg = LocalConfiguration.current
    return cfg.smallestScreenWidthDp >= 600 &&
            cfg.orientation == Configuration.ORIENTATION_LANDSCAPE
}

@Composable
fun LockLandscapeIf(condition: Boolean) {
    val activity = LocalContext.current.findActivity()
    DisposableEffect(condition) {
        val prev = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        if (condition) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        onDispose { activity?.requestedOrientation = prev }
    }
}

