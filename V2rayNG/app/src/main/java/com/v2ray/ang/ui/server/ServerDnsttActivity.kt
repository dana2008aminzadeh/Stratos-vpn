package com.v2ray.ang.ui.server

import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.fmt.DnsttFmt
import com.v2ray.ang.ui.compose.FormTextField

class ServerDnsttActivity : BaseServerActivity() {

    override val serverConfigType: EConfigType = EConfigType.DNSTT

    @Composable
    override fun ScreenContent() {
        val uiState = rememberSaveable(saver = ServerUiState.Saver) {
            ServerUiState.from(
                initialConfig = initialConfig
            )
        }.apply {
            configType = EConfigType.DNSTT
        }

        ServerEditorScaffold(
            title = serverConfigType.toString(),
            onSaveClick = { saveServer(uiState) }
        ) {
            CommonBasicFields(uiState)
            DnsttProtocolFields(uiState)
        }
    }

    override fun validateProtocolConfig(config: ProfileItem): Boolean {
        if (!DnsttFmt.isValidPubKey(config.publicKey)) {
            return false
        }
        config.host = DnsttFmt.normalizeResolver(config.host.orEmpty())
        config.serverPort = DnsttFmt.resolverPort(config.host.orEmpty()).toString()
        return true
    }

    @Composable
    private fun DnsttProtocolFields(state: ServerUiState) {
        FormTextField(
            label = stringResource(R.string.server_lab_dnstt_pubkey),
            value = state.publicKey,
            onValueChange = { state.publicKey = it.trim().lowercase() },
            isError = state.publicKey.isNotEmpty() && !DnsttFmt.isValidPubKey(state.publicKey),
            supportingText = stringResource(R.string.server_lab_dnstt_pubkey_hint),
        )
        FormTextField(
            label = stringResource(R.string.server_lab_dnstt_resolver),
            value = state.host,
            onValueChange = { state.host = it },
            placeholder = "udp:1.1.1.1",
            supportingText = stringResource(R.string.server_lab_dnstt_resolver_hint),
        )
        FormTextField(
            label = stringResource(R.string.server_lab_tunnel_mtu),
            value = state.mtu,
            onValueChange = { state.mtu = it },
            keyboardType = KeyboardType.Number,
        )
    }
}
