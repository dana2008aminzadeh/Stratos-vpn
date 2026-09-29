package com.v2ray.ang.ui.stratos

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import com.v2ray.ang.R
import com.v2ray.ang.stratos.StratosApiProvider
import com.v2ray.ang.stratos.StratosLoginResult
import com.v2ray.ang.stratos.StratosQrLogin
import com.v2ray.ang.stratos.StratosServersStore
import com.v2ray.ang.stratos.StratosSession
import com.v2ray.ang.stratos.StratosSync
import com.v2ray.ang.stratos.StratosValidators
import com.v2ray.ang.ui.base.HelperBaseComponentActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// --------------------------------------------------------------------------------------
// Contract
// --------------------------------------------------------------------------------------

data class StratosLoginUiState(
    val username: String = "",
    val password: String = "",
    val passwordVisible: Boolean = false,
    val isLoading: Boolean = false,
    val usernameErrorRes: Int? = null,
    val passwordErrorRes: Int? = null,
    val generalErrorRes: Int? = null,
)

sealed interface StratosLoginAction {
    data class UsernameChanged(val value: String) : StratosLoginAction
    data class PasswordChanged(val value: String) : StratosLoginAction
    data object TogglePasswordVisibility : StratosLoginAction
    data object Submit : StratosLoginAction
    data class SubmitQr(val content: String) : StratosLoginAction
}

sealed interface StratosLoginEvent {
    data object NavigateHome : StratosLoginEvent
    data class GeneralError(val res: Int) : StratosLoginEvent
}

// --------------------------------------------------------------------------------------
// ViewModel
// --------------------------------------------------------------------------------------

class StratosLoginViewModel(private val app: android.app.Application) : ViewModel() {

    private val _uiState = MutableStateFlow(StratosLoginUiState())
    val uiState: StateFlow<StratosLoginUiState> = _uiState.asStateFlow()

    private val _events = Channel<StratosLoginEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun onAction(action: StratosLoginAction) {
        when (action) {
            is StratosLoginAction.UsernameChanged -> _uiState.update {
                it.copy(username = action.value, usernameErrorRes = null, generalErrorRes = null)
            }

            is StratosLoginAction.PasswordChanged -> _uiState.update {
                it.copy(password = action.value, passwordErrorRes = null, generalErrorRes = null)
            }

            StratosLoginAction.TogglePasswordVisibility -> _uiState.update {
                it.copy(passwordVisible = !it.passwordVisible)
            }

            StratosLoginAction.Submit -> submit()
            is StratosLoginAction.SubmitQr -> submitQr(action.content)
        }
    }

    private fun submitQr(content: String) {
        val credentials = StratosQrLogin.parse(content) ?: run {
            _events.trySend(StratosLoginEvent.GeneralError(R.string.stratos_error_invalid_credentials))
            return
        }
        _uiState.update { it.copy(username = credentials.username, password = credentials.password) }
        login(credentials.username, credentials.password)
    }

    private fun submit() {
        val state = _uiState.value
        login(state.username, state.password)
    }

    private fun login(rawUsername: String, password: String) {
        val username = StratosValidators.normalizeUsername(rawUsername)
        val usernameError = !StratosValidators.isValidUsername(username)
        val passwordError = !StratosValidators.isValidPassword(password)
        if (usernameError || passwordError) {
            _uiState.update {
                it.copy(
                    usernameErrorRes = if (usernameError) R.string.stratos_error_username_format else null,
                    passwordErrorRes = if (passwordError) R.string.stratos_error_password_format else null,
                )
            }
            return
        }

        if (_uiState.value.isLoading) return
        _uiState.update { it.copy(isLoading = true, generalErrorRes = null) }

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    StratosApiProvider.get().login(
                        username = username,
                        password = password,
                        deviceId = StratosSession.deviceId(app.applicationContext),
                        deviceName = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
                    )
                }.getOrElse { StratosLoginResult.Failure(it.message) }
            }

            when (result) {
                is StratosLoginResult.Success -> {
                    StratosSession.onLoginSuccess(result.token, username, result.user, result.admin)
                    // Seed the server fleet right after login (user can always refresh later).
                    withContext(Dispatchers.IO) {
                        runCatching {
                            val servers = StratosApiProvider.get().fetchServers(result.token)
                            if (servers.isNotEmpty()) {
                                StratosSession.saveServers(servers)
                                StratosServersStore.sync(servers)
                            }
                        }
                    }
                    StratosSync.start(app.applicationContext)
                    _uiState.update { it.copy(isLoading = false) }
                    _events.trySend(StratosLoginEvent.NavigateHome)
                }

                StratosLoginResult.DeviceConflict -> _uiState.update {
                    it.copy(isLoading = false, generalErrorRes = R.string.stratos_error_device_conflict)
                }

                StratosLoginResult.InvalidCredentials -> _uiState.update {
                    it.copy(isLoading = false, generalErrorRes = R.string.stratos_error_invalid_credentials)
                }

                is StratosLoginResult.Failure -> _uiState.update {
                    it.copy(isLoading = false, generalErrorRes = R.string.stratos_error_network)
                }
            }
        }
    }

    companion object {
        fun factory(app: android.app.Application) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                StratosLoginViewModel(app) as T
        }
    }
}

// --------------------------------------------------------------------------------------
// Activity
// --------------------------------------------------------------------------------------

class StratosLoginActivity : HelperBaseComponentActivity() {

    private val viewModel: StratosLoginViewModel by viewModels {
        StratosLoginViewModel.factory(application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Already signed in: route straight to the home screen.
        if (StratosSession.isLoggedIn) {
            navigateHome()
            return
        }

        lifecycleScopeCollectEvents()
    }

    private fun lifecycleScopeCollectEvents() {
        lifecycleScope.launch {
            viewModel.events.collect { event ->
                when (event) {
                    StratosLoginEvent.NavigateHome -> navigateHome()
                    is StratosLoginEvent.GeneralError -> Unit // message surfaced via uiState
                }
            }
        }
    }

    private fun navigateHome() {
        startActivity(Intent(this, StratosHomeActivity::class.java))
        finish()
    }

    @androidx.compose.runtime.Composable
    override fun ScreenContent() {
        BackHandler { moveTaskToBack(false) }
        StratosLoginScreen(
            uiState = viewModel.uiState,
            onAction = viewModel::onAction,
            onScanQr = {
                launchQRCodeScanner { result ->
                    if (result != null) viewModel.onAction(StratosLoginAction.SubmitQr(result))
                }
            },
        )
    }
}
