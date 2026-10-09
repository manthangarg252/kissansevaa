package com.kissansevaai.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kissansevaai.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeatureScaffold(
    title: String,
    subtitle: String? = null,
    onBack: () -> Unit,
    floatingActionButton: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Slate900),
            )
        },
        snackbarHost = snackbarHost,
        floatingActionButton = floatingActionButton,
        containerColor = Slate950,
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Slate400,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            content(padding)
        }
    }
}

@Composable
fun LoadingOverlay(isLoading: Boolean) {
    if (isLoading) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = Emerald500)
        }
    }
}

@Composable
fun AiResponseCard(title: String, response: String) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Slate800),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Emerald400)
            Spacer(Modifier.height(8.dp))
            Text(response, style = MaterialTheme.typography.bodyMedium, color = Slate200)
        }
    }
}
