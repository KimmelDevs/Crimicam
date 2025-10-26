package com.example.crimicam.signup

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
import androidx.navigation.NavController
import com.example.crimicam.R

@Composable
fun SignupScreen(navController: NavController) {
    Box(
        modifier = Modifier.fillMaxSize()
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
                contentDescription = "Signup illustration",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            )

            Text(
                text = "Create an Account",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
                fontSize = 24.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            EmailTextField()

            Spacer(modifier = Modifier.height(10.dp))

            PasswordTextField(label = "Password")

            Spacer(modifier = Modifier.height(10.dp))

            PasswordTextField(label = "Confirm Password")

            Spacer(modifier = Modifier.height(24.dp))

            GradientButton(
                text = "Sign Up",
                gradientColors = listOf(Color(0xFF484BF1), Color(0xFF673AB7)),
                cornerRadius = 16.dp
            ) {
                // signup logic didi
            }

            Spacer(modifier = Modifier.height(20.dp))

            TextButton(onClick = { navController.popBackStack() }) {
                Text(
                    text = "Already have an account? Login",
                    style = MaterialTheme.typography.labelLarge,
                    letterSpacing = 1.sp,
                )
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun EmailTextField() {
    val keyboardController = LocalSoftwareKeyboardController.current
    var email by rememberSaveable { mutableStateOf("") }

    OutlinedTextField(
        value = email,
        onValueChange = { email = it },
        label = {
            Text(
                text = "Email Address"
            )
        },
        placeholder = { Text("Email Address") },
        shape = RoundedCornerShape(topEnd = 12.dp, bottomStart = 12.dp),
        singleLine = true,
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
fun PasswordTextField(label: String) {
    val keyboardController = LocalSoftwareKeyboardController.current
    var password by rememberSaveable { mutableStateOf("") }
    var hidden by rememberSaveable { mutableStateOf(true) }

    OutlinedTextField(
        value = password,
        onValueChange = { password = it },
        label = {
            Text(
                text = label
            )
        },
        placeholder = { Text(label) },
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
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
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
                .background(brush = Brush.horizontalGradient(gradientColors)),
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
