package com.gizwits.smart.app.feature.forgot

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

private const val forgotRoute: String = "forgot"

fun NavGraphBuilder.forgotScreen(
    onBackClick: () -> Unit,
) {
    composable(
        route = forgotRoute,
        arguments = listOf(
        )
    ) {
        ForgotRoute(
            onBackClick = onBackClick,
        )
    }
}

fun NavController.navigateToForgot() = navigate(forgotRoute)
