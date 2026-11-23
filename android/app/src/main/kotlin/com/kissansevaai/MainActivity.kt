package com.kissansevaai

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.kissansevaai.core.data.DataStoreManager
import com.kissansevaai.ui.navigation.KissanNavGraph
import com.kissansevaai.ui.theme.KissanSevaTheme
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.kissansevaai.core.sync.DataSyncWorker.schedule(applicationContext)
        enableEdgeToEdge()
        setContent {
            val mainViewModel: MainViewModel = androidx.hilt.navigation.compose.hiltViewModel()
            val currentLang by mainViewModel.dataStoreManager.language.collectAsState(initial = "en")
            val context = LocalContext.current
            val locale = remember(currentLang) {
                when (currentLang.lowercase().trim()) {
                    "hi" -> Locale("hi", "IN")
                    "mr" -> Locale("mr", "IN")
                    "pa" -> Locale("pa", "IN")
                    "bho" -> Locale("bho", "IN")
                    else -> Locale("en", "US")
                }
            }

            Locale.setDefault(locale)
            val configuration = LocalConfiguration.current
            val localizedConfiguration = remember(locale, configuration) {
                Configuration(configuration).apply {
                    setLocale(locale)
                    setLayoutDirection(locale)
                }
            }

            CompositionLocalProvider(
                LocalConfiguration provides localizedConfiguration
            ) {
                KissanSevaTheme {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        KissanNavGraph()
                    }
                }
            }
        }
    }
}
