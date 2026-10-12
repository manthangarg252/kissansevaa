package com.kissansevaai.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kissansevaai.R
import com.kissansevaai.ui.theme.*

import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import coil3.compose.AsyncImage

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onNavigateToSignup: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    var email by remember { mutableStateOf("farmer@kissan.com") }
    var password by remember { mutableStateOf("Password123!") }
    var showPassword by remember { mutableStateOf(false) }

    LaunchedEffect(state.isSuccess) {
        if (state.isSuccess) {
            viewModel.clearState()
            onLoginSuccess()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Slate950),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = "https://images.unsplash.com/photo-1500382017468-9049fed747ef?auto=format&fit=crop&w=800&q=75",
            contentDescription = null,
            contentScale = ContentScale.Crop,
            placeholder = painterResource(R.drawable.ic_image_placeholder),
            error = painterResource(R.drawable.ic_broken_image),
            modifier = Modifier
                .fillMaxSize()
                .alpha(0.25f),
        )
        Card(
            modifier = Modifier
                .widthIn(max = 400.dp)
                .padding(24.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Slate900),
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.auth_login_title),
                    style = MaterialTheme.typography.headlineLarge,
                    color = Emerald400,
                )
                Text(
                    text = stringResource(R.string.auth_login_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Slate400,
                )

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text(stringResource(R.string.auth_email)) },
                    leadingIcon = { Icon(Icons.Default.Email, null) },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next,
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.auth_password)) },
                    leadingIcon = { Icon(Icons.Default.Lock, null) },
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null,
                            )
                        }
                    },
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                state.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                Button(
                    onClick = { viewModel.login(email.trim(), password) },
                    enabled = !state.isLoading && email.isNotBlank() && password.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Emerald600),
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Slate950, strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(R.string.auth_sign_in))
                    }
                }

                TextButton(onClick = onNavigateToSignup) {
                    Text(stringResource(R.string.auth_no_account), color = Slate400)
                }
            }
        }
    }
}
