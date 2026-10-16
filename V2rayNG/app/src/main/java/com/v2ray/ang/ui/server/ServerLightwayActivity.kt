package com.v2ray.ang.ui.server

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.extension.toast
import com.v2ray.ang.fmt.LightwayFmt
import com.v2ray.ang.ui.compose.FormDropdownField
import com.v2ray.ang.ui.compose.FormTextField

/**
 * Editor for Lightway profiles: the dedicated Lightway engine carries the whole
 * tunnel for this profile inside the VPN service (Xray is not involved).
 */
class ServerLightwayActivity : BaseServerActivity() {

    override val serverConfigType: EConfigType = EConfigType.LIGHTWAY

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
            LightwayProtocolFields(uiState)
        }
    }

    @Composable
    private fun LightwayProtocolFields(state: ServerUiState) {
        FormTextField(
            stringResource(R.string.server_lab_security4),
            state.username,
            { state.username = it }
        )
        FormTextField(
            stringResource(R.string.server_lab_id3),
            state.password,
            { state.password = it }
        )
        FormDropdownField(
            stringResource(R.string.server_lab_lightway_mode),
            state.network,
            listOf(LightwayFmt.DEFAULT_MODE, "tcp"),
            { state.network = it }
        )
        FormTextField(
            stringResource(R.string.server_lab_cipher),
            state.method,
            { state.method = it }
        )
        FormTextField(
            stringResource(R.string.server_lab_server_dn),
            state.sni,
            { state.sni = it }
        )
        FormTextField(
            stringResource(R.string.server_lab_ca_cert),
            state.path,
            { state.path = it }
        )
    }

    override fun validateProtocolConfig(config: ProfileItem): Boolean {
        if (config.username.isNullOrBlank()) {
            toast(R.string.server_lab_security4)
            return false
        }
        return true
    }
}
