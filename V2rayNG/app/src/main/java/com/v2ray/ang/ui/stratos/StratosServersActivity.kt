package com.v2ray.ang.ui.stratos

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.compose.BackHandler
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
import com.v2ray.ang.dto.RealPingResult
import com.v2ray.ang.dto.TestServiceMessage
import com.v2ray.ang.extension.serializable
import com.v2ray.ang.extension.toast
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.helper.MessageHelper
import com.v2ray.ang.stratos.StratosServer
import com.v2ray.ang.stratos.StratosServersStore
import com.v2ray.ang.stratos.StratosSession
import com.v2ray.ang.stratos.StratosSync
import com.v2ray.ang.ui.base.HelperBaseComponentActivity
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// --------------------------------------------------------------------------------------
// Contract
// --------------------------------------------------------------------------------------

data class StratosServerRow(
    val server: StratosServer,
    val guid: String,
    val remark: String,
    val delayMs: Long?,
)

data class StratosCountryGroup(
    val countryCode: String,
    val countryName: String,
    val servers: List<StratosServerRow>,
) {
    val bestDelay: Long? get() = servers.mapNotNull { it.delayMs }.filter { it > 0 }.minOrNull()
}

data class StratosServersUiState(
    val groups: List<StratosCountryGroup> = emptyList(),
    val selectedGuid: String? = null,
    val expandedCountries: Set<String> = emptySet(),
    val isTesting: Boolean = false,
    val isAutoConnecting: Boolean = false,
    val isRefreshing: Boolean = false,
    val isEmpty: Boolean = false,
)

sealed interface StratosServersAction {
    data class ToggleCountry(val code: String) : StratosServersAction
    data class Select(val server: StratosServer) : StratosServersAction
    data object TestAll : StratosServersAction
    data object AutoConnect : StratosServersAction
    data object Refresh : StratosServersAction
}

sealed interface StratosServersEvent {
    data object FinishScreen : StratosServersEvent
    data class FinishWithAutoConnect(val guid: String) : StratosServersEvent
    data class ToastRes(val res: Int) : StratosServersEvent
}

// --------------------------------------------------------------------------------------
// ViewModel
// --------------------------------------------------------------------------------------

class StratosServersViewModel(private val app: AngApplication) : ViewModel() {

    private val _uiState = MutableStateFlow(StratosServersUiState())
    val uiState: StateFlow<StratosServersUiState> = _uiState.asStateFlow()

    private val _events = Channel<StratosServersEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val delays = mutableMapOf<String, Long>()
    private var closed = false

