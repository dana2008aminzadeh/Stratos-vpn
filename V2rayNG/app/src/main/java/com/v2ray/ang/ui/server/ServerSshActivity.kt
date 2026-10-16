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
import com.v2ray.ang.fmt.SshFmt
import com.v2ray.ang.ui.compose.FormDropdownField
import com.v2ray.ang.ui.compose.FormTextField

/**
 * Editor for SSH profiles: the app opens an SSH session to the server and chains
 * the Xray core through a local loopback SOCKS relay for this endpoint.
 */
class ServerSshActivity : BaseServerActivity() {

    override val serverConfigType: EConfigType = EConfigType.SSH

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
            SshProtocolFields(uiState)
        }
    }

    @Composable
    private fun SshProtocolFields(state: ServerUiState) {
        FormTextField(
            stringResource(R.string.server_lab_security4),
            state.username,
            { state.username = it }
        )
        FormDropdownField(
            stringResource(R.string.server_lab_ssh_auth),
            state.method,
            listOf(SshFmt.AUTH_PASSWORD, SshFmt.AUTH_KEY),
            { state.method = it }
        )
        FormTextField(
            stringResource(R.string.server_lab_id3),
            state.password,
            { state.password = it },
            isError = state.isPasswordError
        )
        if (state.method == SshFmt.AUTH_KEY) {
            FormTextField(
                stringResource(R.string.server_lab_private_key),
                state.path,
                { state.path = it }
            )
        }
        FormTextField(
            stringResource(R.string.server_lab_host_key_pin),
            state.publicKey,
            { state.publicKey = it }
        )
    }

    override fun validateProtocolConfig(config: ProfileItem): Boolean {
        if (config.username.isNullOrBlank()) {
            toast(R.string.server_lab_security4)
            return false
        }
        if (config.method == SshFmt.AUTH_KEY && config.path.isNullOrBlank()) {
            toast(R.string.server_lab_private_key)
            return false
        }
        return true
    }
}
