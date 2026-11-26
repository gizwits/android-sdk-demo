package com.gizwits.smart.app.feature.changePassword

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

const val changeRouter: String = "changePassword"

fun NavGraphBuilder.changePasswordScreen(
    onBackClick: () -> Unit,
) {
    composable(route = changeRouter) {
        ChangePasswordRoute(
            onBackClick = onBackClick,
        )
    }
}

fun NavController.navigateToChangePassword() = navigate(changeRouter)
