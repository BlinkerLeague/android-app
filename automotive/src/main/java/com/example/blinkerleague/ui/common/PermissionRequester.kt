package com.example.blinkerleague.ui.common

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import com.example.blinkerleague.data.permission.PermissionRepository
import com.example.blinkerleague.data.permission.RuntimePermission

/**
 * 런타임 권한 요청 창을 띄우는 함수를 돌려줌 (온보딩 4단계 "속도 권한" 등).
 * 결과는 [PermissionRepository]에 반영되고, 화면은 `status(permission)` Flow로 받음.
 *
 * ```
 * val requestSpeed = rememberPermissionRequester(container.permissions, RuntimePermission.SPEED)
 * Button(onClick = requestSpeed) { … }
 * ```
 */
@Composable
fun rememberPermissionRequester(repository: PermissionRepository, permission: RuntimePermission): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        repository.onRequestResult(permission)
    }
    return { launcher.launch(permission.manifestName) }
}
