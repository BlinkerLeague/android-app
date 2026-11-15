package com.example.blinkerleague

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.blinkerleague.data.location.PleosLocationSource
import com.example.blinkerleague.data.vehicle.PleosVehicleSignalSource
import com.example.blinkerleague.domain.model.Availability
import com.example.blinkerleague.domain.model.Gear
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 실제 공급원(Pleos Vehicle SDK·CarProperty·FusedLocation)이 에뮬레이터에서 값을 주는지 확인한다.
 * 결과는 logcat 태그 `PleosSourcesTest`로도 남긴다.
 *
 *   ./gradlew :automotive:connectedDebugAndroidTest \
 *     -Pandroid.testInstrumentationRunnerArguments.class=com.example.blinkerleague.PleosSourcesTest
 *
 * 런타임 권한(CAR_SPEED, FUSED_LOCATION)은 테스트가 현재 사용자에게 허용함.
 */
@RunWith(AndroidJUnit4::class)
class PleosSourcesTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Before
    fun grantRuntimePermissions() {
        for (permission in listOf("android.car.permission.CAR_SPEED", "pleos.car.permission.FUSED_LOCATION")) {
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, permission)
        }
    }

    private fun <T> firstOrNull(flow: Flow<T>, name: String): T? = runBlocking {
        withTimeoutOrNull(TIMEOUT_MS) { flow.first() }.also { Log.i(TAG, "$name 첫 값: $it") }
    }

    @Test
    fun capabilities() = runBlocking {
        val report = PleosVehicleSignalSource(context).capabilities()
        Log.i(TAG, "capabilities: $report")
        assertEquals(Availability.SUPPORTED, report.turnSignal)
        assertEquals(Availability.SUPPORTED, report.steering)
        assertEquals(Availability.SUPPORTED, report.gear)
        assertEquals(Availability.SUPPORTED, report.speed)
    }

    @Test
    fun vehicleFlowsSendCurrentValue() {
        val source = PleosVehicleSignalSource(context)
        val gear = firstOrNull(source.vehicleStates, "기어")
        assertNotNull("기어 값이 오지 않음", gear)
        assertNotEquals(Gear.UNKNOWN, gear!!.gear)
        assertEquals(null, gear.ignitionOn) // 시동 API 없음
        assertNotNull("방향지시등 값이 오지 않음", firstOrNull(source.turnSignals, "방향지시등"))
        assertNotNull("핸들 각도 값이 오지 않음", firstOrNull(source.steeringAngles, "핸들 각도"))
        assertNotNull("속도 값이 오지 않음", firstOrNull(source.speeds, "속도"))
    }

    @Test
    fun locationFlowSendsValue() {
        val location = firstOrNull(PleosLocationSource(context).locations, "위치")
        assertNotNull("위치 값이 오지 않음", location)
        assertEquals(null, location!!.speedKmh) // FusedLocation에는 속도가 없음
    }

    private companion object {
        const val TAG = "PleosSourcesTest"
        const val TIMEOUT_MS = 5_000L
    }
}
