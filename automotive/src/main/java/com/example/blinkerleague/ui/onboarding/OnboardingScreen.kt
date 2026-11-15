package com.example.blinkerleague.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.blinkerleague.BlinkerLeagueApplication
import com.example.blinkerleague.R
import com.example.blinkerleague.data.permission.RuntimePermission
import com.example.blinkerleague.domain.model.Availability
import com.example.blinkerleague.domain.model.CapabilityReport
import com.example.blinkerleague.domain.model.PermissionStatus
import com.example.blinkerleague.domain.region.Regions
import com.example.blinkerleague.ui.common.rememberPermissionRequester
import com.example.blinkerleague.ui.theme.BlinkerLeagueTheme
import com.example.blinkerleague.ui.theme.OnSuccessContainer

private val OnboardingViewModelFactory = viewModelFactory {
    initializer {
        val container = (this[APPLICATION_KEY] as BlinkerLeagueApplication).container
        OnboardingViewModel(
            provinces = Regions.all,
            speedPermission = container.permissions.status(RuntimePermission.SPEED),
            loadCapabilities = container.vehicleSignalSource::capabilities,
            register = container.driverRepository::register,
        )
    }
}

@Composable
fun OnboardingScreen(onRegistered: () -> Unit, viewModel: OnboardingViewModel = viewModel(factory = OnboardingViewModelFactory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val container = (LocalContext.current.applicationContext as BlinkerLeagueApplication).container
    val requestSpeed = rememberPermissionRequester(container.permissions, RuntimePermission.SPEED)
    LaunchedEffect(state.registered) { if (state.registered) onRegistered() }
    LaunchedEffect(state.speedPermission) { viewModel.refreshCapabilities() }
    OnboardingContent(
        state = state,
        onNicknameChange = viewModel::onNicknameChange,
        onProvinceSelect = viewModel::onProvinceSelect,
        onRegionSelect = viewModel::onRegionSelect,
        onRequestSpeed = requestSpeed,
        onSubmit = viewModel::submit,
    )
}

@Composable
fun OnboardingContent(
    state: OnboardingUiState,
    onNicknameChange: (String) -> Unit,
    onProvinceSelect: (Int) -> Unit,
    onRegionSelect: (String) -> Unit,
    onRequestSpeed: () -> Unit,
    onSubmit: () -> Unit,
) {
    Row(
        Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // 1. 닉네임·지역
        Panel(Modifier.weight(1.2f).fillMaxHeight()) {
            Text(stringResource(R.string.onboarding_title), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            Label(R.string.onboarding_nickname)
            OutlinedTextField(
                value = state.nickname,
                onValueChange = onNicknameChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge,
                placeholder = { Text(stringResource(R.string.onboarding_nickname_hint)) },
                isError = state.nicknameError != null,
                supportingText = state.nicknameError?.let { error ->
                    {
                        Text(
                            stringResource(
                                when (error) {
                                    NicknameError.LENGTH -> R.string.onboarding_nickname_length
                                    NicknameError.TAKEN -> R.string.onboarding_nickname_taken
                                },
                            ),
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            )
            Label(R.string.onboarding_region)
            if (state.provinces.size > 1) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.provinces.forEachIndexed { i, province ->
                        FilterChip(selected = i == state.provinceIndex, onClick = { onProvinceSelect(i) }, label = { Text(province.name) })
                    }
                }
            }
            val districts = state.provinces.getOrNull(state.provinceIndex)?.districts.orEmpty()
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 120.dp),
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(districts, key = { it.code }) { district ->
                    FilterChip(
                        selected = district.code == state.regionCode,
                        onClick = { onRegionSelect(district.code) },
                        label = { Text(district.name, style = MaterialTheme.typography.bodyMedium) },
                        modifier = Modifier.heightIn(min = 56.dp),
                    )
                }
            }
        }

        // 2. 내 차량 측정 가능 항목
        Panel(Modifier.weight(1f).fillMaxHeight()) {
            Label(R.string.onboarding_capabilities)
            val report = state.capabilities
            if (report == null) {
                Text(stringResource(R.string.onboarding_capabilities_checking), style = MaterialTheme.typography.bodyMedium)
            } else {
                CapabilityLine(R.string.onboarding_cap_turn_signal, report.turnSignal)
                CapabilityLine(R.string.onboarding_cap_steering, report.steering)
                CapabilityLine(R.string.onboarding_cap_gear, report.gear)
                CapabilityLine(R.string.onboarding_cap_speed, report.speed)
            }
        }

        // 3. 속도 권한 → 리그 입장
        Panel(Modifier.weight(1f).fillMaxHeight()) {
            Label(R.string.onboarding_speed_title)
            // 시스템 창에는 "위치 정보"라고 뜨므로 먼저 이유를 설명함 (docs/platform-capabilities.md)
            Text(stringResource(R.string.onboarding_speed_body), style = MaterialTheme.typography.bodyMedium)
            when (state.speedPermission) {
                PermissionStatus.GRANTED -> Text(stringResource(R.string.onboarding_speed_granted), style = MaterialTheme.typography.titleLarge, color = OnSuccessContainer)
                else -> {
                    OutlinedButton(onClick = onRequestSpeed, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
                        Text(stringResource(R.string.onboarding_speed_request), style = MaterialTheme.typography.labelLarge)
                    }
                    if (state.speedPermission == PermissionStatus.DENIED) {
                        Text(stringResource(R.string.onboarding_speed_denied), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            state.submitError?.let {
                Text(
                    stringResource(if (it == SubmitError.OFFLINE) R.string.onboarding_error_offline else R.string.onboarding_error_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (state.regionCode == null) {
                Text(stringResource(R.string.onboarding_pick_region), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(onClick = onSubmit, enabled = state.canSubmit, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
                Text(
                    stringResource(if (state.submitting) R.string.onboarding_submitting else R.string.onboarding_submit),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun CapabilityLine(label: Int, availability: Availability) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        val supported = availability == Availability.SUPPORTED
        Text(
            stringResource(label),
            style = MaterialTheme.typography.bodyLarge,
            color = if (supported) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            stringResource(
                when (availability) {
                    Availability.SUPPORTED -> R.string.onboarding_cap_supported
                    Availability.UNSUPPORTED -> R.string.onboarding_cap_unsupported
                    Availability.NO_PERMISSION -> R.string.onboarding_cap_no_permission
                },
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = if (supported) OnSuccessContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
private fun OnboardingPreview() = BlinkerLeagueTheme {
    Surface(color = MaterialTheme.colorScheme.background) {
        OnboardingContent(
            OnboardingUiState(
                nickname = "깜빡이장인",
                provinces = Regions.all,
                regionCode = "11440",
                capabilities = CapabilityReport(Availability.SUPPORTED, Availability.SUPPORTED, Availability.SUPPORTED, Availability.NO_PERMISSION),
            ),
            {}, {}, {}, {}, {},
        )
    }
}

@Preview(widthDp = 720, heightDp = 720)
@Composable
private fun OnboardingNarrowPreview() = BlinkerLeagueTheme {
    Surface(color = MaterialTheme.colorScheme.background) {
        OnboardingContent(
            OnboardingUiState(
                nickname = "깜빡이장인",
                provinces = Regions.all,
                regionCode = "11440",
                capabilities = CapabilityReport(Availability.SUPPORTED, Availability.UNSUPPORTED, Availability.UNSUPPORTED, Availability.NO_PERMISSION),
            ),
            {}, {}, {}, {}, {},
        )
    }
}
