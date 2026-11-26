package com.gizwits.smart.app.feature.user

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

const val userRoute: String = "user"

fun NavGraphBuilder.userScreen(
    onBackClick: () -> Unit,
    navigateToChangePassword: () -> Unit,
) {
    composable(route = userRoute) {
        UserRoute(
            onBackClick = onBackClick,
            navigateToChangePassword = navigateToChangePassword
        )
    }
}

fun NavController.navigateToUser() = navigate(userRoute)
