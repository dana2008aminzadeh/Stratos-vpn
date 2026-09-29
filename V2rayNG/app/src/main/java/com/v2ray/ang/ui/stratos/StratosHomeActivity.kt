package com.v2ray.ang.ui.stratos

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.VpnService
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import com.v2ray.ang.AngApplication
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.core.LauncherManager
import com.v2ray.ang.dto.UrlContentRequest
import com.v2ray.ang.enums.PermissionType
import com.v2ray.ang.extension.serializable
import com.v2ray.ang.extension.toast
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.handler.SpeedtestManager
import com.v2ray.ang.helper.MessageHelper
import com.v2ray.ang.stratos.StratosSession
import com.v2ray.ang.stratos.StratosSettingsController
import com.v2ray.ang.stratos.StratosSync
import com.v2ray.ang.stratos.StratosTrafficUpdate
import com.v2ray.ang.ui.base.HelperBaseComponentActivity
import com.v2ray.ang.util.HttpUtil
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.Utils
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

enum class StratosConnectState { Disconnected, Connecting, Connected, Stopping }

data class StratosHomeUiState(
    val connectState: StratosConnectState = StratosConnectState.Disconnected,
    val username: String = "",
    val dataLimitBytes: Long = 0L,
    val usedBytes: Long = 0L,
    val expireAtMillis: Long = 0L,
    val isUnlimitedData: Boolean = false,
    val blockedReason: String = StratosSession.BLOCKED_NONE,
    val traffic: StratosTrafficUpdate? = null,
    val downSpeedHistory: List<Float> = emptyList(),
    val upSpeedHistory: List<Float> = emptyList(),
    val ipLocal: String = "",
    val ipLocalLoading: Boolean = false,
    val ipVpn: String = "",
    val ipVpnCountry: String = "",
    val ipVpnLoading: Boolean = false,
    val selectedServerRemark: String = "",
    val selectedServerCountry: String = "",
    val dnsPresetId: String = "default",
    val renewUrl: String = "",
    val websiteUrl: String = "",
    val telegramUrl: String = "",
    val isSyncing: Boolean = false,
    val connectedSinceMillis: Long = 0L,
    val showChangePassword: Boolean = false,
    val passwordChangeBusy: Boolean = false,
    val showLogoutConfirm: Boolean = false,
    val showKickedDialog: Boolean = false,
) {
    val remainingBytes: Long
        get() = if (isUnlimitedData) Long.MAX_VALUE else (dataLimitBytes - usedBytes).coerceAtLeast(0L)

    val remainingFraction: Float
        get() = when {
            isUnlimitedData -> 1f
            dataLimitBytes <= 0L -> 1f
            else -> ((dataLimitBytes - usedBytes).coerceAtLeast(0L).toDouble() / dataLimitBytes.toDouble())
                .toFloat().coerceIn(0f, 1f)
        }

    val remainingDays: Long
        get() = if (expireAtMillis <= 0L) Long.MAX_VALUE
        else ((expireAtMillis - System.currentTimeMillis()).coerceAtLeast(0L)) / (24L * 60 * 60 * 1000)

    val isExpired: Boolean
        get() = blockedReason == StratosSession.BLOCKED_EXPIRED ||
                blockedReason == StratosSession.BLOCKED_NO_DATA ||
                blockedReason == StratosSession.BLOCKED_DISABLED

    val canConnect: Boolean
        get() = blockedReason == StratosSession.BLOCKED_NONE
}

sealed interface StratosHomeAction {
    data object ToggleConnect : StratosHomeAction
    data object Refresh : StratosHomeAction
    data object VpnPermissionGranted : StratosHomeAction
    data object ShowChangePassword : StratosHomeAction
    data object HideChangePassword : StratosHomeAction
    data class SubmitChangePassword(val current: String, val new: String) : StratosHomeAction
    data object ShowLogoutConfirm : StratosHomeAction
    data object HideLogoutConfirm : StratosHomeAction
    data object ConfirmLogout : StratosHomeAction
    data object DismissKick : StratosHomeAction
}

