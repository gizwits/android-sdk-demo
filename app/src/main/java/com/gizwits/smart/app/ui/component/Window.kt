package com.gizwits.smart.app.ui.component

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalView
import com.google.accompanist.systemuicontroller.SystemUiController
import com.google.accompanist.systemuicontroller.rememberSystemUiController

@Composable
fun WindowFocusChangeHandler(block: (Boolean) -> Unit) {
    val viewTreeObserver = LocalView.current.viewTreeObserver
    DisposableEffect(block) {
        viewTreeObserver.addOnWindowFocusChangeListener(block)
        onDispose {
            viewTreeObserver.removeOnWindowFocusChangeListener(block)
        }
    }
}

@Composable
fun SystemUiController(block: SystemUiController.() -> Unit) {
    val systemUiController = rememberSystemUiController()
    LaunchedEffect(Unit) { systemUiController.block() }
}