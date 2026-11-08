package com.example.blinkerleague.data.permission

/**
 * 앱이 사용자에게 요청하는 런타임(dangerous) 권한. main 매니페스트에 선언된 것만 둠.
 * 이름은 플랫폼 문서·기기 `dumpsys package`로 확인한 값 (docs/platform-capabilities.md)
 */
enum class RuntimePermission(val manifestName: String) {
    /** 차량 속도 CarProperty `PERF_VEHICLE_SPEED` (저속 회전 제외, 운행 거리) */
    SPEED("android.car.permission.CAR_SPEED"),

    /** Pleos FusedLocation SDK. P0 판정에는 쓰지 않음 (회전은 핸들 각도) */
    LOCATION("pleos.car.permission.FUSED_LOCATION"),
}
