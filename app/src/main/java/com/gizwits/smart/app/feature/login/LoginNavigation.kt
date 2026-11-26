package com.gizwits.smart.app.feature.login

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navArgument

const val loginRoute: String = "login?isDefault={isDefault}"

fun NavGraphBuilder.loginScreen(
    onBackClick: () -> Unit,
    navigateToRegister: () -> Unit,
    navigateToForgot: () -> Unit,
    navigateToSetting: () -> Unit
) {
    composable(
        route = loginRoute,
        arguments = listOf(
            navArgument("isDefault") { defaultValue = true }
        )
    ) {
        LoginRoute(
            onBackClick = onBackClick,
            navigateToRegister = navigateToRegister,
            navigateToForgot = navigateToForgot,
            navigateToSetting = navigateToSetting
        )
    }
}

fun NavController.navigateToLogin(
    isDefault: Boolean = false
) {
    navigate("login?isDefault=${isDefault}")
}

