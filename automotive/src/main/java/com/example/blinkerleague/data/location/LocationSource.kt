package com.example.blinkerleague.data.location

import com.example.blinkerleague.domain.model.LocationSample
import kotlinx.coroutines.flow.Flow

/**
 * 위치 공급원 (약 1초 주기). 운행 거리와 저속 판단에 씀. 실차는 FusedLocation이 미지원이라
 * NaviHelper `getCurrentLocationInfo`로 구현할 예정. Fake 구현체는 [FakeLocationSource]
 */
interface LocationSource {
    val locations: Flow<LocationSample>
}
