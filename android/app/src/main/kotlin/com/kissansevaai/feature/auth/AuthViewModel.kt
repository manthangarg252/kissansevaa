package com.kissansevaai.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kissansevaai.core.common.toUserFriendlyMessage
import com.kissansevaai.core.data.DataStoreManager
import com.kissansevaai.core.database.*
import com.kissansevaai.core.model.FarmProfileUpdateRequest
import com.kissansevaai.core.model.LoginRequest
import com.kissansevaai.core.model.RegisterRequest
import com.kissansevaai.core.model.UserProfile
import com.kissansevaai.core.network.KissanApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val api: KissanApi,
    private val dataStore: DataStoreManager,
    private val cropDao: CropAnalysisDao,
    private val chatDao: ChatDao,
    private val livestockDao: LivestockDao,
    private val cropPlanDao: CropPlanDao,
    private val plannerTaskDao: PlannerTaskDao,
    private val savedSchemeDao: SavedSchemeDao,
    private val marketFavoriteDao: MarketFavoriteDao,
    private val syncOperationDao: SyncOperationDao,
) : ViewModel() {

    val isLoggedIn: StateFlow<Boolean> = dataStore.token
        .map { !it.isNullOrBlank() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val baseUrl: StateFlow<String?> = dataStore.baseUrl
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun setBaseUrl(url: String) {
        viewModelScope.launch { dataStore.setBaseUrl(url) }
    }

    val language: StateFlow<String> = dataStore.language
        .stateIn(viewModelScope, SharingStarted.Eagerly, "en")

    fun setLanguage(lang: String) {
        viewModelScope.launch { dataStore.setLanguage(lang) }
    }

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState

    private val _profile = MutableStateFlow<UserProfile?>(null)
    val profile: StateFlow<UserProfile?> = _profile

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val resp = api.login(LoginRequest(email, password))
                dataStore.saveAuth(
                    token = resp.token,
                    name = resp.name,
                    email = resp.email,
                    id = resp.userId,
                )
                _uiState.value = _uiState.value.copy(isLoading = false, isSuccess = true)
                loadProfile()
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
                    id = resp.userId,
                )
                _uiState.value = _uiState.value.copy(isLoading = false, isSuccess = true)
                loadProfile()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.toUserFriendlyMessage(),
                )
            }
        }
    }

    fun loadProfile() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { api.getMe() }.onSuccess { _profile.value = it }
        }
    }

    fun updateFarmProfile(request: FarmProfileUpdateRequest, onDone: (Boolean, String?) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            try {
                val updated = api.updateProfile(request)
                _profile.value = updated
                onDone(true, null)
            } catch (e: Exception) {
                onDone(false, e.toUserFriendlyMessage())
            }
        }
    }

    fun logout() {
        viewModelScope.launch(Dispatchers.IO) {
            val uid = dataStore.userId.first().orEmpty()
            if (uid.isNotBlank()) {
                cropDao.clearForUser(uid)
                chatDao.clearForUser(uid)
                livestockDao.clearForUser(uid)
                cropPlanDao.clearForUser(uid)
                plannerTaskDao.clearForUser(uid)
                savedSchemeDao.clearForUser(uid)
                marketFavoriteDao.clearForUser(uid)
                syncOperationDao.clearForUser(uid)
            }
            dataStore.clearAuth()
            _profile.value = null
        }
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
