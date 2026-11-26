package com.gizwits.smart.app.feature.debug

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

const val debugRoute: String = "debug"

fun NavGraphBuilder.debugScreen(
    onBackClick: () -> Unit
) {
    composable(route = debugRoute) {
        DebugRoute(
            onBackClick = onBackClick
        )
    }
}

fun NavController.navigateToDebug() = navigate(debugRoute)