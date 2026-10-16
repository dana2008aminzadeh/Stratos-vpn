package com.v2ray.ang.ui.server

import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.fmt.SshFmt
import com.v2ray.ang.ui.compose.FormDropdownField
import com.v2ray.ang.ui.compose.FormTextField
import com.v2ray.ang.ui.compose.SettingsSwitchItem

class ServerSshActivity : BaseServerActivity() {

    override val serverConfigType: EConfigType = EConfigType.SSH

    @Composable
    override fun ScreenContent() {
        val uiState = rememberSaveable(saver = ServerUiState.Saver) {
            ServerUiState.from(
                initialConfig = initialConfig
            )
        }.apply {
            configType = EConfigType.SSH
        }

        ServerEditorScaffold(
            title = serverConfigType.toString(),
            onSaveClick = { saveServer(uiState) }
        ) {
            CommonBasicFields(uiState)
            SshProtocolFields(uiState)
        }
    }

    override fun validateProtocolConfig(config: ProfileItem): Boolean {
        if (config.username.isNullOrBlank()) {
            return false
        }
        return SshFmt.isComplete(config)
    }

    @Composable
    private fun SshProtocolFields(state: ServerUiState) {
        FormTextField(
            label = stringResource(R.string.server_lab_ssh_username),
            value = state.username,
            onValueChange = { state.username = it },
        )
        FormDropdownField(
            label = stringResource(R.string.server_lab_ssh_auth),
            value = state.method.ifBlank { AppConfig.SSH_AUTH_PASSWORD },
            options = listOf(AppConfig.SSH_AUTH_PASSWORD, AppConfig.SSH_AUTH_KEY),
            onValueChange = { state.method = it },
        )
        if (state.method == AppConfig.SSH_AUTH_KEY) {
            FormTextField(
                label = stringResource(R.string.server_lab_ssh_private_key),
                value = state.secretKey,
                onValueChange = { state.secretKey = it },
                maxLines = 12,
                supportingText = stringResource(R.string.server_lab_ssh_private_key_hint),
            )
        } else {
            FormTextField(
                label = stringResource(R.string.server_lab_ssh_password),
                value = state.password,
                onValueChange = { state.password = it },
                isError = state.isPasswordError,
            )
        }
        FormTextField(
            label = stringResource(R.string.server_lab_ssh_host_key),
            value = state.publicKey,
            onValueChange = { state.publicKey = it },
            supportingText = stringResource(R.string.server_lab_ssh_host_key_hint),
        )
        SettingsSwitchItem(
            title = stringResource(R.string.server_lab_allow_insecure),
            summary = stringResource(R.string.server_lab_ssh_insecure_hint),
            checked = state.allowInsecure,
            onCheckedChange = { state.allowInsecure = it },
        )
    }
}