    private val testReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val safeIntent = intent ?: return
            when (safeIntent.getIntExtra("key", 0)) {
                AppConfig.MSG_MEASURE_CONFIG_SUCCESS -> safeIntent
                    .serializable<RealPingResult>("content")
                    ?.let { result ->
                        delays[result.guid] = result.delayMillis
                        rebuildGroups()
                    }

                AppConfig.MSG_MEASURE_CONFIG_FINISH,
                AppConfig.MSG_MEASURE_CONFIG_CANCEL -> onTestFinished()
            }
        }
    }

    init {
        ContextCompat.registerReceiver(
            app,
            testReceiver,
            IntentFilter(AppConfig.BROADCAST_ACTION_ACTIVITY),
            Utils.receiverFlags(),
        )
        loadInitial()
    }

    override fun onCleared() {
        if (!closed) {
            closed = true
            runCatching { app.unregisterReceiver(testReceiver) }
        }
        super.onCleared()
    }

    private fun loadInitial() {
        val servers = StratosSession.serversState.value
        for (server in servers) {
            val guid = StratosServersStore.guidFor(server.id)
            val delay = MmkvManager.decodeServerAffiliationInfo(guid)?.testDelayMillis
            if (delay != null && delay != 0L) delays[guid] = delay
        }
        _uiState.update {
            it.copy(
                selectedGuid = MmkvManager.getSelectServer(),
                expandedCountries = setOfNotNull(currentCountryOfSelection()),
                isEmpty = servers.isEmpty(),
            )
        }
        rebuildGroups()
        if (servers.isEmpty()) {
            onAction(StratosServersAction.Refresh)
        }
    }

    private fun currentCountryOfSelection(): String? {
        val selected = MmkvManager.getSelectServer() ?: return null
        return StratosSession.serversState.value
            .firstOrNull { StratosServersStore.guidFor(it.id) == selected }
            ?.countryCode
    }

    fun onAction(action: StratosServersAction) {
        when (action) {
            is StratosServersAction.ToggleCountry -> _uiState.update { state ->
                val expanded = state.expandedCountries.toMutableSet()
                if (!expanded.add(action.code)) expanded.remove(action.code)
                state.copy(expandedCountries = expanded)
            }

            is StratosServersAction.Select -> select(action.server)
            StratosServersAction.TestAll -> startTest(autoConnect = false)
            StratosServersAction.AutoConnect -> startTest(autoConnect = true)
            StratosServersAction.Refresh -> refreshFleet()
        }
    }

    private fun select(server: StratosServer) {
        val guid = StratosServersStore.guidFor(server.id)
        if (guid == MmkvManager.getSelectServer()) {
            _events.trySend(StratosServersEvent.FinishScreen)
            return
        }
        MmkvManager.setSelectServer(guid)
        _uiState.update { it.copy(selectedGuid = guid) }
        // Running connection follows the new selection immediately.
        LauncherManager.restartService(app)
        _events.trySend(StratosServersEvent.FinishScreen)
    }

    private fun startTest(autoConnect: Boolean) {
        if (_uiState.value.isTesting) return
        val guids = StratosServersStore.currentGuids()
        if (guids.isEmpty()) {
            _events.trySend(StratosServersEvent.ToastRes(R.string.stratos_no_servers))
            return
        }
        delays.clear()
        rebuildGroups()
        _uiState.update {
            it.copy(isTesting = true, isAutoConnecting = autoConnect, expandedCountries = it.expandedCountries)
        }
        LogUtil.i(AppConfig.TAG, "Stratos ping test started (${guids.size} servers, auto=$autoConnect)")
        MessageHelper.sendMsg2TestService(
            app,
            TestServiceMessage(
                key = AppConfig.MSG_MEASURE_CONFIG_START,
                serverGuids = guids,
                onlyTcp = false, // real delay: full config measurement
            ),
        )
    }

    private fun onTestFinished() {
        val auto = _uiState.value.isAutoConnecting
        _uiState.update { it.copy(isTesting = false, isAutoConnecting = false) }
        if (!auto) return

        val best = delays.entries
            .filter { it.value > 0 }
            .minByOrNull { it.value }
        if (best == null) {
            _events.trySend(StratosServersEvent.ToastRes(R.string.stratos_ping_timeout))
            return
        }
        _events.trySend(StratosServersEvent.FinishWithAutoConnect(best.key))
    }

    private fun refreshFleet() {
        if (_uiState.value.isRefreshing) return
        _uiState.update { it.copy(isRefreshing = true) }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { StratosSync.syncNow(app.applicationContext, refreshServers = true) }
            launch(Dispatchers.Main) {
                _uiState.update { it.copy(isRefreshing = false) }
                rebuildGroups()
            }
        }
    }

    private fun rebuildGroups() {
        val servers = StratosSession.serversState.value
        val groups = servers
            .groupBy { it.countryCode.lowercase() }
            .map { (code, list) ->
                StratosCountryGroup(
                    countryCode = code,
                    countryName = list.firstOrNull()?.countryName ?: code.uppercase(),
                    servers = list.map { server ->
                        val guid = StratosServersStore.guidFor(server.id)
                        StratosServerRow(
                            server = server,
                            guid = guid,
                            remark = MmkvManager.decodeServerConfig(guid)?.remarks ?: server.name,
                            delayMs = delays[guid],
                        )
                    },
                )
            }
            .sortedBy { it.countryName }
        _uiState.update { it.copy(groups = groups, isEmpty = groups.isEmpty()) }
    }

    companion object {
        fun factory(app: AngApplication) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                StratosServersViewModel(app) as T
        }
    }
}

// --------------------------------------------------------------------------------------
// Activity
// --------------------------------------------------------------------------------------

class StratosServersActivity : HelperBaseComponentActivity() {

    private val viewModel: StratosServersViewModel by viewModels {
        StratosServersViewModel.factory(application as AngApplication)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            viewModel.events.collect { event ->
                when (event) {
                    StratosServersEvent.FinishScreen -> finish()
                    is StratosServersEvent.FinishWithAutoConnect -> {
                        setResult(
                            RESULT_OK,
                            Intent().putExtra(EXTRA_AUTOCONNECT_GUID, event.guid),
                        )
                        finish()
                    }

                    is StratosServersEvent.ToastRes -> toast(event.res)
                }
            }
        }
    }

    @Composable
    override fun ScreenContent() {
        BackHandler { finish() }
        StratosServersScreen(
            uiState = viewModel.uiState,
            onAction = viewModel::onAction,
            onBack = { finish() },
        )
    }

    companion object {
        const val EXTRA_AUTOCONNECT_GUID = "stratos_autoconnect_guid"
    }
}
