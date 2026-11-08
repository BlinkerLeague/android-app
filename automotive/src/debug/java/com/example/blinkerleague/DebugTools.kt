package com.example.blinkerleague

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.example.blinkerleague.data.permission.RuntimePermission
import com.example.blinkerleague.data.debug.DebugProbes

/**
 * 디버그 빌드 전용 조사 도구 진입점 (src/debug). 릴리스 빌드에는 같은 이름의 빈 구현(src/release)이 들어감.
 * 조사용 프로브·서비스·권한은 릴리스 APK에 포함되지 않음 (Pleos 심사: 승인되지 않은 권한 선언 금지).
 */
object DebugTools {

    @Volatile private var probes: DebugProbes? = null

    /** 프로세스 안에서 하나만 쓰는 조사용 프로브 모음 */
    fun probes(context: Context): DebugProbes =
        probes ?: synchronized(this) { probes ?: DebugProbes(context.applicationContext).also { probes = it } }

    /**
     * MainActivity 실행 인텐트의 조사용 엑스트라를 처리함.
     * - `--es probe vehicle,speed,…` : 서비스 없이 앱 프로세스에서 프로브 등록 (포그라운드/일반 백그라운드 실험)
     * - `--el naviPollMs 5000`, `--ed proxLat … --ed proxLng … --ef proxRadius 100`
     * - `--el fgsDelayMs 5000` : 앱이 백그라운드로 간 뒤 앱 스스로 포그라운드 서비스 시작을 시도
     * - `--es requestPermission speed|location` : 런타임 권한 요청 창 띄우기 (온보딩 화면 전 시험용)
     */
    fun onActivityCreated(activity: Activity) {
        val intent = activity.intent
        intent.getStringExtra("requestPermission")?.let { name ->
            intent.removeExtra("requestPermission")
            val permission = RuntimePermission.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
            val component = activity as? ComponentActivity
            if (permission != null && component != null) {
                val permissions = (activity.application as BlinkerLeagueApplication).container.permissions
                component.registerForActivityResult(ActivityResultContracts.RequestPermission()) {
                    permissions.onRequestResult(permission)
                }.launch(permission.manifestName)
            }
        }
        val probes = probes(activity)
        intent.getStringExtra("probe")?.let { targets ->
            probes.start(DebugProbes.parseTargets(targets), via = "activity", naviPollMs = intent.getLongExtra("naviPollMs", 0L))
            if (intent.hasExtra("proxLat")) {
                probes.location.addProximityAlert(
                    intent.getDoubleExtra("proxLat", Double.NaN),
                    intent.getDoubleExtra("proxLng", Double.NaN),
                    intent.getFloatExtra("proxRadius", 100f),
                )
            }
        }
        val fgsDelayMs = intent.getLongExtra("fgsDelayMs", -1L)
        if (fgsDelayMs >= 0) {
            intent.removeExtra("fgsDelayMs") // 작업 복귀 시 같은 인텐트로 다시 실행되지 않게
            probes.event("fgsDelay", "scheduled ${fgsDelayMs}ms")
            val appContext = activity.applicationContext
            Handler(Looper.getMainLooper()).postDelayed(
                { probes.tryStartService(appContext, via = "app-delayed-${fgsDelayMs}ms") },
                fgsDelayMs,
            )
        }
    }
}
