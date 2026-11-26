package com.gizwits.smart.app.feature.login

import ButtonGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gizwits.smart.app.R
import com.gizwits.smart.app.feature.main.DeviceListType
import com.gizwits.smart.app.ui.component.FullScreenAwaitDialog
import com.gizwits.smart.app.ui.component.SmartOutlinedTextField
import com.gizwits.smart.app.ui.component.SmartTextButton
import com.gizwits.smart.app.ui.component.TransparentTopAppBar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginRoute(
    onBackClick: () -> Unit,
    navigateToRegister: () -> Unit,
    navigateToForgot: () -> Unit,
    navigateToSetting: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel()
) {
    val uiState: LoginUiState by viewModel.loginUiState.collectAsState()

    LaunchedEffect(uiState) {
    }

    Scaffold(
        topBar = {
            TransparentTopAppBar(
                title = {
                    Text(
                        text = stringResource(id = R.string.user_login_title),
                        style = LocalTextStyle.current.copy(
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 32.dp),
                navigationIcon = {
                    if (!viewModel.isDefault) {
                        IconButton(
                            onClick = onBackClick,
                            colors = IconButtonDefaults.iconButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurface
                            )
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_return_24),
                                contentDescription = null
                            )
                        }
                    }
                },
//                actions = {
//                    TextButton(
//                        onClick = navigateToRegister,
//                        colors = ButtonDefaults.textButtonColors(
//                            contentColor = MaterialTheme.colorScheme.onSurface
//                        )
//                    ) {
//                        Text(text = stringResource(id = R.string.register))
//                    }
//                }
            )
        }
    ) { contentPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
        ) {
            LoginScreen(
                uiState = uiState,
                navigateToSetting= navigateToSetting,
                navigateToRegister= navigateToRegister,
                navigateToForgot = navigateToForgot,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun LoginScreen(
    uiState: LoginUiState,
    navigateToSetting: () -> Unit,
    navigateToForgot: () -> Unit,
    navigateToRegister: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (uiState) {
        is LoginUiState.LoginByUserName -> {
            LoginByUserNameContent(
                uiState = uiState,
                navigateToSetting= navigateToSetting,
                navigateToForgot= navigateToForgot,
                navigateToRegister= navigateToRegister,
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun LoginByUserNameContent(
    uiState: LoginUiState.LoginByUserName,
    navigateToSetting: () -> Unit,
    navigateToRegister: () -> Unit,
    navigateToForgot: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope: CoroutineScope = rememberCoroutineScope()
    val loginState = uiState.loginState

    if (!loginState.isEnabled) {
        FullScreenAwaitDialog()
    }
    val buttonNames = listOf("账号密码登录", "验证码登录")

    Column(
        modifier = modifier.padding(start = 24.dp, top = 0.dp, end = 24.dp, bottom = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {


        ButtonGroup(options = buttonNames, option = loginState.type, modifier = Modifier, onChange = {
            loginState.type = it
        })
        Column(
            modifier = Modifier.fillMaxWidth(0.9f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SmartOutlinedTextField(
                value = loginState.userName,
                onValueChange = { loginState.userName = it },
                modifier = Modifier.fillMaxWidth(),
                enabled = loginState.isEnabled,
                label = {
                    Text(text = stringResource(id = R.string.user_name_label))
                },
                supportingText = {
                    Text(text = loginState.userNameSuggestion ?: "")
                },
                isError = loginState.userNameSuggestion != null,
                shape = RoundedCornerShape(16.dp)
            )
            when (loginState.type) {
                "账号密码登录" -> {
                    SmartOutlinedTextField(
                        value = loginState.userPassword,
                        onValueChange = { loginState.userPassword = it },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = loginState.isEnabled,
                        label = {
                            Text(text = stringResource(id = R.string.user_password_label))
                        },
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    loginState.isPasswordVisible = !loginState.isPasswordVisible
                                }
                            ){
                                Icon(
                                    painter = painterResource(
                                        id = if (loginState.isPasswordVisible) {
                                            R.drawable.ic_preview_open_24
                                        } else {
                                            R.drawable.ic_preview_close_24
                                        }
                                    ),
                                    contentDescription = null
                                )
                            }
                        },
                        visualTransformation = if (loginState.isPasswordVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        shape = RoundedCornerShape(16.dp)
                    )
                }
                "验证码登录" -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SmartOutlinedTextField(
                            value = loginState.code,
                            onValueChange = { loginState.code = it },
                            modifier = Modifier.weight(1f),
                            label = {
                                Text(text = "验证码")
                            },
                            supportingText = {
                                Text(text = "输入验证码")
                            },
                            shape = RoundedCornerShape(16.dp)
                        )
                        TextButton(
                            modifier = Modifier.padding(bottom = 10.dp),
                            onClick = {
                                // 获取验证码
                                coroutineScope.launch {
                                    val success = loginState.getCode()

                                    if (!success) {
                                        // 提示错误
                                    }
                                }
                            },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurface
                            )
                        ) {
                            Text(text = "获取")
                        }
                    }
                }
            }


            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = loginState.isAgreePrivacyPolicy,
                    onCheckedChange = { loginState.isAgreePrivacyPolicy = it },
                    enabled = loginState.isEnabled
                )
                Text(
                    text = stringResource(id = R.string.login_privacy_policy_hint),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(
                    onClick = navigateToRegister,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Text(text = stringResource(id = R.string.register))
                }
                TextButton(
                    onClick = navigateToForgot,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Text(text = stringResource(id = R.string.forgot_password))
                }
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        Button(
            onClick = {
                coroutineScope.launch {
                    val success = loginState.login()
                    if (success) {
                        navigateToSetting()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(0.9f),
            enabled = loginState.isEnabled && loginState.isAgreePrivacyPolicy
        ) {
            Text(text = stringResource(id = R.string.login))
        }
        Button(
            onClick = {
                coroutineScope.launch {
                    val success = loginState.loginByAnonymous()
                    if (success) {
                        navigateToSetting()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(0.9f),
            enabled = loginState.isEnabled,
        ) {
            Text(text = stringResource(id = R.string.login_by_anonymous))
        }
    }
}

