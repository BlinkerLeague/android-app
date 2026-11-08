package com.example.blinkerleague.data.permission

import android.content.Context
import android.content.pm.PackageManager
import com.example.blinkerleague.domain.model.PermissionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 런타임 권한 상태를 Flow로 알려줌 (홈 "속도 권한이 없어…" 안내, 온보딩 권한 단계, 신호 공급원의 재구독).
 *
 * - 권한은 앱 밖(설정, `adb pm grant`)에서도 바뀌므로 화면이 다시 보일 때 [refresh]를 부름 (`MainActivity.onResume`)
 * - 요청 결과는 [onRequestResult]로 넘김. "요청한 적 있음"을 저장해 거부와 미요청을 구분함
 */
class PermissionRepository(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val states: Map<RuntimePermission, MutableStateFlow<PermissionStatus>> =
        RuntimePermission.entries.associateWith { MutableStateFlow(current(it)) }

    fun status(permission: RuntimePermission): StateFlow<PermissionStatus> = states.getValue(permission).asStateFlow()

    /** 지금 상태를 다시 읽음. 바뀐 것만 Flow로 나감 */
    fun refresh() {
        states.forEach { (permission, flow) -> flow.value = current(permission) }
    }

    /** 권한 요청 창의 결과. 허용 여부는 시스템 값을 다시 읽어 정함 */
    fun onRequestResult(permission: RuntimePermission) {
        prefs.edit().putBoolean(permission.name, true).apply()
        states.getValue(permission).value = current(permission)
    }

    private fun current(permission: RuntimePermission): PermissionStatus = PermissionStatus.of(
        granted = appContext.checkSelfPermission(permission.manifestName) == PackageManager.PERMISSION_GRANTED,
        requestedBefore = prefs.getBoolean(permission.name, false),
    )

    private companion object {
        const val PREFS = "runtime_permissions"
    }
}
