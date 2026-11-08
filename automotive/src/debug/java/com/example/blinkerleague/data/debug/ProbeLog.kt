package com.example.blinkerleague.data.debug

import android.os.SystemClock
import android.util.Log
import java.lang.reflect.Modifier
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * 조사용 로그 한 줄 형식 (Logcat [tag]):
 * `HH:mm:ss.SSS +경과초 dt=같은이름직전간격초 #누적 [출처] 이름 값`
 * 출처: Vehicle(Pleos Vehicle SDK), CarProperty(android.car), Navi(NaviHelper), Probe(조사 도구 자체)
 * `scripts/probe_log_summary.py`로 요약함.
 */
class ProbeLog(private val tag: String) {
    private val startedAt = SystemClock.elapsedRealtime()
    private val lastAt = mutableMapOf<String, Long>()
    private val counts = mutableMapOf<String, Int>()
    private val clock = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

    @Synchronized
    fun log(source: String, name: String, value: String) {
        val key = "$source/$name"
        val now = SystemClock.elapsedRealtime()
        val dt = lastAt[key]?.let { "%.3f".format((now - it) / 1000.0) } ?: "-"
        lastAt[key] = now
        val n = (counts[key] ?: 0) + 1
        counts[key] = n
        val line = "${LocalTime.now().format(clock)} +%.3f dt=$dt #$n [$source] $name $value"
            .format((now - startedAt) / 1000.0)
        // Logcat 한 줄 길이 제한(약 4KB) 때문에 나눠서 찍음. 이어지는 줄은 "  ↳"로 시작
        line.chunked(3_500).forEachIndexed { i, part -> Log.i(tag, if (i == 0) part else "  ↳$part") }
    }

    companion object {
        private const val MAX_DEPTH = 5

        /**
         * 객체의 필드를 전부 문자열로. SDK 데이터 클래스의 공개 getter(get·is로 시작)를 리플렉션으로 훑고,
         * 중첩된 Pleos 타입·리스트는 재귀로 펼침 (toString이 모든 필드를 보여준다는 보장이 없어서)
         */
        fun dump(value: Any?, depth: Int = 0): String = when {
            value == null -> "null"
            value is Enum<*> -> value.name
            value is CharSequence -> "\"$value\""
            value is Number || value is Boolean -> value.toString()
            value is Collection<*> -> value.joinToString(prefix = "[", postfix = "]") { dump(it, depth + 1) }
            value is Array<*> -> value.joinToString(prefix = "[", postfix = "]") { dump(it, depth + 1) }
            depth > MAX_DEPTH || !value.javaClass.name.startsWith("ai.pleos.") -> value.toString()
            else -> {
                val props = value.javaClass.methods
                    .filter { m ->
                        m.parameterCount == 0 && !Modifier.isStatic(m.modifiers) && m.declaringClass != Any::class.java &&
                            (m.name.startsWith("get") && m.name.length > 3 || m.name.startsWith("is") && m.name.length > 2)
                    }
                    .sortedBy { it.name }
                    .joinToString(", ") { m ->
                        val name = m.name.removePrefix("get").removePrefix("is").replaceFirstChar { it.lowercase() }
                        val v = runCatching { dump(m.invoke(value), depth + 1) }
                            .getOrElse { "<${it.javaClass.simpleName}>" }
                        "$name=$v"
                    }
                "${value.javaClass.simpleName}{$props}"
            }
        }
    }
}
