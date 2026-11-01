package com.example.blinkerleague.voice

/** 음성 안내 출력. 주행 안전 원칙상 운행당 시동 시 1회, 종료 시 1회만 부름 ([com.example.blinkerleague.service.TripVoiceGuide]) */
interface VoiceAnnouncer {
    /** [text]를 읽음. 다 읽으면 true, 오디오 포커스를 못 받았거나 TTS를 쓸 수 없으면 false (안내를 건너뜀) */
    suspend fun speak(text: String): Boolean
}
