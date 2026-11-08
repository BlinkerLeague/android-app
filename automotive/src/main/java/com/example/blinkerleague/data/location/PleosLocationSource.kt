package com.example.blinkerleague.data.location

import android.content.Context
import android.util.Log
import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import com.example.blinkerleague.domain.model.LocationSample
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

/**
 * 실제 위치 공급원: Pleos FusedLocation SDK 1.1.3 (docs/spikes/fused-location.md).
 *
 * - `isSupported()`가 false면 아무것도 보내지 않음
 * - 권한 `pleos.car.permission.FUSED_LOCATION`(런타임)이 없으면 등록 호출이 `SecurityException`을 던짐 → 잡아서 Flow를 끝냄
 * - 약 1초마다 옴. `LocationData`에는 속도가 없어서 [LocationSample.speedKmh]는 null (속도는 차량 공급원에서 받음)
 * - heading은 에뮬레이터에서 항상 0. 그대로 전달함
 *
 * @param locationGranted 위치 권한 허용 여부 Flow. 수집 중에 허용되면 그때 구독함. null이면 구독 시점에 한 번 시도
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PleosLocationSource(
    context: Context,
    private val locationGranted: Flow<Boolean>? = null,
) : LocationSource {

    private val fused: FusedLocation? by lazy {
        val instance = FusedLocation(context.applicationContext)
        if (runCatching { instance.isSupported() }.getOrDefault(false)) {
            instance.initialize()
            instance
        } else {
            Log.w(TAG, "FusedLocation 미지원")
            null
        }
    }

    override val locations: Flow<LocationSample> = (locationGranted ?: flowOf(true))
        .distinctUntilChanged()
        .flatMapLatest { granted -> if (granted) locationUpdates else emptyFlow() }

    private val locationUpdates: Flow<LocationSample> = callbackFlow {
        val sdk = fused
        if (sdk == null) {
            close()
            return@callbackFlow
        }
        val listener = object : FusedLocationListener {
            override fun onChanged(location: LocationData?) {
                location?.let {
                    trySend(LocationSample(it.timestampMillis, it.latitude, it.longitude, speedKmh = null, headingDeg = it.heading))
                }
            }
            override fun onError(exception: Exception) {
                Log.w(TAG, "위치 오류: $exception")
            }
        }
        val registered = runCatching { sdk.registerFusedLocationCallback(listener) }
            .onFailure { Log.w(TAG, "위치 구독 실패 (권한 확인)", it) }
            .isSuccess
        if (!registered) {
            close()
            return@callbackFlow
        }
        awaitClose { runCatching { sdk.unregisterFusedLocationCallback(listener) } }
    }

    private companion object {
        const val TAG = "PleosLocationSource"
    }
}
