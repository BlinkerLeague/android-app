package com.example.blinkerleague

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 스파이크: Vehicle SDK(2.3.2)의 모든 도메인에서 어떤 값을 읽을 수 있는지 에뮬레이터에서 확인한다.
 * 결과는 logcat 태그 `VehicleSurvey`로 남기고, 정리한 내용은 docs/sdk-test-log.md에 적는다.
 *
 * SDK 메서드 이름을 손으로 적지 않고 리플렉션으로 훑는다.
 * - `check*`/`get*`/`has*`/`is*` 중 인자가 (onComplete, onFailed) 두 개뿐인 메서드를 모두 호출
 * - `register*` 중 인자가 리스너 하나뿐인 메서드에 로그용 Proxy 리스너를 등록
 * 영역(Area) 인자가 필요한 메서드는 건너뛰고 이름만 남긴다.
 *
 * 실행 예 (앱은 user 10에서 돔):
 *   adb shell am instrument -w --user 10 -e class com.example.blinkerleague.VehicleSdkSurveyTest#dumpReadableValues \
 *     com.example.blinkerleague.test/androidx.test.runner.AndroidJUnitRunner
 *   # 리스너: -e listenSeconds 60 (기본 30초). 그동안 Virtual Console로 값을 바꿈
 */
@RunWith(AndroidJUnit4::class)
class VehicleSdkSurveyTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var vehicle: Vehicle

    @Before
    fun setUp() {
        vehicle = Vehicle(instrumentation.targetContext)
        vehicle.initialize()
        // initialize()에는 연결 완료 콜백이 없음 → 잠시 기다림
        SystemClock.sleep(1500)
    }

    @After
    fun tearDown() {
        vehicle.release()
    }

    @Test
    fun dumpReadableValues() {
        for ((domainName, domain) in domains()) {
            val methods = domain.javaClass.interfaces.flatMap { it.methods.toList() }
                .distinctBy { it.name + it.parameterTypes.joinToString() }
                .sortedBy { it.name }
            for (m in methods) {
                if (!READ_PREFIXES.any { m.name.startsWith(it) }) continue
                val label = "$domainName.${m.name}"
                if (!isCallbackPair(m)) {
                    Log.i(TAG, "SKIP $label(${m.parameterTypes.joinToString { it.simpleName }})")
                    continue
                }
                Log.i(TAG, "READ $label -> ${callForResult(domain, m)}")
            }
        }
    }

    @Test
    fun listenForChanges() {
        val seconds = InstrumentationRegistry.getArguments().getString("listenSeconds")?.toLong() ?: 30L
        val registered = mutableListOf<Triple<Any, Method, Any>>()
        for ((domainName, domain) in domains()) {
            val methods = domain.javaClass.interfaces.flatMap { it.methods.toList() }.sortedBy { it.name }
            for (m in methods) {
                if (!m.name.startsWith("register")) continue
                val label = "$domainName.${m.name}"
                val type = m.parameterTypes.singleOrNull()
                if (type == null || !type.isInterface) {
                    Log.i(TAG, "SKIP $label(${m.parameterTypes.joinToString { it.simpleName }})")
                    continue
                }
                val listener = loggingListener(label, type)
                try {
                    m.invoke(domain, listener)
                    registered += Triple(domain, m, listener)
                    Log.i(TAG, "REGISTERED $label")
                } catch (e: InvocationTargetException) {
                    Log.i(TAG, "REGISTER_FAIL $label ${e.targetException}")
                }
            }
        }
        Log.i(TAG, "LISTENING ${seconds}s")
        SystemClock.sleep(seconds * 1000)
        for ((domain, m, listener) in registered) {
            runCatching {
                domain.javaClass.interfaces.flatMap { it.methods.toList() }
                    .first { it.name == m.name.replaceFirst("register", "unregister") && it.parameterTypes.contentEquals(m.parameterTypes) }
                    .invoke(domain, listener)
            }
        }
        Log.i(TAG, "DONE")
    }

    /**
     * 좌석(SeatArea) 인자가 필요한 읽기 값: 안전벨트 착용, 착석, 미착용 경고, 에어백 등.
     * 영역 목록은 Seat.checkSeatBeltBuckledCapabilities 결과를 씀.
     */
    @Test
    fun dumpSeatAreaValues() {
        val latch = CountDownLatch(1)
        var areas: List<SeatArea> = emptyList()
        vehicle.getSeat().checkSeatBeltBuckledCapabilities(
            onComplete = { list: List<SeatArea> -> areas = list; latch.countDown() },
            onFailed = { e: Exception -> Log.i(TAG, "SEAT_AREAS FAIL $e"); latch.countDown() },
        )
        latch.await(3, TimeUnit.SECONDS)
        Log.i(TAG, "SEAT_AREAS ${areas.map { seatName(it) }}")
        for ((domainName, domain) in domains()) {
            val methods = domain.javaClass.interfaces.flatMap { it.methods.toList() }.sortedBy { it.name }
            for (m in methods) {
                if (!READ_PREFIXES.drop(1).any { m.name.startsWith(it) }) continue
                val p = m.parameterTypes
                if (p.size != 3 || p[0] != SeatArea::class.java || p[1].name != "kotlin.jvm.functions.Function1") continue
                for (area in areas) {
                    Log.i(TAG, "SEAT $domainName.${m.name}[${seatName(area)}] -> ${callForResult(domain, m, area)}")
                }
            }
        }
    }

    private fun seatName(area: SeatArea) = area.values.joinToString("+") { it.toString() }

    /** Vehicle.getXxx() 로 얻는 도메인 객체 (Brake, Light, Safety …) */
    private fun domains(): List<Pair<String, Any>> =
        Vehicle::class.java.methods
            .filter { it.name.startsWith("get") && it.parameterCount == 0 && it.returnType.isInterface }
            .sortedBy { it.name }
            .map { it.name.removePrefix("get") to it.invoke(vehicle)!! }

    private fun isCallbackPair(m: Method) =
        m.parameterCount == 2 && m.parameterTypes.all { it.name == "kotlin.jvm.functions.Function1" }

    private fun callForResult(target: Any, m: Method, vararg leadingArgs: Any): String {
        val latch = CountDownLatch(1)
        var result = "TIMEOUT"
        val onComplete: (Any?) -> Unit = { result = "OK ${describe(it)}"; latch.countDown() }
        val onFailed: (Exception) -> Unit = { result = "FAIL ${it.javaClass.simpleName}: ${it.message}"; latch.countDown() }
        try {
            m.invoke(target, *leadingArgs, onComplete, onFailed)
        } catch (e: InvocationTargetException) {
            return "THROW ${e.targetException}"
        }
        latch.await(3, TimeUnit.SECONDS)
        return result
    }

    private fun loggingListener(label: String, type: Class<*>): Any =
        Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { proxy, method, args ->
            when (method.name) {
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.get(0)
                "toString" -> "SurveyListener($label)"
                else -> {
                    Log.i(TAG, "EVENT $label.${method.name} ${args?.joinToString { describe(it) } ?: ""}")
                    null
                }
            }
        }

    private fun describe(value: Any?): String = when (value) {
        null -> "null"
        is Collection<*> -> value.joinToString(prefix = "[", postfix = "]") { describe(it) }
        is Array<*> -> value.joinToString(prefix = "[", postfix = "]") { describe(it) }
        is Exception -> "${value.javaClass.simpleName}: ${value.message}"
        else -> "$value (${value.javaClass.simpleName})"
    }

    companion object {
        private const val TAG = "VehicleSurvey"
        private val READ_PREFIXES = listOf("check", "get", "has", "is")
    }
}
