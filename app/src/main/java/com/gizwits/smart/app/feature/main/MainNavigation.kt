package com.gizwits.smart.app.feature.main

import com.gizwits.smart.app.feature.setup.SetupViewModel
import com.gizwits.smart.app.feature.setup.SetupPage
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable

const val mainRoute: String = "main"

fun NavGraphBuilder.mainScreen(
    navigateToControl: (String) -> Unit,
    navigateToSetting: () -> Unit,
    setup: SetupViewModel,
    openSetup: (SetupPage) -> Unit
) {
    composable(route = mainRoute) {
        MainRoute(
            navigateToControl = navigateToControl,
            navigateToSetting = navigateToSetting,
            setup = setup,
            openSetup = openSetup
        )
    }
}

fun NavController.navigateToMain(
    navOptions: NavOptions? = null
) = navigate(mainRoute, navOptions)

