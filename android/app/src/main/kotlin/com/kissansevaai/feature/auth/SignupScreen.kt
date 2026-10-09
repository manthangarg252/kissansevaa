package com.kissansevaai.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kissansevaai.R
import com.kissansevaai.ui.theme.*

import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage

@Composable
fun SignupScreen(
    onSignupSuccess: () -> Unit,
    onNavigateToLogin: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    LaunchedEffect(state.isSuccess) {
        if (state.isSuccess) {
            viewModel.clearState()
            onSignupSuccess()
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(Slate950),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = "https://images.unsplash.com/photo-1464226184884-fa280b87c399?auto=format&fit=crop&q=80&w=1200",
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .alpha(0.25f),
        )
        Card(
            modifier = Modifier.widthIn(max = 400.dp).padding(24.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Slate900),
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(stringResource(R.string.auth_create_account), style = MaterialTheme.typography.headlineLarge, color = Emerald400)

                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text(stringResource(R.string.auth_full_name)) },
                    leadingIcon = { Icon(Icons.Default.Person, null) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                )
                OutlinedTextField(
                    value = email, onValueChange = { email = it },
                    label = { Text(stringResource(R.string.auth_email)) },
                    leadingIcon = { Icon(Icons.Default.Email, null) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                )
                OutlinedTextField(
                    value = password, onValueChange = { password = it },
                    label = { Text(stringResource(R.string.auth_password)) },
                    leadingIcon = { Icon(Icons.Default.Lock, null) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                )

                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

                Button(
                    onClick = { viewModel.register(name.trim(), email.trim(), password) },
                    enabled = !state.isLoading && name.isNotBlank() && email.isNotBlank() && password.length >= 6,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Emerald600),
                ) {
                    if (state.isLoading) CircularProgressIndicator(Modifier.size(20.dp), Slate950, strokeWidth = 2.dp)
                    else Text(stringResource(R.string.auth_sign_up))
                }

                TextButton(onClick = onNavigateToLogin) {
                    Text(stringResource(R.string.auth_already_account), color = Slate400)
                }
            }
        }
    }
}
