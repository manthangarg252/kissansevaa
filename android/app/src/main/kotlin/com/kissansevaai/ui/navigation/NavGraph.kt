package com.kissansevaai.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.kissansevaai.ui.theme.DeepEmerald
import com.kissansevaai.ui.theme.SurfaceBg
import com.kissansevaai.feature.auth.AuthViewModel
import com.kissansevaai.feature.auth.LoginScreen
import com.kissansevaai.feature.auth.SignupScreen
import com.kissansevaai.feature.carbon.CarbonCreditsScreen
import com.kissansevaai.feature.chat.ChatbotScreen
import com.kissansevaai.feature.crop.CropDiseaseScreen
import com.kissansevaai.feature.dashboard.DashboardScreen
import com.kissansevaai.feature.iot.IoTMonitoringScreen
import com.kissansevaai.feature.livestock.LivestockScreen
import com.kissansevaai.feature.loans.LoansScreen
import com.kissansevaai.feature.market.MarketInsightsScreen
import com.kissansevaai.feature.planner.CropPlannerScreen
import com.kissansevaai.feature.prices.LivePricesScreen
import com.kissansevaai.feature.schemes.SchemesScreen
import com.kissansevaai.feature.settings.SettingsScreen
import com.kissansevaai.feature.traders.TradersScreen
import com.kissansevaai.feature.weather.WeatherScreen

@Composable
fun KissanNavGraph() {
    val authVm: AuthViewModel = hiltViewModel()
    val isLoggedInState by authVm.isLoggedInState.collectAsState()

    val resolvedLoggedIn = isLoggedInState
    if (resolvedLoggedIn == null) {
        Box(
            modifier = Modifier.fillMaxSize().background(SurfaceBg),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = DeepEmerald)
        }
        return
    }

    val navController = rememberNavController()

    androidx.compose.runtime.LaunchedEffect(resolvedLoggedIn) {
        if (!resolvedLoggedIn) {
            val currentRoute = navController.currentBackStackEntry?.destination?.route
            if (currentRoute != null && !currentRoute.contains("Login") && !currentRoute.contains("Signup")) {
                navController.navigate(Screen.Login) {
                    popUpTo(0) { inclusive = true }
                }
            }
        }
    }

    val startDest: Any = if (resolvedLoggedIn) Screen.Dashboard else Screen.Login

    NavHost(navController = navController, startDestination = startDest) {

        composable<Screen.Login> {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Screen.Dashboard) {
                        popUpTo(Screen.Login) { inclusive = true }
                    }
                },
                onNavigateToSignup = { navController.navigate(Screen.Signup) },
            )
        }

        composable<Screen.Signup> {
            SignupScreen(
                onSignupSuccess = {
                    navController.navigate(Screen.Dashboard) {
                        popUpTo(Screen.Login) { inclusive = true }
                    }
                },
                onNavigateToLogin = { navController.popBackStack() },
            )
        }

        composable<Screen.Dashboard> {
            DashboardScreen(
                onNavigate = { screen -> navController.navigate(screen) },
                onLogout = {
                    authVm.logout()
                    navController.navigate(Screen.Login) {
                        popUpTo(0) { inclusive = true }
                    }
                },
            )
        }

        composable<Screen.CropDisease> {
            CropDiseaseScreen(onBack = { navController.popBackStack() })
        }

        composable<Screen.Livestock> {
            LivestockScreen(onBack = { navController.popBackStack() })
        }

        composable<Screen.Weather> {
            WeatherScreen(onBack = { navController.popBackStack() })
        }

        composable<Screen.CropPlanner> {
            CropPlannerScreen(onBack = { navController.popBackStack() })
        }

        composable<Screen.MarketInsights> {
            MarketInsightsScreen(onBack = { navController.popBackStack() })
        }

        composable<Screen.IoTMonitoring> {
            IoTMonitoringScreen(onBack = { navController.popBackStack() })
        }

        composable<Screen.Schemes> {
            SchemesScreen(onBack = { navController.popBackStack() })
        }

        composable<Screen.Loans> {
            LoansScreen(onBack = { navController.popBackStack() })
        }

        composable<Screen.Traders> {
            TradersScreen(onBack = { navController.popBackStack() })
        }

        composable<Screen.Carbon> {
            CarbonCreditsScreen(onBack = { navController.popBackStack() })
        }

        composable<Screen.LivePrices> {
            LivePricesScreen(onBack = { navController.popBackStack() })
        }

        composable<Screen.Chatbot> {
            ChatbotScreen(onBack = { navController.popBackStack() })
        }

        composable<Screen.Settings> {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
