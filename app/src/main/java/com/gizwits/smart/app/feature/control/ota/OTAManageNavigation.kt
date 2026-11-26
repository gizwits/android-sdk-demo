package com.gizwits.smart.app.feature.control.manage

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.gizwits.smart.app.feature.control.ota.OTAManageRoute
import com.gizwits.smart.sdk.ota.GizOTAFirmwareType

const val otaRoute: String = "control/{deviceId}/ota/{type}"

fun NavGraphBuilder.otaManagerScreen(
    onBackClick: () -> Unit
) {
    composable(
        route = otaRoute,
        arguments = listOf(
            navArgument("deviceId") { type = NavType.StringType },
            navArgument("type") { type = NavType.StringType },
        )
    ) {
        OTAManageRoute(
            onBackClick = onBackClick
        )
    }
}

fun NavController.navigateToOTAScreen(deviceId: String, type: GizOTAFirmwareType) = run {
    val typeValue = type.name
    navigate("control/${deviceId}/ota/$typeValue")
}