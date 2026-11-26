package com.gizwits.smart.app.feature.control.manage

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.gizwits.smart.sdk.ota.GizOTAFirmwareType

const val controlManageRoute: String = "control/{deviceId}/manage"

fun NavGraphBuilder.controlManageScreen(
    onBackClick: () -> Unit,
    navigateToOTAManage: (String, GizOTAFirmwareType) -> Unit,
) {
    composable(
        route = controlManageRoute,
        arguments = listOf(
            navArgument("deviceId") { type = NavType.StringType }
        )
    ) {
        ControlManageRoute(
            onBackClick = onBackClick,
            navigateToOTAManage = navigateToOTAManage
        )
    }
}

fun NavController.navigateToControlManage(deviceId: String) = navigate("control/${deviceId}/manage")