sealed interface StratosHomeEvent {
    data object RequestVpnPermission : StratosHomeEvent
    data object NavigateLogin : StratosHomeEvent
    data class OpenUrl(val url: String) : StratosHomeEvent
    data class ToastRes(val res: Int) : StratosHomeEvent
}

// --------------------------------------------------------------------------------------
// ViewModel
// --------------------------------------------------------------------------------------

class StratosHomeViewModel(private val app: AngApplication) : ViewModel() {

    private val _uiState = MutableStateFlow(StratosHomeUiState())
    val uiState: StateFlow<StratosHomeUiState> = _uiState.asStateFlow()

    private val _events = Channel<StratosHomeEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val speedDownWindow = ArrayDeque<Float>()
    private val speedUpWindow = ArrayDeque<Float>()
    private val maxHistory = 60

    private var closed = false

    private val serviceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val safeIntent = intent ?: return
            when (safeIntent.getIntExtra("key", 0)) {
                AppConfig.MSG_STATE_RUNNING -> {
                    _uiState.update {
                        it.copy(
                            connectState = StratosConnectState.Connected,
                            connectedSinceMillis = System.currentTimeMillis(),
                        )
                    }
                    refreshVpnIp()
                }

                AppConfig.MSG_STATE_NOT_RUNNING,
                AppConfig.MSG_STATE_STOP_SUCCESS -> {
                    _uiState.update {
                        it.copy(
                            connectState = StratosConnectState.Disconnected,
                            connectedSinceMillis = 0L,
                            traffic = null,
                            ipVpn = "",
                            ipVpnCountry = "",
                            downSpeedHistory = emptyList(),
                            upSpeedHistory = emptyList(),
                        )
                    }
                    clearSpeedHistory()
                    refreshLocalIp()
                }

                AppConfig.MSG_STATE_START_SUCCESS -> Unit
                AppConfig.MSG_STATE_START_FAILURE -> {
                    _uiState.update { it.copy(connectState = StratosConnectState.Disconnected) }
                    _events.trySend(StratosHomeEvent.ToastRes(R.string.toast_config_file_invalid))
                }

                AppConfig.MSG_STRATOS_TRAFFIC -> safeIntent
                    .serializable<StratosTrafficUpdate>("content")
                    ?.let(::onTraffic)

                AppConfig.MSG_STRATOS_BLOCKED -> onBlocked(
                    safeIntent.getStringExtra("content").orEmpty(),
                )
            }
        }
    }

    init {
        ContextCompat.registerReceiver(
            app,
            serviceReceiver,
            IntentFilter(AppConfig.BROADCAST_ACTION_ACTIVITY),
            Utils.receiverFlags(),
        )
        MessageHelper.sendMsg2Service(app, AppConfig.MSG_REGISTER_CLIENT, "")

        // Mirror the session/account state into the screen model
        viewModelScope.launch {
            StratosSession.userState.collect { user ->
                syncUserIntoState(recomputeUsed = true)
            }
        }
        viewModelScope.launch {
            StratosSession.localUsedState.collect { syncUserIntoState(recomputeUsed = true) }
        }
        viewModelScope.launch {
            StratosSession.blockedState.collect { reason ->
                if (reason == StratosSession.BLOCKED_KICKED) {
                    _uiState.update { it.copy(showKickedDialog = true) }
                } else {
                    syncUserIntoState(recomputeUsed = true)
                }
            }
        }
        viewModelScope.launch {
            StratosSession.adminState.collect { admin ->
                _uiState.update {
                    it.copy(
                        renewUrl = admin.renewUrl,
                        websiteUrl = admin.websiteUrl,
                        telegramUrl = admin.telegramUrl,
                    )
                }
            }
        }
        viewModelScope.launch {
            StratosSync.isSyncing.collect { s -> _uiState.update { it.copy(isSyncing = s) } }
        }

        syncUserIntoState(recomputeUsed = true)
        refreshLocalIp()
        refreshSelectedServer()
        refreshDns()
    }

    override fun onCleared() {
        if (!closed) {
            closed = true
            runCatching { app.unregisterReceiver(serviceReceiver) }
            runCatching { MessageHelper.sendMsg2Service(app, AppConfig.MSG_UNREGISTER_CLIENT, "") }
        }
        super.onCleared()
    }

    private fun syncUserIntoState(recomputeUsed: Boolean) {
        val user = StratosSession.effectiveUser()
        val blocked = StratosSession.blockedReason()
        _uiState.update { state ->
            state.copy(
                username = user?.username.orEmpty(),
                dataLimitBytes = user?.dataLimitBytes ?: 0L,
                usedBytes = user?.usedBytes ?: state.usedBytes,
                expireAtMillis = user?.expireAtMillis ?: 0L,
                isUnlimitedData = user?.isUnlimitedData ?: false,
                blockedReason = blocked,
            )
        }
    }

    fun onAction(action: StratosHomeAction) {
        when (action) {
            StratosHomeAction.ToggleConnect -> toggleConnect()
            StratosHomeAction.Refresh -> refreshNow(userInitiated = true)
            StratosHomeAction.VpnPermissionGranted -> startConnect()
            StratosHomeAction.ShowChangePassword -> _uiState.update { it.copy(showChangePassword = true) }
            StratosHomeAction.HideChangePassword -> _uiState.update {
                it.copy(showChangePassword = false, passwordChangeBusy = false)
            }

            is StratosHomeAction.SubmitChangePassword -> submitPasswordChange(action.current, action.new)
            StratosHomeAction.ShowLogoutConfirm -> _uiState.update { it.copy(showLogoutConfirm = true) }
            StratosHomeAction.HideLogoutConfirm -> _uiState.update { it.copy(showLogoutConfirm = false) }
            StratosHomeAction.ConfirmLogout -> logout()
            StratosHomeAction.DismissKick -> _uiState.update { it.copy(showKickedDialog = false) }
        }
    }

    // ---------------------------------------------------------- connect

    private fun toggleConnect() {
        when (_uiState.value.connectState) {
            StratosConnectState.Connected,
            StratosConnectState.Connecting -> {
                _uiState.update { it.copy(connectState = StratosConnectState.Stopping) }
                LauncherManager.stopService(app)
            }

            else -> {
                if (!StratosSession.isConnectAllowed()) {
                    _events.trySend(StratosHomeEvent.ToastRes(R.string.stratos_expired_cannot_connect))
                    refreshNow(userInitiated = false)
                    return
                }
                if (MmkvManager.getSelectServer().isNullOrEmpty()) {
                    // no fleet available yet: pull it once, then retry via UI
                    _events.trySend(StratosHomeEvent.ToastRes(R.string.stratos_no_servers))
                    refreshNow(userInitiated = false, refreshServers = true)
                    return
                }
                _events.trySend(StratosHomeEvent.RequestVpnPermission)
            }
        }
    }

    private fun startConnect() {
        if (!StratosSession.isConnectAllowed()) {
            _events.trySend(StratosHomeEvent.ToastRes(R.string.stratos_expired_cannot_connect))
            return
        }
        _uiState.update { it.copy(connectState = StratosConnectState.Connecting) }
        viewModelScope.launch(Dispatchers.Default) {
            LauncherManager.startService(app)
        }
    }

    // ---------------------------------------------------------- refresh / sync

    fun refreshNow(userInitiated: Boolean, refreshServers: Boolean = true) {
        if (_uiState.value.isSyncing) return
        viewModelScope.launch(Dispatchers.IO) {
            val ok = StratosSync.syncNow(app.applicationContext, refreshServers = refreshServers)
            withContext(Dispatchers.Main) {
                refreshSelectedServer()
                refreshDns()
                if (userInitiated && !ok) {
                    _events.trySend(StratosHomeEvent.ToastRes(R.string.stratos_error_network))
                }
            }
        }
    }

    private fun refreshSelectedServer() {
        val guid = MmkvManager.getSelectServer()
        val config = guid?.let { MmkvManager.decodeServerConfig(it) }
        val server = StratosSession.serversState.value.firstOrNull {
            it.id.isNotEmpty() && com.v2ray.ang.stratos.StratosServersStore.guidFor(it.id) == guid
        }
        _uiState.update {
            it.copy(
                selectedServerRemark = config?.remarks.orEmpty(),
                selectedServerCountry = server?.countryCode.orEmpty(),
            )
        }
    }

    private fun refreshDns() {
        _uiState.update { it.copy(dnsPresetId = StratosSettingsController.currentDnsPresetId()) }
    }

    // ---------------------------------------------------------- traffic + quota

    private fun onTraffic(update: StratosTrafficUpdate) {
        pushSpeedSample(speedDownWindow, update.downSpeedBytesPerSec.toFloat())
        pushSpeedSample(speedUpWindow, update.upSpeedBytesPerSec.toFloat())
        _uiState.update {
            it.copy(
                traffic = update,
                usedBytes = update.accountUsedBytes,
                downSpeedHistory = speedDownWindow.toList(),
                upSpeedHistory = speedUpWindow.toList(),
            )
        }
    }

    private fun pushSpeedSample(window: ArrayDeque<Float>, value: Float) {
        if (window.size >= maxHistory) window.removeFirst()
        window.addLast(value)
    }

    private fun clearSpeedHistory() {
        speedDownWindow.clear()
        speedUpWindow.clear()
    }

    private fun onBlocked(reason: String) {
        if (reason == StratosSession.BLOCKED_KICKED) {
            _uiState.update { it.copy(showKickedDialog = true, connectState = StratosConnectState.Disconnected) }
        }
        _uiState.update { it.copy(connectState = StratosConnectState.Disconnected) }
        syncUserIntoState(true)
    }

    // ---------------------------------------------------------- IPs

    private fun refreshLocalIp() {
        if (_uiState.value.ipLocalLoading) return
        _uiState.update { it.copy(ipLocalLoading = true) }
        viewModelScope.launch(Dispatchers.IO) {
            val ip = fetchIpDirect()
            _uiState.update { it.copy(ipLocal = ip.orEmpty(), ipLocalLoading = false) }
        }
    }

    private fun refreshVpnIp() {
        _uiState.update { it.copy(ipVpnLoading = true) }
        viewModelScope.launch(Dispatchers.IO) {
            val info = runCatching { SpeedtestManager.getRemoteIPInfo() }.getOrNull()
            _uiState.update {
                it.copy(
                    ipVpn = info?.ipAddress.orEmpty(),
                    ipVpnCountry = info?.country.orEmpty(),
                    ipVpnLoading = false,
                )
            }
        }
    }

    private fun fetchIpDirect(): String? {
        val url = MmkvManager.decodeSettingsString(AppConfig.PREF_IP_API_URL)
            .takeIf { !it.isNullOrBlank() } ?: AppConfig.IP_API_URL
        val content = HttpUtil.getUrlContent(
            UrlContentRequest(url = url, timeout = 5000, httpPort = 0),
        ) ?: return null
        val info = JsonUtil.fromJsonSafe(content, com.v2ray.ang.dto.IPAPIInfo::class.java) ?: return null
        return listOf(info.ip, info.clientIp, info.ip_addr, info.query)
            .firstOrNull { !it.isNullOrBlank() }
    }

    // ---------------------------------------------------------- password / logout

    private fun submitPasswordChange(current: String, new: String) {
        if (_uiState.value.passwordChangeBusy) return
        _uiState.update { it.copy(passwordChangeBusy = true) }
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                val token = StratosSession.token() ?: return@withContext false
                runCatching {
                    com.v2ray.ang.stratos.StratosApiProvider.get().changePassword(token, current, new)
                }.getOrDefault(false)
            }
            if (ok) {
                _uiState.update { it.copy(passwordChangeBusy = false, showChangePassword = false) }
                _events.trySend(StratosHomeEvent.ToastRes(R.string.stratos_password_changed))
            } else {
                _uiState.update { it.copy(passwordChangeBusy = false) }
                _events.trySend(StratosHomeEvent.ToastRes(R.string.stratos_password_change_failed))
            }
        }
    }

    private fun logout() {
        _uiState.update { it.copy(showLogoutConfirm = false) }
        LauncherManager.stopService(app)
        val token = StratosSession.token()
        StratosSession.logout()
        if (!token.isNullOrBlank()) {
            viewModelScope.launch(Dispatchers.IO) {
                runCatching { com.v2ray.ang.stratos.StratosApiProvider.get().logout(token) }
            }
        }
        _events.trySend(StratosHomeEvent.NavigateLogin)
    }

    fun openRenew() {
        val url = _uiState.value.renewUrl.ifBlank { _uiState.value.websiteUrl }
        if (url.isBlank()) {
            _events.trySend(StratosHomeEvent.ToastRes(R.string.stratos_error_network))
        } else {
            _events.trySend(StratosHomeEvent.OpenUrl(url))
        }
    }

    fun openWebsite() = _uiState.value.websiteUrl.takeIf { it.isNotBlank() }
        ?.let { _events.trySend(StratosHomeEvent.OpenUrl(it)) }

    fun openTelegram() = _uiState.value.telegramUrl.takeIf { it.isNotBlank() }
        ?.let { _events.trySend(StratosHomeEvent.OpenUrl(it)) }

    companion object {
        fun factory(app: AngApplication) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                StratosHomeViewModel(app) as T
        }
    }
}

