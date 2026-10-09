package com.kissansevaai.ui.navigation

import kotlinx.serialization.Serializable

sealed interface Screen {
    @Serializable data object Login : Screen
    @Serializable data object Signup : Screen
    @Serializable data object Dashboard : Screen
    @Serializable data object CropDisease : Screen
    @Serializable data object Livestock : Screen
    @Serializable data object MarketInsights : Screen
    @Serializable data object IoTMonitoring : Screen
    @Serializable data object Schemes : Screen
    @Serializable data object Loans : Screen
    @Serializable data object Traders : Screen
    @Serializable data object Carbon : Screen
    @Serializable data object LivePrices : Screen
    @Serializable data object Chatbot : Screen
    @Serializable data object Settings : Screen
}
