"""Vehicle SDK 값 조사용 Virtual Console 주입 스크립트 (VehicleSdkSurveyTest#listenForChanges와 같이 실행).

브레이크·전조등·상향등·제동등·비상등·경고등(telltale)·크루즈 값을 차례로 바꾸고,
바꿀 때마다 Logcat `SurveyInject` 태그에 표시를 남김 → 콜백 로그(VehicleSurvey)와 순서를 맞춰 봄.

사용: python scripts/vehicle_survey_sim.py   (환경 변수 ADB로 adb 경로 지정 가능)
엔드포인트는 Virtual Console `/vehicle/catalog`에서 확인함. 쓰기는 PUT이고, 세션 신호가 살아 있어야 받음.
"""
import os
import subprocess
import threading
import time
import urllib.request
import uuid

ADB = os.environ.get("ADB", r"C:\android\sdk\platform-tools\adb.exe")
CONSOLE = "http://127.0.0.1:8088"
STEP_SEC = 2.0

STEPS = [
    ("brake 30%", "/vehicle/controls?brake=30"),
    ("brake 100%", "/vehicle/controls?brake=100"),
    ("brake 0%", "/vehicle/controls?brake=0"),
    ("headlamp Position", "/vehicle/lights?feature=headlamp_mode&value=Position"),
    ("headlamp LowBeam", "/vehicle/lights?feature=headlamp_mode&value=LowBeam"),
    ("headlamp Auto", "/vehicle/lights?feature=headlamp_mode&value=Auto"),
    ("headlamp Off", "/vehicle/lights?feature=headlamp_mode&value=Off"),
    ("high_beam on", "/vehicle/lights?feature=high_beam&value=true"),
    ("high_beam off", "/vehicle/lights?feature=high_beam&value=false"),
    ("stop_light on", "/vehicle/lights?feature=stop_light&value=true"),
    ("stop_light off", "/vehicle/lights?feature=stop_light&value=false"),
    ("hazard on", "/vehicle/lights?feature=hazard&value=true"),
    ("hazard off", "/vehicle/lights?feature=hazard&value=false"),
    ("telltale TurnSignalLeft Blink", "/vehicle/telltales/simulator?type=TurnSignalLeft&state=Blink&color=Green"),
    ("telltale TurnSignalLeft Off", "/vehicle/telltales/simulator?type=TurnSignalLeft&state=Off&color=Green"),
    ("telltale SeatBelt On", "/vehicle/telltales/simulator?type=SeatBelt&state=On&color=Red"),
    ("telltale SeatBelt Off", "/vehicle/telltales/simulator?type=SeatBelt&state=Off&color=Red"),
    ("cruise target 20m/s", "/vehicle/driving-mode?target_speed_mps=20"),
    ("cruise state ready", "/vehicle/driving-mode?ad_mode_state=ready"),
    ("cruise state not_ready", "/vehicle/driving-mode?ad_mode_state=not_ready"),
]


def request(path, method):
    try:
        urllib.request.urlopen(urllib.request.Request(CONSOLE + path, method=method), timeout=5).read()
        return True
    except Exception as e:  # noqa: BLE001 — 실패는 기록만 하고 계속 진행
        print(f"요청 실패 {path}: {e}")
        return False


def mark(label):
    subprocess.run([ADB, "shell", "log", "-t", "SurveyInject", "-p", "i", f"'{label}'"], capture_output=True)


def main():
    subprocess.run([ADB, "forward", "tcp:8088", "tcp:8088"], capture_output=True)
    client = f"survey-{uuid.uuid4().hex[:8]}"
    stop = threading.Event()

    def heartbeat():
        while not stop.is_set():
            request(f"/browser/session?client={client}", "POST")
            stop.wait(1.0)

    threading.Thread(target=heartbeat, daemon=True).start()
    time.sleep(1.5)
    for label, path in STEPS:
        ok = request(path, "PUT")
        mark(label if ok else f"{label} (실패)")
        print("inject", label, "ok" if ok else "FAIL")
        time.sleep(STEP_SEC)
    stop.set()


if __name__ == "__main__":
    main()
