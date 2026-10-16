package com.v2ray.ang.ui.server

import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.ui.compose.FormDropdownField
import com.v2ray.ang.ui.compose.FormTextField
import com.v2ray.ang.ui.compose.SettingsSwitchItem

class ServerLightwayActivity : BaseServerActivity() {

    override val serverConfigType: EConfigType = EConfigType.LIGHTWAY

    @Composable
    override fun ScreenContent() {
        val uiState = rememberSaveable(saver = ServerUiState.Saver) {
            ServerUiState.from(
                initialConfig = initialConfig
            )
        }.apply {
            configType = EConfigType.LIGHTWAY
        }

        ServerEditorScaffold(
            title = serverConfigType.toString(),
            onSaveClick = { saveServer(uiState) }
        ) {
            CommonBasicFields(uiState)
            LightwayProtocolFields(uiState)
        }
    }

    override fun validateProtocolConfig(config: ProfileItem): Boolean {
        if (config.password.isNullOrBlank()) {
            return false
        }
        return true
    }

    @Composable
    private fun LightwayProtocolFields(state: ServerUiState) {
        FormTextField(
            label = stringResource(R.string.server_lab_id3),
            value = state.password,
            onValueChange = { state.password = it },
            isError = state.isPasswordError,
        )
        FormDropdownField(
            label = stringResource(R.string.server_lab_lightway_proto),
            value = state.network,
            options = listOf(AppConfig.LIGHTWAY_PROTO_UDP, AppConfig.LIGHTWAY_PROTO_TCP),
            onValueChange = { state.network = it },
        )
        FormDropdownField(
            label = stringResource(R.string.server_lab_lightway_cipher),
            value = state.method.ifBlank { AppConfig.LIGHTWAY_CIPHER_AES },
            options = listOf(AppConfig.LIGHTWAY_CIPHER_AES, AppConfig.LIGHTWAY_CIPHER_CHACHA),
            onValueChange = { state.method = it },
        )
        FormTextField(
            label = stringResource(R.string.server_lab_sni),
            value = state.sni,
            onValueChange = { state.sni = it },
        )
        FormTextField(
            label = stringResource(R.string.server_lab_lightway_obfs),
            value = state.obfsPassword,
            onValueChange = { state.obfsPassword = it },
        )
        FormTextField(
            label = stringResource(R.string.server_lab_port_hop),
            value = state.portHopping,
            onValueChange = { state.portHopping = it },
        )
        FormTextField(
            label = stringResource(R.string.server_lab_tunnel_mtu),
            value = state.mtu,
            onValueChange = { state.mtu = it },
            keyboardType = KeyboardType.Number,
        )
        SettingsSwitchItem(
            title = stringResource(R.string.server_lab_allow_insecure),
            checked = state.allowInsecure,
            onCheckedChange = { state.allowInsecure = it },
        )
    }
}
