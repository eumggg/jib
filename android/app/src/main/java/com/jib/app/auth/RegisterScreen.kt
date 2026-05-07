package com.jib.app.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun RegisterScreen(
    onRegisterSuccess: () -> Unit,
    onNavigateToLogin: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val authState by viewModel.authState.collectAsStateWithLifecycle()

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordMismatch by remember { mutableStateOf(false) }

    LaunchedEffect(authState) {
        if (authState is AuthState.Success) {
            onRegisterSuccess()
            viewModel.clearError()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Create Account",
            style = MaterialTheme.typography.headlineMedium,
        )

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = password,
            onValueChange = {
                password = it
                passwordMismatch = false
            },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = confirmPassword,
            onValueChange = {
                confirmPassword = it
                passwordMismatch = false
            },
            label = { Text("Confirm Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            isError = passwordMismatch,
            supportingText = if (passwordMismatch) {
                { Text("Passwords do not match") }
            } else null,
            modifier = Modifier.fillMaxWidth(),
        )

        if (authState is AuthState.Error) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = (authState as AuthState.Error).message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = {
                if (password != confirmPassword) {
                    passwordMismatch = true
                } else {
                    viewModel.signUp(email, password)
                }
            },
            enabled = authState !is AuthState.Loading
                && email.isNotBlank()
                && password.isNotBlank()
                && confirmPassword.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (authState is AuthState.Loading) {
                CircularProgressIndicator(modifier = Modifier.height(20.dp))
            } else {
                Text("Register")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(onClick = onNavigateToLogin) {
            Text("Already have an account? Sign in")
        }
    }
}
