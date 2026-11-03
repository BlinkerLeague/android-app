package com.example.blinkerleague

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 스파이크: Pleos FusedLocation SDK(1.1.3)로 Pleos 에뮬레이터에서 현재 위치를 받을 수 있는지 확인한다.
 * 결과는 logcat 태그 `FusedLocationSpike`로 남긴다.
 *
 * 실행 전 권한 허용 필요 (dangerous):
 *   adb shell pm grant com.example.blinkerleague pleos.car.permission.FUSED_LOCATION
 */
@RunWith(AndroidJUnit4::class)
class FusedLocationSpikeTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var fused: FusedLocation

    @Before
    fun setUp() {
        fused = FusedLocation(context)
        val supported = fused.isSupported()
        Log.i(TAG, "isSupported=$supported")
        assertTrue("FusedLocation 미지원 환경", supported)
        fused.initialize()
    }

    @After
    fun tearDown() {
        fused.release()
    }

    @Test
    fun lastKnownLocation() {
        val latch = CountDownLatch(1)
        var result: LocationData? = null
        var error: Exception? = null
        fused.getLastKnownLocation(object : FusedLocationListener {
            override fun onChanged(location: LocationData?) {
                result = location
                latch.countDown()
            }

            override fun onError(exception: Exception) {
                error = exception
                latch.countDown()
            }
        })
        val answered = latch.await(10, TimeUnit.SECONDS)
        Log.i(TAG, "lastKnown answered=$answered location=$result error=$error")
        assertTrue("10초 안에 응답 없음", answered)
        assertTrue("오류: $error", error == null)
    }

    @Test
    fun locationUpdates() {
        val updates = CopyOnWriteArrayList<LocationData>()
        val errors = CopyOnWriteArrayList<Exception>()
        val listener = object : FusedLocationListener {
            override fun onChanged(location: LocationData?) {
                Log.i(TAG, "update $location")
                if (location != null) updates += location
            }

            override fun onError(exception: Exception) {
                Log.e(TAG, "update error", exception)
                errors += exception
            }
        }
        fused.registerFusedLocationCallback(listener)
        Thread.sleep(15_000)
        fused.unregisterFusedLocationCallback(listener)

        val intervals = updates.zipWithNext { a, b -> b.timestampMillis - a.timestampMillis }
        Log.i(TAG, "updates=${updates.size} errors=${errors.size} intervalsMs=$intervals")
        assertTrue("오류: $errors", errors.isEmpty())
        assertTrue("15초 동안 위치 업데이트 없음", updates.isNotEmpty())
    }

    private companion object {
        const val TAG = "FusedLocationSpike"
    }
}
