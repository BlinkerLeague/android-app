package com.example.blinkerleague.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.util.Log
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import kotlinx.coroutines.CompletableDeferred
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
 * 내비 음성과 겹치지 않게: 짧은 오디오 포커스(GAIN_TRANSIENT)를 받은 뒤에만 읽음.
 * 내비가 말하는 중이라 포커스가 지연되면 [FOCUS_WAIT_MS]까지 기다리고, 넘으면 이번 안내는 건너뜀.
 * 실차에서 포커스가 어떻게 중재되는지는 스파이크 10번에서 확인.
 */
class PleosTtsAnnouncer(context: Context) : VoiceAnnouncer {

    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANT)
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
        val ok = withFocus { speakAndWait(engine, text) } ?: false
        Log.i(TAG, "안내 ${if (ok) "완료" else "실패"}: $text")
        ok
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
                if (change == AudioManager.AUDIOFOCUS_GAIN) granted.complete(true)
            }
            .build()
        try {
            when (audioManager.requestAudioFocus(request)) {
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

    private fun finish(success: Boolean) {
        current?.complete(success)
        current = null
    }

    private companion object {
        const val TAG = "PleosTtsAnnouncer"
        const val INIT_WAIT_MS = 10_000L
        const val FOCUS_WAIT_MS = 15_000L
        const val SPEAK_WAIT_MS = 20_000L
    }
}
