package com.kissansevaai.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kissansevaai.core.common.toUserFriendlyMessage
import com.kissansevaai.core.data.DataStoreManager
import com.kissansevaai.core.model.LoginRequest
import com.kissansevaai.core.model.RegisterRequest
import com.kissansevaai.core.network.KissanApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val api: KissanApi,
    private val dataStore: DataStoreManager,
) : ViewModel() {

    val isLoggedIn: StateFlow<Boolean> = dataStore.token
        .map { !it.isNullOrBlank() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val resp = api.login(LoginRequest(email, password))
                dataStore.saveAuth(
                    token = resp.token,
                    name = resp.name,
                    email = resp.email,
                    id = resp.id ?: "",
                )
                _uiState.value = _uiState.value.copy(isLoading = false, isSuccess = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.toUserFriendlyMessage(),
                )
            }
        }
    }

    fun register(name: String, email: String, password: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val resp = api.register(RegisterRequest(name, email, password))
                dataStore.saveAuth(
                    token = resp.token,
                    name = resp.name,
                    email = resp.email,
                    id = resp.id ?: "",
                )
                _uiState.value = _uiState.value.copy(isLoading = false, isSuccess = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.toUserFriendlyMessage(),
                )
            }
        }
    }

    fun logout() {
        viewModelScope.launch { dataStore.clearAuth() }
    }

    fun clearState() {
        _uiState.value = AuthUiState()
    }
}

data class AuthUiState(
    val isLoading: Boolean = false,
    val isSuccess: Boolean = false,
    val error: String? = null,
)