// --------------------------------------------------------------------------------------
// Activity
// --------------------------------------------------------------------------------------

class StratosHomeActivity : HelperBaseComponentActivity() {

    private val viewModel: StratosHomeViewModel by viewModels {
        StratosHomeViewModel.factory(application as AngApplication)
    }

    private val vpnPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                viewModel.onAction(StratosHomeAction.VpnPermissionGranted)
            }
        }

    private val serversLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            // Auto connect from the server list: adopt the fastest server and connect.
            val autoGuid = result.data?.getStringExtra(StratosServersActivity.EXTRA_AUTOCONNECT_GUID)
            if (!autoGuid.isNullOrBlank()) {
                MmkvManager.setSelectServer(autoGuid)
                viewModel.refreshNow(userInitiated = false, refreshServers = false)
                viewModel.onAction(StratosHomeAction.ToggleConnect)
                return@registerForActivityResult
            }
            // selection changed or screen closed: reflect the chosen server/country
            viewModel.refreshNow(userInitiated = false, refreshServers = false)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Auth gate: without a session there is no home screen.
        if (!StratosSession.isLoggedIn) {
            startActivity(Intent(this, StratosLoginActivity::class.java))
            finish()
            return
        }

        // The Stratos VPN notification (live speeds + stop) is mandatory.
        checkAndRequestPermission(PermissionType.POST_NOTIFICATIONS) {}

        lifecycleScope.launch {
            viewModel.events.collect { event ->
                when (event) {
                    StratosHomeEvent.RequestVpnPermission -> requestVpnPermission()
                    StratosHomeEvent.NavigateLogin -> navigateLogin()
                    is StratosHomeEvent.OpenUrl -> Utils.openUri(this@StratosHomeActivity, event.url)
                    is StratosHomeEvent.ToastRes -> toast(event.res)
                }
            }
        }
    }

    private fun requestVpnPermission() {
        if (!SettingsManager.isVpnMode()) {
            viewModel.onAction(StratosHomeAction.VpnPermissionGranted)
            return
        }
        val intent = VpnService.prepare(this)
        if (intent == null) {
            viewModel.onAction(StratosHomeAction.VpnPermissionGranted)
        } else {
            vpnPermissionLauncher.launch(intent)
        }
    }

    private fun navigateLogin() {
        startActivity(
            Intent(this, StratosLoginActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        )
        finish()
    }

    override fun onResume() {
        super.onResume()
        if (StratosSession.isLoggedIn) {
            viewModel.refreshNow(userInitiated = false, refreshServers = false)
        }
    }

    @Composable
    override fun ScreenContent() {
        BackHandler { moveTaskToBack(false) }
        StratosHomeScreen(
            uiState = viewModel.uiState,
            onAction = viewModel::onAction,
            onOpenServers = {
                serversLauncher.launch(Intent(this, StratosServersActivity::class.java))
            },
            onOpenDns = { startActivity(Intent(this, StratosDnsActivity::class.java)) },
            onOpenSettings = { startActivity(Intent(this, StratosSettingsActivity::class.java)) },
            onOpenAbout = { startActivity(Intent(this, StratosAboutActivity::class.java)) },
            onOpenRenew = viewModel::openRenew,
            onOpenWebsite = viewModel::openWebsite,
            onOpenTelegram = viewModel::openTelegram,
        )
    }
}
