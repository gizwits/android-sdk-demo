package com.gizwits.smart.app.feature.register

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

private const val registerRoute: String = "register"
private const val forgotRoute: String = "forgot"

fun NavGraphBuilder.registerScreen(
    onBackClick: () -> Unit,
) {
    composable(
        route = registerRoute,
        arguments = listOf(
        )
    ) {
        RegisterRoute(
            onBackClick = onBackClick,
        )
    }
}

fun NavController.navigateToRegister() = navigate(registerRoute)
