package com.example.blinkerleague.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Handler
import android.os.Looper
import android.util.Log
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

/**
 * Pleos TextToSpeech SDK(3.3.1)로 읽는 구현. API는 SDK 클래스(javap)로 확인함:
 * `TextToSpeech(Context, Mode)`, `initialize(OnInitializeListener)`, `addEventListener(EventListener)`,
 * `setLocale`, `setAudioAttributes`, `speak(text, isFinal)`, `release()`.
 *
 * Android 표준 TextToSpeech는 Pleos 에뮬레이터에서 엔진(caas SpeechService)에 연결되지 않아 쓰지 않음.
 * 서버 등록(`registerApp`)이 필요 없는 [Mode.ON_DEVICE]를 씀.
 *
 * 내비 음성과 겹치지 않게 (스파이크 10, docs/spikes/voice-navi-focus.md):
 * - 용도는 [AudioAttributes.USAGE_MEDIA]. `USAGE_ASSISTANT`로 읽으면 차량 오디오 정책이 그동안 내비 안내의 포커스 요청을
 *   **거부**해 회전 안내가 빠짐(에뮬레이터 실측). `USAGE_ANNOUNCEMENT`는 시스템 전용이라 일반 앱이 쓰면 예외
 * - 다른 용도에서는 내비 요청이 허용되지만 우리 쪽에 포커스 손실이 오지 않아 두 음성이 동시에 나옴
 *   → 재생 상태 콜백으로 내비 안내(`USAGE_ASSISTANCE_NAVIGATION_GUIDANCE`)가 시작되면 즉시 멈추고,
 *   내비가 끝난 뒤 한 번 더 처음부터 읽음 (운행당 안내 1회는 그대로)
 * - 내비가 이미 말하는 중이면 끝날 때까지([NAVI_WAIT_MS]) 기다렸다가 읽음
 */
