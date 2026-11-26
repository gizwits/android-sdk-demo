package com.gizwits.smart.app.feature.forgot

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
fun ForgotRoute(
    viewModel: ForgotViewModel = hiltViewModel(),
    onBackClick: () -> Unit,
) {
    val uiState: ForgotUiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TransparentTopAppBar(
                title = {
                    Text(
                        text = stringResource(id = R.string.forgot_password),
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
            ForgotScreen(
                uiState = uiState,
                onBackClick = onBackClick,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun ForgotScreen(
    uiState: ForgotUiState,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (uiState) {
        is ForgotUiState.ForgotByUserName -> {
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
    uiState: ForgotUiState.ForgotByUserName,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope: CoroutineScope = rememberCoroutineScope()
    val state = uiState.state
    if (!state.isEnabled) {
        FullScreenAwaitDialog()
    }
    Column(
        modifier = modifier.padding(start = 24.dp, top = 0.dp, end = 24.dp, bottom = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(0.9f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SmartOutlinedTextField(
                value = state.userName,
                onValueChange = { state.userName = it },
                modifier = Modifier.fillMaxWidth(),
                enabled = state.isEnabled,
                label = {
                    Text(text = stringResource(id = R.string.user_name_label))
                },
                supportingText = {
                    Text(text = state.userNameSuggestion ?: "")
                },
                isError = state.userNameSuggestion != null,
                shape = RoundedCornerShape(16.dp)
            )
            SmartOutlinedTextField(
                value = state.userPassword,
                onValueChange = { state.userPassword = it },
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                enabled = state.isEnabled,
                label = {
                    Text(text = "新密码")
                },
                trailingIcon = {
                    IconButton(
                        onClick = {
                            state.isPasswordVisible = !state.isPasswordVisible
                        }
                    ){
                        Icon(
                            painter = painterResource(
                                id = if (state.isPasswordVisible) {
                                    R.drawable.ic_preview_open_24
                                } else {
                                    R.drawable.ic_preview_close_24
                                }
                            ),
                            contentDescription = null
                        )
                    }
                },
                visualTransformation = if (state.isPasswordVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                shape = RoundedCornerShape(16.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SmartOutlinedTextField(
                    value = state.code,
                    onValueChange = { state.code = it },
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
                            val success = state.getCode()

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
        Spacer(modifier = Modifier.weight(1f))
        Button(
            onClick = {
                coroutineScope.launch {
                    val success = state.registerWithAccount()
                    if (success) {
                        onBackClick()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(0.9f),
            enabled = true
        ) {
            Text(text = stringResource(id = R.string.forgot_password))
        }
    }
}

















