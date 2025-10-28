package com.example.crimicam.signup

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.navigation.NavController
import com.example.crimicam.R

@Composable
fun SignupScreen(
    navController: NavController,
    viewModel: SignupViewModel = viewModel()
) {
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }

    val signupState by viewModel.signupState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollState = rememberScrollState()

    // Handle navigation on success - redirect to login
    LaunchedEffect(signupState.isSuccess) {
        if (signupState.isSuccess) {
            snackbarHostState.showSnackbar(
                message = "Account created successfully! Please login.",
                duration = SnackbarDuration.Short
            )
            // Navigate back to login screen
            navController.navigate("login") {
                popUpTo("signup") { inclusive = true }
            }
            viewModel.resetState()
        }
    }

    // Show error message
    LaunchedEffect(signupState.errorMessage) {
        signupState.errorMessage?.let { error ->
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
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 24.dp),
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
                    contentDescription = "Signup illustration",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                )

                Text(
                    text = "Create an Account",
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                    fontSize = 24.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                NameTextField(
                    value = name,
                    onValueChange = { name = it },
                    enabled = !signupState.isLoading
                )

                Spacer(modifier = Modifier.height(10.dp))

                EmailTextField(
                    value = email,
                    onValueChange = { email = it },
                    enabled = !signupState.isLoading
                )

                Spacer(modifier = Modifier.height(10.dp))

                PasswordTextField(
                    label = "Password",
                    value = password,
                    onValueChange = { password = it },
                    enabled = !signupState.isLoading
                )

                Spacer(modifier = Modifier.height(10.dp))

                PasswordTextField(
                    label = "Confirm Password",
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    enabled = !signupState.isLoading
                )

                Spacer(modifier = Modifier.height(24.dp))

                GradientButton(
                    text = if (signupState.isLoading) "Creating Account..." else "Sign Up",
                    gradientColors = listOf(Color(0xFF484BF1), Color(0xFF673AB7)),
                    cornerRadius = 16.dp,
                    enabled = !signupState.isLoading
                ) {
                    viewModel.signUp(name, email, password, confirmPassword)
                }

                Spacer(modifier = Modifier.height(20.dp))

                TextButton(
                    onClick = { navController.popBackStack() },
                    enabled = !signupState.isLoading
                ) {
                    Text(
                        text = "Already have an account? Login",
                        style = MaterialTheme.typography.labelLarge,
                        letterSpacing = 1.sp,
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
            }

            // Loading indicator
            if (signupState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun NameTextField(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean = true
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(text = "Full Name") },
        placeholder = { Text("Enter your full name") },
        shape = RoundedCornerShape(topEnd = 12.dp, bottomStart = 12.dp),
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(
            imeAction = ImeAction.Next,
            keyboardType = KeyboardType.Text,
            capitalization = KeyboardCapitalization.Words
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
fun EmailTextField(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean = true
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(text = "Email Address") },
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
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean = true
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    var hidden by rememberSaveable { mutableStateOf(true) }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(text = label) },
        placeholder = { Text(label) },
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
            imeAction = if (label == "Confirm Password") ImeAction.Done else ImeAction.Next,
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