package com.gizwits.smart.app.feature.control

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.gizwits.smart.app.feature.control.data.ControlDataMode

const val controlRoute: String = "control/{deviceId}"

fun NavGraphBuilder.controlScreen(
    onBackClick: () -> Unit,
    navigateToControlData: (String, ControlDataMode) -> Unit,
    navigateToControlManage: (String) -> Unit
) {
    composable(
        route = controlRoute,
        arguments = listOf(
            navArgument("deviceId") { type = NavType.StringType }
        )
    ) {
        ControlRoute(
            onBackClick = onBackClick,
            navigateToControlData = navigateToControlData,
            navigateToControlManage = navigateToControlManage
        )
    }
}

fun NavController.navigateToControl(deviceId: String) = navigate("control/${deviceId}")