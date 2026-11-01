package com.example.blinkerleague.data.location

import com.example.blinkerleague.domain.model.LocationSample
import kotlinx.coroutines.flow.Flow

/** 위치 공급원 (약 1초 주기). Fused Location 구현체와 Fake 구현체([FakeLocationSource])가 있음. */
interface LocationSource {
    val locations: Flow<LocationSample>
}
