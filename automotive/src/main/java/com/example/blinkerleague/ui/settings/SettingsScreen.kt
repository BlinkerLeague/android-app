package com.example.blinkerleague.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.blinkerleague.BlinkerLeagueApplication
import com.example.blinkerleague.BuildConfig
import com.example.blinkerleague.R
import com.example.blinkerleague.data.permission.RuntimePermission
import com.example.blinkerleague.domain.model.PermissionStatus
import com.example.blinkerleague.service.RecordingStatus
import com.example.blinkerleague.ui.common.rememberPermissionRequester
import com.example.blinkerleague.ui.theme.BlinkerLeagueTheme
import com.example.blinkerleague.ui.theme.OnSuccessContainer

private val SettingsViewModelFactory = viewModelFactory {
    initializer {
        val container = (this[APPLICATION_KEY] as BlinkerLeagueApplication).container
        SettingsViewModel(
            profile = container.driverRepository::profile,
            speedPermission = container.permissions.status(RuntimePermission.SPEED),
            recordingStatus = container.recordingStatus,
            version = BuildConfig.VERSION_NAME,
            usesServer = container.usesServer,
            loadLeagueVisible = { container.leagueRepository.current()?.visible },
            saveLeagueVisible = container.leagueRepository::setVisible,
        )
    }
}

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = viewModel(factory = SettingsViewModelFactory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val container = (context.applicationContext as BlinkerLeagueApplication).container
    val requestSpeed = rememberPermissionRequester(container.permissions, RuntimePermission.SPEED)
    SettingsContent(
        state = state,
        onRequestSpeed = requestSpeed,
        onLeagueVisibleChange = viewModel::setLeagueVisible,
        // 거부를 여러 번 하면 시스템이 요청 창을 더 띄우지 않으므로 앱 정보 화면으로 보냄
        onOpenAppSettings = {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        },
    )
}

@Composable
fun SettingsContent(
    state: SettingsUiState,
    onRequestSpeed: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onLeagueVisibleChange: (Boolean) -> Unit = {},
) {
    Row(
        Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Panel(Modifier.weight(1f).fillMaxHeight()) {
            Label(R.string.settings_profile)
            Line(R.string.settings_nickname, state.nickname ?: "—")
            Line(R.string.settings_region, state.region ?: "—")
            Text(stringResource(R.string.settings_profile_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Label(R.string.settings_app)
            Line(R.string.settings_version, state.version)
            Line(R.string.settings_mode, stringResource(if (state.usesServer) R.string.settings_mode_server else R.string.settings_mode_local))
            state.leagueVisible?.let { visible ->
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Label(R.string.settings_league)
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 64.dp).toggleable(
                        value = visible,
                        enabled = !state.leagueVisibilitySaving,
                        role = Role.Switch,
                        onValueChange = onLeagueVisibleChange,
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.settings_league_visible), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Switch(checked = visible, onCheckedChange = null, enabled = !state.leagueVisibilitySaving)
                }
                Text(stringResource(R.string.settings_league_visible_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (state.leagueVisibilityFailed) {
                    Text(stringResource(R.string.settings_league_failed), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
            }
        }
        Panel(Modifier.weight(1f).fillMaxHeight()) {
            Label(R.string.settings_permissions)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.onboarding_cap_speed), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text(
                    stringResource(
                        when (state.speedPermission) {
                            PermissionStatus.GRANTED -> R.string.onboarding_speed_granted
                            PermissionStatus.DENIED -> R.string.settings_permission_denied
                            PermissionStatus.NOT_REQUESTED -> R.string.settings_permission_not_requested
                        },
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (state.speedPermission == PermissionStatus.GRANTED) OnSuccessContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(stringResource(R.string.onboarding_speed_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (state.speedPermission != PermissionStatus.GRANTED) {
                OutlinedButton(onClick = onRequestSpeed, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
                    Text(stringResource(R.string.onboarding_speed_request), style = MaterialTheme.typography.labelLarge)
                }
                if (state.speedPermission == PermissionStatus.DENIED) {
                    OutlinedButton(onClick = onOpenAppSettings, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
                        Text(stringResource(R.string.settings_open_app_settings), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Label(R.string.settings_recording)
            Text(
                stringResource(
                    when (state.recordingStatus) {
                        RecordingStatus.OFF -> R.string.home_notice_recording_off
                        RecordingStatus.WAITING -> R.string.home_status_waiting
                        RecordingStatus.RECORDING -> R.string.home_status_recording
                    },
                ),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun Line(label: Int, value: String) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(label), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun Label(text: Int) {
    Text(stringResource(text), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Panel(modifier: Modifier, content: @Composable () -> Unit) {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
    }
}

@Preview(widthDp = 1280, heightDp = 720)
@Composable
private fun SettingsPreview() = BlinkerLeagueTheme {
    Surface(color = MaterialTheme.colorScheme.background) {
        Box {
            SettingsContent(
                SettingsUiState("깜빡이장인", "서울 마포구", PermissionStatus.DENIED, RecordingStatus.WAITING, "1.0", false, leagueVisible = true),
                {}, {},
            )
        }
    }
}

@Preview(widthDp = 1280, heightDp = 720)
@Composable
private fun SettingsSavingPreview() = BlinkerLeagueTheme {
    Surface(color = MaterialTheme.colorScheme.background) {
        Box {
            SettingsContent(
                SettingsUiState(
                    "깜빡이장인", "서울 마포구", PermissionStatus.DENIED, RecordingStatus.WAITING, "1.0", false,
                    leagueVisible = true, leagueVisibilitySaving = true,
                ),
                {}, {},
            )
        }
    }
}
