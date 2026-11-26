package com.gizwits.smart.app.feature.control.data

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument

const val controlDataRoute: String = "control/{deviceId}/data/{mode}"

fun NavGraphBuilder.controlDataScreen(
    onBackClick: () -> Unit
) {
    composable(
        route = controlDataRoute,
        arguments = listOf(
            navArgument("deviceId") { type = NavType.StringType }
        )
    ) {
        ControlDataRoute(
            onBackClick = onBackClick
        )
    }
}

fun NavController.navigateToControlData(
    deviceId: String,
    mode: ControlDataMode
) = navigate("control/${deviceId}/data/${mode.name}")