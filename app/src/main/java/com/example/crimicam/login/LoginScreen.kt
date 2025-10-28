package com.example.crimicam.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.crimicam.R

@Composable
fun LoginScreen(
    homeClick: () -> Unit,
    signupClick: () -> Unit,
    viewModel: LoginViewModel = viewModel()
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    val loginState by viewModel.loginState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Handle navigation on success
    LaunchedEffect(loginState.isSuccess) {
        if (loginState.isSuccess) {
            homeClick()
            viewModel.resetState()
        }
    }

    // Show error message
    LaunchedEffect(loginState.errorMessage) {
        loginState.errorMessage?.let { error ->
            snackbarHostState.showSnackbar(
                message = error,
                duration = SnackbarDuration.Short
            )
            viewModel.resetState()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Crimicam",
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    fontSize = 32.sp
                )

                Image(
                    painter = painterResource(id = R.drawable.img),
                    contentDescription = "Login illustration",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                )

                Text(
                    text = "Login to Continue",
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                    fontSize = 24.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                EmailTextField(
                    value = email,
                    onValueChange = { email = it },
                    enabled = !loginState.isLoading
                )

                Spacer(modifier = Modifier.height(10.dp))

                PasswordTextField(
                    value = password,
                    onValueChange = { password = it },
                    enabled = !loginState.isLoading
                )

                Spacer(modifier = Modifier.height(24.dp))

                GradientButton(
                    text = if (loginState.isLoading) "Logging in..." else "Login",
                    gradientColors = listOf(Color(0xFF484BF1), Color(0xFF673AB7)),
                    cornerRadius = 16.dp,
                    enabled = !loginState.isLoading
                ) {
                    viewModel.login(email, password)
                }

                Spacer(modifier = Modifier.height(20.dp))

                TextButton(
                    onClick = signupClick,
                    enabled = !loginState.isLoading
                ) {
                    Text(
                        text = "Create an Account",
                        style = MaterialTheme.typography.labelLarge,
                        letterSpacing = 1.sp,
                    )
                }
            }

            // Loading indicator
            if (loginState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun EmailTextField(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean = true
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = {
            Text(
                text = "Email Address"
            )
        },
        placeholder = { Text("Email Address") },
        shape = RoundedCornerShape(topEnd = 12.dp, bottomStart = 12.dp),
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(
            imeAction = ImeAction.Next,
            keyboardType = KeyboardType.Email
        ),
        keyboardActions = KeyboardActions(onNext = { keyboardController?.hide() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.primary
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PasswordTextField(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean = true
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    var hidden by rememberSaveable { mutableStateOf(true) }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = {
            Text(
                text = "Password"
            )
        },
        placeholder = { Text("Enter your password") },
        enabled = enabled,
        visualTransformation = if (hidden) PasswordVisualTransformation() else VisualTransformation.None,
        trailingIcon = {
            IconButton(onClick = { hidden = !hidden }) {
                Icon(
                    painter = painterResource(
                        id = if (hidden) R.drawable.ic_visibility_off else R.drawable.ic_visibility_on
                    ),
                    contentDescription = if (hidden) "Show password" else "Hide password",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        shape = RoundedCornerShape(topEnd = 12.dp, bottomStart = 12.dp),
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            imeAction = ImeAction.Done,
            keyboardType = KeyboardType.Password
        ),
        keyboardActions = KeyboardActions(onDone = { keyboardController?.hide() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.primary
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun GradientButton(
    text: String,
    gradientColors: List<Color>,
    cornerRadius: Dp,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(55.dp),
        shape = RoundedCornerShape(cornerRadius),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
        contentPadding = PaddingValues()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(cornerRadius))
                .background(
                    brush = Brush.horizontalGradient(
                        colors = if (enabled) gradientColors else listOf(
                            Color.Gray,
                            Color.DarkGray
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                color = Color.White,
                fontSize = 18.sp
            )
        }
    }
}