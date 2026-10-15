package com.v2ray.ang.ui.server

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.extension.toast
import com.v2ray.ang.fmt.DnsttFmt
import com.v2ray.ang.ui.compose.FormDropdownField
import com.v2ray.ang.ui.compose.FormTextField

/**
 * Editor for DNSTT profiles: the app runs the dnstt DNS-tunnel client against the
 * configured domain/public key and the Xray core chains through its loopback relay.
 */
class ServerDnsttActivity : BaseServerActivity() {

    override val serverConfigType: EConfigType = EConfigType.DNSTT

    @Composable
    override fun ScreenContent() {
        val scope = rememberCoroutineScope()
        val uiState = rememberSaveable(saver = ServerUiState.Saver) {
            ServerUiState.from(
                initialConfig = initialConfig
            )
        }.apply {
            configType = serverConfigType
        }

        ServerEditorScaffold(
            title = serverConfigType.toString(),
            onSaveClick = { saveServer(uiState) }
        ) {
            CommonBasicFields(uiState)
            DnsttProtocolFields(uiState)
        }
    }

    @Composable
    private fun DnsttProtocolFields(state: ServerUiState) {
        FormTextField(
            stringResource(R.string.server_lab_dnstt_pubkey),
            state.publicKey,
            { state.publicKey = it }
        )
        FormDropdownField(
            stringResource(R.string.server_lab_dnstt_transport),
            state.method,
            listOf(DnsttFmt.TRANSPORT_UDP, DnsttFmt.TRANSPORT_DOH, DnsttFmt.TRANSPORT_DOT),
            { state.method = it }
        )
        when (state.method) {
            DnsttFmt.TRANSPORT_UDP -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FormTextField(
                    stringResource(R.string.server_lab_dnstt_resolver),
                    state.host,
                    { state.host = it }
                )
            }

            else -> FormTextField(
                stringResource(R.string.server_lab_dnstt_dns),
                state.path,
                { state.path = it }
            )
        }
    }

    override fun validateProtocolConfig(config: ProfileItem): Boolean {
        if (config.publicKey.isNullOrBlank()) {
            toast(R.string.server_lab_dnstt_pubkey)
            return false
        }
        val transport = config.method ?: DnsttFmt.TRANSPORT_UDP
        if (transport == DnsttFmt.TRANSPORT_UDP) {
            if (config.host.isNullOrBlank()) {
                toast(R.string.server_lab_dnstt_resolver)
                return false
            }
            if ((config.serverPort?.toIntOrNull() ?: 0) !in 1..65535) {
                toast(R.string.server_lab_port)
                return false
            }
        } else {
            if (config.path.isNullOrBlank()) {
                toast(R.string.server_lab_dnstt_dns)
                return false
            }
        }
        return true
    }
}
