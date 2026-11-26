package com.gizwits.smart.app.feature.register

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gizwits.smart.app.R
import com.gizwits.smart.app.ui.component.FullScreenAwaitDialog
import com.gizwits.smart.app.ui.component.SmartOutlinedTextField
import com.gizwits.smart.app.ui.component.TransparentTopAppBar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterRoute(
    viewModel: RegisterViewModel = hiltViewModel(),
    onBackClick: () -> Unit,
) {
    val uiState: RegisterUiState by viewModel.registerUiState.collectAsState()

    Scaffold(
        topBar = {
            TransparentTopAppBar(
                title = {
                    Text(
                        text = stringResource(id = R.string.user_register_title),
                        style = LocalTextStyle.current.copy(
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 32.dp),
                navigationIcon = {
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
                },
            )
        }
    ) { contentPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
        ) {
            RegisterScreen(
                uiState = uiState,
                onBackClick = onBackClick,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun RegisterScreen(
    uiState: RegisterUiState,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (uiState) {
        is RegisterUiState.RegisterByUserName -> {
            RegisterByUserNameContent(
                uiState = uiState,
                onBackClick = onBackClick,
                modifier = modifier
            )
        }
    }
}

@Composable
private fun RegisterByUserNameContent(
    uiState: RegisterUiState.RegisterByUserName,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope: CoroutineScope = rememberCoroutineScope()
    val registerState = uiState.registerState
    if (!registerState.isEnabled) {
        FullScreenAwaitDialog()
    }

    val buttonNames = listOf("账号注册", "手机邮箱注册")

    Column(
        modifier = modifier.padding(start = 24.dp, top = 0.dp, end = 24.dp, bottom = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ButtonGroup(options = buttonNames, option = registerState.type, modifier = Modifier, onChange = {
            registerState.type = it
        })

        Column(
            modifier = Modifier.fillMaxWidth(0.9f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SmartOutlinedTextField(
                value = registerState.userName,
                onValueChange = { registerState.userName = it },
                modifier = Modifier.fillMaxWidth(),
                enabled = registerState.isEnabled,
                label = {
                    Text(text = stringResource(id = R.string.user_name_label))
                },
                supportingText = {
                    Text(text = registerState.userNameSuggestion ?: "")
                },
                isError = registerState.userNameSuggestion != null,
                shape = RoundedCornerShape(16.dp)
            )

            when (registerState.type) {
                "账号注册" -> {
                }
                "手机邮箱注册" -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SmartOutlinedTextField(
                            value = registerState.code,
                            onValueChange = { registerState.code = it },
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
                                    val success = registerState.getCode()

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

            SmartOutlinedTextField(
                value = registerState.userPassword,
                onValueChange = { registerState.userPassword = it },
                modifier = Modifier.fillMaxWidth(),
                enabled = registerState.isEnabled,
                label = {
                    Text(text = stringResource(id = R.string.user_password_label))
                },
                trailingIcon = {
                    IconButton(
                        onClick = {
                            registerState.isPasswordVisible = !registerState.isPasswordVisible
                        }
                    ){
                        Icon(
                            painter = painterResource(
                                id = if (registerState.isPasswordVisible) {
                                    R.drawable.ic_preview_open_24
                                } else {
                                    R.drawable.ic_preview_close_24
                                }
                            ),
                            contentDescription = null
                        )
                    }
                },
                visualTransformation = if (registerState.isPasswordVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                shape = RoundedCornerShape(16.dp)
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        Button(
            onClick = {
                coroutineScope.launch {
                    val success = registerState.registerWithAccount()
                    if (success) {
                        onBackClick()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(0.9f),
            enabled = true
        ) {
            Text(text = stringResource(id = R.string.register))
        }
    }
}

















