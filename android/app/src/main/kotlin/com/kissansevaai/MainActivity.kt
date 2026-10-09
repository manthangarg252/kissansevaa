package com.kissansevaai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.kissansevaai.ui.navigation.KissanNavGraph
import com.kissansevaai.ui.theme.KissanSevaTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.kissansevaai.core.sync.DataSyncWorker.schedule(applicationContext)
        enableEdgeToEdge()
        setContent {
            KissanSevaTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    KissanNavGraph()
                }
            }
        }
    }
}
