package com.gizwits.smart.app.feature.setting

import com.gizwits.smart.app.feature.setup.SetupViewModel
import com.gizwits.smart.app.feature.setup.SetupPage
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

const val settingRoute: String = "setting"
const val feedbackRoute: String = "feedback"

fun NavGraphBuilder.settingScreen(
    onBackClick: () -> Unit,
    navigateToLogin: () -> Unit,
    navigateToUser: () -> Unit,
    navigateToDebug: () -> Unit,
    navigateToFeedback: () -> Unit,
    setup: SetupViewModel,
    openSetup: (SetupPage) -> Unit
) {
    composable(route = settingRoute) {
        SettingRoute(
            onBackClick = onBackClick,
            navigateToLogin = navigateToLogin,
            navigateToUser = navigateToUser,
            navigateToDebug = navigateToDebug,
            navigateToFeedback = navigateToFeedback,
            setup = setup,
            openSetup = openSetup
        )
    }
    composable(route = feedbackRoute) {
        FeedbackScreen(onBackClick)
    }
}

fun NavController.navigateToSetting() = navigate(settingRoute)
fun NavController.navigateToFeedback() = navigate(feedbackRoute)