class PleosTtsAnnouncer(
    context: Context,
    private val usage: Int = AudioAttributes.USAGE_MEDIA,
) : VoiceAnnouncer {

    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder()
        .setUsage(usage)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private val mutex = Mutex()
    private var tts: TextToSpeech? = null
    private var current: CompletableDeferred<Boolean>? = null

    private val events = object : EventListener {
        override fun onStart() = Unit
        override fun onReady() = Unit
        override fun onDone() = finish(true)
        override fun onStop() = finish(false)
        override fun onError(type: ErrorType) {
            Log.w(TAG, "TTS 오류: $type")
            finish(false)
        }
        override fun onUpdatedRms(rms: Double) = Unit
    }

    override suspend fun speak(text: String): Boolean = mutex.withLock {
        val engine = engine() ?: return false
        var outcome = Outcome.FAILED
        for (attempt in 1..MAX_ATTEMPTS) {
            if (!awaitNavigationQuiet()) {
                Log.i(TAG, "내비 안내가 끝나지 않아 이번 안내를 건너뜀")
                break
            }
            outcome = withFocus { speakUnlessNavigation(engine, text) } ?: Outcome.FAILED
            if (outcome != Outcome.INTERRUPTED) break
            Log.i(TAG, "내비 안내가 시작돼 멈춤 (시도 $attempt/$MAX_ATTEMPTS)")
        }
        val ok = outcome == Outcome.DONE
        Log.i(TAG, "안내 ${if (ok) "완료" else "실패($outcome)"}: $text")
        ok
    }

    private enum class Outcome { DONE, FAILED, INTERRUPTED }

    /** 내비 안내 음성이 재생 중인지 (다른 앱 재생도 용도는 보임) */
    private fun navigationSpeaking(configs: List<AudioPlaybackConfiguration> = audioManager.activePlaybackConfigurations): Boolean =
        configs.any { it.audioAttributes.usage == AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE }

    /** 내비가 말하는 중이면 끝날 때까지 기다림. [NAVI_WAIT_MS] 안에 안 끝나면 false */
    private suspend fun awaitNavigationQuiet(): Boolean =
        withTimeoutOrNull(NAVI_WAIT_MS) {
            while (navigationSpeaking()) delay(NAVI_POLL_MS)
            true
        } ?: false

    /** 읽는 동안 내비 안내가 시작되면 멈추고 INTERRUPTED */
    private suspend fun speakUnlessNavigation(engine: TextToSpeech, text: String): Outcome {
        var interrupted = false
        val callback = object : AudioManager.AudioPlaybackCallback() {
            override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>) {
                if (!interrupted && navigationSpeaking(configs)) {
                    interrupted = true
                    runCatching { engine.stop() }
                    finish(false)
                }
            }
        }
        audioManager.registerAudioPlaybackCallback(callback, Handler(Looper.getMainLooper()))
        try {
            val done = speakAndWait(engine, text)
            return when {
                interrupted -> Outcome.INTERRUPTED
                done -> Outcome.DONE
                else -> Outcome.FAILED
            }
        } finally {
            audioManager.unregisterAudioPlaybackCallback(callback)
        }
    }

    /** 처음 쓸 때 초기화. 실패하면 다음 안내 때 다시 시도함 */
    private suspend fun engine(): TextToSpeech? {
        tts?.let { return it }
        val result = CompletableDeferred<ErrorType?>()
        val engine = runCatching { TextToSpeech(appContext, Mode.ON_DEVICE) }.getOrElse {
            Log.w(TAG, "TTS 생성 실패", it)
            return null
        }
        engine.initialize(object : OnInitializeListener {
            override fun onSuccess() {
                result.complete(null)
            }
            override fun onFailure(type: ErrorType) {
                result.complete(type)
            }
        })
        val error = withTimeoutOrNull(INIT_WAIT_MS) { result.await() } ?: run {
            if (!result.isCompleted) ErrorType.TIMEOUT else null
        }
        if (error != null) {
            Log.w(TAG, "TTS 초기화 실패: $error — 음성 안내를 건너뜀")
            runCatching { engine.release() }
            return null
        }
        engine.setLocale(Locale.KOREA) // ko-KR. 국가 없는 Locale.KOREAN("ko-")은 엔진이 거부함
        engine.setAudioAttributes(attributes)
        engine.addEventListener(events)
        tts = engine
        return engine
    }

    /** 오디오 포커스를 받은 동안 [block]을 실행. 포커스를 못 받으면 null */
    private suspend fun <T> withFocus(block: suspend () -> T): T? {
        val granted = CompletableDeferred<Boolean>()
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attributes)
            .setAcceptsDelayedFocusGain(true)
            .setOnAudioFocusChangeListener { change ->
                Log.i(TAG, "포커스 변화: ${focusName(change)}")
                if (change == AudioManager.AUDIOFOCUS_GAIN) granted.complete(true)
            }
            .build()
        try {
            val result = audioManager.requestAudioFocus(request)
            Log.i(TAG, "포커스 요청 결과: $result (1=GRANTED, 2=DELAYED, 0=FAILED)")
            when (result) {
                AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> granted.complete(true)
                AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> Unit // 내비 음성 등이 끝나면 GAIN이 옴
                else -> return null
            }
            if (withTimeoutOrNull(FOCUS_WAIT_MS) { granted.await() } != true) {
                Log.i(TAG, "오디오 포커스를 받지 못해 안내를 건너뜀")
                return null
            }
            return block()
        } finally {
            audioManager.abandonAudioFocusRequest(request)
        }
    }

    private suspend fun speakAndWait(engine: TextToSpeech, text: String): Boolean {
        val done = CompletableDeferred<Boolean>()
        current = done
        engine.speak(text, true) // isFinal = true: 문장 하나를 한 번에 보냄
        return withTimeoutOrNull(SPEAK_WAIT_MS) { done.await() } ?: false
    }

    private fun focusName(change: Int) = when (change) {
        AudioManager.AUDIOFOCUS_GAIN -> "GAIN"
        AudioManager.AUDIOFOCUS_LOSS -> "LOSS"
        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> "LOSS_TRANSIENT"
        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> "LOSS_TRANSIENT_CAN_DUCK"
        else -> change.toString()
    }

    private fun finish(success: Boolean) {
        current?.complete(success)
        current = null
    }

    private companion object {
        const val TAG = "PleosTtsAnnouncer"
        const val INIT_WAIT_MS = 10_000L
        const val FOCUS_WAIT_MS = 15_000L
        const val SPEAK_WAIT_MS = 20_000L
        const val NAVI_WAIT_MS = 15_000L
        const val NAVI_POLL_MS = 300L

        /** 내비에 끊기면 한 번 더 읽음 */
        const val MAX_ATTEMPTS = 2
    }
}
