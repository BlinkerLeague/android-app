package com.example.blinkerleague.domain.model

/** 런타임 권한 상태 (screen-plan 4.2 `speedPermission`: 허용/거부/미요청) */
enum class PermissionStatus {
    GRANTED,

    /** 요청했지만 사용자가 거부함 (다시 요청할 수 있는지는 시스템이 정함) */
    DENIED,

    /** 아직 한 번도 요청하지 않음 */
    NOT_REQUESTED,
    ;

    companion object {
        fun of(granted: Boolean, requestedBefore: Boolean): PermissionStatus = when {
            granted -> GRANTED
            requestedBefore -> DENIED
            else -> NOT_REQUESTED
        }
    }
}
